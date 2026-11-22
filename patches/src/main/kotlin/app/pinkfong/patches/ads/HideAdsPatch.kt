/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.pinkfong.patches.ads

import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.pinkfong.patches.shared.Constants.COMPATIBILITY_BEBEFINN_PARTY
import app.pinkfong.patches.shared.Constants.NEXT_GEN_ADS

private val hideAdsFlag = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.pinkfongads") }
    }
}

/**
 * Bebefinn Party gets all its ads from Google AdMob through the Google Mobile Ads Unity plugin's next-gen bridge
 * (`com.google.unity.ads.nextgen`). Unity Ads and Liftoff (Vungle) are only mediation adapters behind AdMob; the game
 * has no ad SDK of its own.
 *
 * Every load the bridge makes (app open, banner, interstitial, rewarded, rewarded interstitial) goes through one
 * method, `AdWrapper.load(AdRequest, AdLoadCallback)`. That method is replaced with a call into the extension that
 * answers the callback with a genuine "no fill" error, then returns. The game is told, the way a real ad network
 * would tell it, that no ad is coming, so it carries on instead of waiting for an answer that never arrives. The
 * bridge posts the failure to the game's thread itself, so the answer is as asynchronous as a real one. No request
 * ever reaches AdMob, so none of the mediated networks (Unity Ads, Liftoff) is asked for an ad either.
 *
 * The preloaders (background loading ahead of time) and native template ads are stopped as well in case the game
 * uses them. They are looked up first and skipped when missing.
 *
 * The shared `stopAdSdks()` is not used here: the game has no legacy AdMob API, and stubbing the start-up of Unity
 * Ads and Vungle would leave Google's adapter initialisation waiting for listeners that never answer, which can
 * delay the game's own "ads are ready" callback. Blocking at AdWrapper.load makes that unnecessary.
 *
 * Billing and the Pinkfong membership (a paid subscription) are not touched.
 */
@Suppress("unused")
val bebefinnHideAdsPatch = bytecodePatch(
    name = "Hide ads (Bebefinn Party)",
    description = "Removes Bebefinn Party's ads (Google AdMob with Unity Ads and Liftoff): banners, full-screen " +
        "ads and app-open ads never load.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_BEBEFINN_PARTY)
    dependsOn(menuPatch, hideAdsFlag)
    extendWith("extensions/extension.mpe")

    execute {
        // p2 is the AdLoadCallback; the extension answers it with "no fill". Only v0 is free, so nothing else is used.
        adWrapperLoadFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p2}, $NEXT_GEN_ADS->failLoad(Ljava/lang/Object;)V
                return-void
            """,
        )

        // start() returns whether preloading began: false means it did not.
        preloaderStartFingerprints.forEach { fingerprint ->
            fingerprint.matchOrNull()?.method?.addInstructions(
                0,
                """
                    const/4 v0, 0x0
                    return v0
                """,
            )
        }

        nativeTemplateLoadFingerprint.matchOrNull()?.method?.addInstructions(0, "return-void")
    }
}
