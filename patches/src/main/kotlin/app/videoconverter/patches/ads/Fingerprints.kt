/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.videoconverter.patches.ads

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/*
 * Video Converter is obfuscated (R8 renames every class and method of the app and of InverseAI's own libraries), so
 * nothing below names an app class. Every target is found by what it does and carries: a Remote Config key string, a
 * request-code literal together with the class a method starts, or the trace string the Compose compiler leaves in
 * every composable function (they survive R8 because they are plain string constants).
 */

private const val CONTEXT = "Landroid/content/Context;"

private val Method.code: List<Instruction>
    get() = implementation?.instructions?.toList() ?: emptyList()

private val Method.strings: List<String>
    get() = code.mapNotNull { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? StringReference
        if (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) {
            reference?.string
        } else {
            null
        }
    }

private fun Method.startsClass(type: String) = code.any { instruction ->
    instruction.opcode == Opcode.CONST_CLASS &&
        ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type == type
}

private fun Method.hasLiteral(value: Int) = code.any { instruction ->
    (instruction as? NarrowLiteralInstruction)?.narrowLiteral == value
}

// ---- Remote Config switches --------------------------------------------------------------------------------------

/**
 * The app keeps its ad switches in Firebase Remote Config and reads each through one boolean getter: public, takes a
 * Context, returns Z, and holds the key as a string constant (default, then a call to the preferences reader). The
 * setter of the same key holds the same string but returns V, which is why the return type matters. The key is
 * compared whole, so "vcon_can_show_ads" never matches a longer key.
 */
internal class ConfigFlag(val key: String, val required: Boolean) {
    val fingerprint = Fingerprint(
        returnType = "Z",
        parameters = listOf(CONTEXT),
        strings = listOf(key),
        custom = { method, _ -> key in method.strings },
    )
}

/**
 * Switches that only decide whether an ad is requested or shown. Forcing them to false is what the app does itself
 * for a user whose ads are switched off remotely, so each screen takes its ad-free path.
 *
 * Not in this list on purpose: can_rewarded_feature, can_reward_batch_process and can_reward_on_app_share decide
 * whether the "Unlock free / Watch ad" choice is offered at all (the rewarded patch turns it into an instant unlock),
 * and can_show_ad_for_media_api only chooses between an ad and the free path for one dialog.
 */
internal val configFlags = listOf(
    // The master switch: banner and native slots, interstitial load and show, rewarded load.
    ConfigFlag("vcon_can_show_ads", required = true),
    // The app-open ad at start and when coming back from the background; with it off the splash goes to Home.
    ConfigFlag("can_show_app_open_ad", required = true),
    ConfigFlag("can_show_ad_app_open_from_background", required = true),
    // InverseAI's own cross-promotion ads (house ads for its other apps), used on their own and as AdMob's fallback.
    ConfigFlag("vcon_can_show_cross_interstitial_ad", required = true),
    ConfigFlag("vcon_can_show_cross_banner_ad", required = true),
    ConfigFlag("vcon_can_show_cross_native_ad", required = true),
    ConfigFlag("vcon_can_show_cross_rewarded_ad_v2", required = true),
    ConfigFlag("vcon_can_always_show_cross_rewarded_ad_v2", required = true),
    ConfigFlag("vcon_can_show_cross_promo_only_for_long_process", required = true),
    // Belt and braces: the master switch gates all of these already, so a release that drops one still patches.
    ConfigFlag("vcon_can_show_banner_ad", required = false),
    ConfigFlag("vcon_can_show_native_ad", required = false),
    ConfigFlag("vcon_can_load_native_ad", required = false),
    ConfigFlag("vcon_can_show_interstitial_ad", required = false),
    ConfigFlag("vcon_can_load_interstitial_ad", required = false),
    ConfigFlag("vcon_can_show_rewarded_ad", required = false),
    ConfigFlag("vcon_can_load_rewarded_ad", required = false),
)

// ---- Cross-promotion interstitial ----------------------------------------------------------------------------------

private const val CROSS_INTERSTITIAL_ACTIVITY = "Lorg/inverseai/cross_promo/activity/InterstitialAdActivity;"

/**
 * The cross-promotion library's interstitial launcher: it starts InterstitialAdActivity for a result (request code
 * 911) and answers whether it did. The app calls it directly at the end of single and batch processing and after long
 * processes, without looking at the switches above, and continues the user's action through its own listener when the
 * answer is false.
 */
internal val crossInterstitialLauncherFingerprint = Fingerprint(
    returnType = "Z",
    parameters = listOf("Landroid/app/Activity;"),
    custom = { method, _ -> method.startsClass(CROSS_INTERSTITIAL_ACTIVITY) && method.hasLiteral(911) },
)

// ---- Compose ad screens (Noise Reducer, AI assistant) --------------------------------------------------------------

/**
 * The Noise Reducer screens are Jetpack Compose and have their own ad module (banner, interstitial and rewarded
 * views). Every composable function carries a trace string "<package>.<Class>.<function> (<File>.kt:<line>)"; the
 * file and line part changes with the source, so only the class and function part is compared.
 *
 * A composable that draws nothing may simply return before it starts its group, so each of these is stopped with a
 * return at the top.
 */
internal class ComposeAd(val trace: String, val required: Boolean) {
    val fingerprint = Fingerprint(
        returnType = "V",
        // The Composer (renamed by R8) and the changed-flags int come last.
        custom = { method, _ ->
            method.parameterTypes.size >= 2 && method.parameterTypes.last().toString() == "I" &&
                method.strings.any { it.contains(trace) }
        },
    )
}

internal val composeAds = listOf(
    // The ad module's own entry points: the banner view and the host of the interstitial and rewarded views.
    ComposeAd(".adModuleController.AdController.ShowBannerAdView (", required = true),
    ComposeAd(".adModuleController.AdController.InterstitialAndRewardedAdView (", required = true),
    // The two Noise Reducer builds wrap them in an AdLoader.
    ComposeAd(".adUseCase.AdLoader.ShowBannerAdView (", required = false),
    ComposeAd(".adUseCase.AdLoader.InterstitialAndRewardedAdView (", required = false),
    // The container the Noise Reducer home list reserves for the banner. Its height is fixed, so an empty one would
    // leave a blank block where the ad was.
    ComposeAd(".HomeScreenComponents.AppPromoAdContainer (", required = false),
    ComposeAd(".DemoMediaScreenView.BannerAdSection (", required = false),
)
