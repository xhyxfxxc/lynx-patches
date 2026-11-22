/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.pinkfong.patches.ads

import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.freeFrom
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.pinkfong.patches.shared.Constants.ALL_GAMES
import app.pinkfong.patches.shared.Constants.LEGACY_ADS
import app.pinkfong.patches.shared.Constants.NEXT_GEN_ADS
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val hideAdsFlag = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.pinkfongads") }
    }
}

/**
 * The Pinkfong and Baby Shark kids games (Smart Study) get their ads from Google AdMob, with Unity Ads and Liftoff
 * (Vungle) only as mediation adapters behind it; none has an ad SDK of its own. The games come in three flavours,
 * and each is stopped where it is easiest to answer with a genuine "no ad".
 *
 * Group A, Unity with the Google Mobile Ads Next-Gen SDK (`com.google.unity.ads.nextgen`). Every load the bridge
 * makes (app open, banner, interstitial, rewarded, rewarded interstitial) goes through one method,
 * `AdWrapper.load(AdRequest, AdLoadCallback)`. It is replaced with a call into the extension that answers the
 * callback with a genuine "no fill" error, then returns. The game is told, the way a real ad network would tell it,
 * that no ad is coming, so it carries on instead of waiting for an answer that never arrives. The first blocked
 * load of each ad format is answered at once (so a game waiting for a start-up ad continues), and every later one
 * only after 60 seconds: several games (Smart Study's "SSAdMob") load again from their failure callback, and an
 * instant answer made that loop run hundreds of times a second until the JNI global reference table overflowed and
 * the app crashed. The bridge posts the failure to the game's thread itself, so the answer is as asynchronous as a
 * real one. No request ever reaches AdMob, so none of the mediated networks is asked for an ad either. The
 * preloaders (background loading ahead of time) and native template ads are stopped as well. Only p2 (the callback)
 * is used, because one of the games has no free register in that method.
 *
 * Group B, Unity with the legacy Google Mobile Ads SDK (`com.google.android.gms.ads`, public API kept) and the
 * legacy bridge (`com.google.unity.ads`). Every full-screen format is loaded by a static SDK method
 * (`InterstitialAd.load`, `AppOpenAd.load`, `RewardedAd.load`, `RewardedInterstitialAd.load` and the Ad Manager
 * variants). Each is replaced with a call into the extension that answers the load callback with a "no fill"
 * LoadAdError on the main thread, as the SDK does, then returns. The same throttle applies: the first blocked load
 * of each callback class is answered at once, the rest after 60 seconds. Banner and native ads are loaded by views
 * and loaders the bridge builds, so the bridge's `loadAd` methods are stopped with a plain return.
 *
 * Group C, Cocos2d-x games whose Java side R8 has renamed completely, so no SDK or helper name can be matched. The
 * Cocos client sends string commands to `GameActivity.onMessageFromClient(key, value)`. "preloadInterstitial" calls
 * a no-argument method of an obfuscated ad helper that starts the interstitial load; it is found as the first such
 * call after that command's name and replaced with a return, so no interstitial is ever loaded. "showInterstitial"
 * then finds nothing loaded, asks the (now empty) preload again and tells the game "not shown", so the game
 * continues; that is the game's own path for a failed ad. "showBanner" builds and loads a banner, so the handler
 * returns early for that key (the base class does not handle it, so skipping its call is safe).
 *
 * All three are optional per app; the patch fails only when none matched, so a game update that moves every target
 * is reported instead of patching nothing. Group A's apps also carry the legacy bridge classes as dead code, so the
 * group B stops apply to them harmlessly.
 *
 * The shared `stopAdSdks()` is not used: it stubs the start-up of AdMob, Unity Ads and Vungle, which would leave
 * Google's adapter initialisation waiting for listeners that never answer and can delay the game's own "ads are
 * ready" callback. Blocking the loads makes that unnecessary, and group C has no SDK names to stub anyway.
 *
 * Billing and the Pinkfong membership (a paid subscription) are not touched, nor is the "More apps" button.
 */
@Suppress("unused")
val pinkfongHideAdsPatch = bytecodePatch(
    name = "Hide ads (Pinkfong)",
    description = "Removes the ads of Pinkfong and Baby Shark kids games (Google AdMob with Unity Ads and Liftoff): " +
        "banners, full-screen ads and app-open ads never load.",
    default = true,
) {
    compatibleWith(*ALL_GAMES.toTypedArray())
    dependsOn(menuPatch, hideAdsFlag)
    extendWith("extensions/extension.mpe")

    execute {
        var stopped = 0

        // Group A. p2 is the AdLoadCallback; the extension answers it with "no fill". Only v0 may be free, so nothing
        // else is used.
        adWrapperLoadFingerprint.matchOrNull()?.method?.let {
            it.addInstructions(
                0,
                """
                    invoke-static {p2}, $NEXT_GEN_ADS->failLoad(Ljava/lang/Object;)V
                    return-void
                """,
            )
            stopped++
        }

        // start() returns whether preloading began: false means it did not.
        preloaderStartFingerprints.forEach { fingerprint ->
            fingerprint.matchOrNull()?.method?.let {
                it.addInstructions(
                    0,
                    """
                        const/4 v0, 0x0
                        return v0
                    """,
                )
                stopped++
            }
        }

        nativeTemplateLoadFingerprint.matchOrNull()?.method?.let {
            it.addInstructions(0, "return-void")
            stopped++
        }

        // Group B. The loads are static, so the callback register is a parameter: p3, or p4 after an int.
        legacySdkLoads.forEach { load ->
            load.fingerprint.matchOrNull()?.method?.let {
                it.addInstructions(
                    0,
                    """
                        invoke-static {${load.callbackRegister}}, $LEGACY_ADS->failLoad(Ljava/lang/Object;)V
                        return-void
                    """,
                )
                stopped++
            }
        }

        legacyBridgeLoads.forEach { fingerprint ->
            fingerprint.matchOrNull()?.method?.let {
                it.addInstructions(0, "return-void")
                stopped++
            }
        }

        // Group C.
        cocosMessageHandlerFingerprint.matchOrNull()?.method?.let {
            stopCocosAds(it)
            stopped++
        }

        if (stopped == 0) {
            throw PatchException(
                "Hide ads (Pinkfong): none of the three ad stacks was found (next-gen bridge, legacy SDK, Cocos handler)",
            )
        }
    }
}

/**
 * Group C: stubs the interstitial preload the handler calls and makes the handler ignore "showBanner".
 */
private fun BytecodePatchContext.stopCocosAds(handler: MutableMethod) {
    val code = handler.implementation?.instructions
        ?: throw PatchException("Cocos message handler has no code")

    // The first call after the "preloadInterstitial" command that takes nothing and returns nothing is the helper's
    // preload: the command's other calls (String.equals) take an argument.
    val commandAt = code.indexOfFirst { instruction ->
        (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
            ((instruction as ReferenceInstruction).reference as? StringReference)?.string == "preloadInterstitial"
    }
    if (commandAt < 0) throw PatchException("Cocos message handler: \"preloadInterstitial\" not found")
    val call = code.drop(commandAt + 1).firstOrNull { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        instruction.opcode == Opcode.INVOKE_VIRTUAL && reference != null &&
            reference.parameterTypes.isEmpty() && reference.returnType == "V"
    } ?: throw PatchException("Cocos message handler: the interstitial preload call was not found")
    val target = (call as ReferenceInstruction).reference as MethodReference

    val preload = mutableClassDefBy(target.definingClass).methods.firstOrNull {
        it.name == target.name && it.parameterTypes.isEmpty() && it.returnType == "V" && it.implementation != null
    } ?: throw PatchException("Cocos ad helper: ${target.definingClass}->${target.name}() has no code")
    preload.addInstructions(0, "return-void")

    // v0 has to be a free local at the start of the handler: this, key and value are the three parameter registers,
    // and the handler's own first instruction overwrites v0 before it reads it.
    val parameterRegisters = handler.parameterTypes.size + 1
    if ((handler.implementation?.registerCount ?: 0) <= parameterRegisters || !freeFrom(code, listOf(0))) {
        throw PatchException("Cocos message handler: v0 is not free at the start")
    }
    handler.addInstructionsWithLabels(
        0,
        """
            const-string v0, "showBanner"
            invoke-virtual {v0, p1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :keep
            return-void
        """,
        ExternalLabel("keep", handler.getInstruction(0)),
    )
}
