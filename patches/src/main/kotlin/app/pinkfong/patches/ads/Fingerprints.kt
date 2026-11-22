/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.pinkfong.patches.ads

import app.morphe.patcher.Fingerprint

/*
 * Every target below is a public class or method of the Google Mobile Ads Unity plugin (the "next-gen" bridge in
 * com.google.unity.ads.nextgen) or of the Google Mobile Ads Next-Gen SDK it wraps. They are matched by defining class,
 * name, return type and exact parameters, never by an obfuscated name, so they survive R8 and an SDK update.
 */

private const val SDK = "Lcom/google/android/libraries/ads/mobile/sdk"
private const val BRIDGE = "Lcom/google/unity/ads/nextgen"

/**
 * The single choke point: the bridge's AdWrapper sends every ad load (app open, banner, interstitial, rewarded and
 * rewarded interstitial) through this method, together with the callback that tells the game how it went.
 */
internal val adWrapperLoadFingerprint = Fingerprint(
    definingClass = "$BRIDGE/AdWrapper;",
    name = "load",
    returnType = "V",
    parameters = listOf("$SDK/common/AdRequest;", "$SDK/common/AdLoadCallback;"),
)

/**
 * The preloaders start background ad loading ahead of time, with a preload configuration instead of a request. The
 * game may not use them, so they are patched defensively and skipped when absent.
 */
private fun preloaderStart(name: String) = Fingerprint(
    definingClass = "$BRIDGE/$name;",
    name = "start",
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;", "$SDK/common/PreloadConfiguration;"),
)

internal val preloaderStartFingerprints = listOf(
    preloaderStart("UnityAppOpenAdPreloader"),
    preloaderStart("UnityInterstitialAdPreloader"),
    preloaderStart("UnityRewardedAdPreloader"),
    preloaderStart("UnityBannerAdPreloader"),
)

/** Native template ads load through their own request type, not through AdWrapper. Optional, like the preloaders. */
internal val nativeTemplateLoadFingerprint = Fingerprint(
    definingClass = "$BRIDGE/UnityNativeTemplateAd;",
    name = "loadAd",
    returnType = "V",
    parameters = listOf("$SDK/nativead/NativeAdRequest;"),
)
