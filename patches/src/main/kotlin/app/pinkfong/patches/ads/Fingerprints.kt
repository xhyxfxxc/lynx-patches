/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.pinkfong.patches.ads

import app.morphe.patcher.Fingerprint

/*
 * The Pinkfong games ship three ad stacks (see HideAdsPatch). Every target below is a public class or method of the
 * Google Mobile Ads Unity plugin (its bridge classes in com.google.unity.ads) or of the Google Mobile Ads SDK itself
 * (the "next-gen" com.google.android.libraries.ads.mobile.sdk, or the legacy com.google.android.gms.ads). They are
 * matched by defining class, name, return type and exact parameters, never by an obfuscated name, so they survive R8
 * and an SDK update. Group C (Cocos2d-x) has no such names and is matched by what its message handler does.
 *
 * Every fingerprint is looked up with matchOrNull: an app has some of these classes and not others, and a missing one
 * only means that stack is not there.
 */

// ---- Group A: Unity, Google Mobile Ads Next-Gen SDK ----------------------------------------------------------------

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
    preloaderStart("UnityRewardedInterstitialAdPreloader"),
)

/** Native template ads load through their own request type, not through AdWrapper. Optional, like the preloaders. */
internal val nativeTemplateLoadFingerprint = Fingerprint(
    definingClass = "$BRIDGE/UnityNativeTemplateAd;",
    name = "loadAd",
    returnType = "V",
    parameters = listOf("$SDK/nativead/NativeAdRequest;"),
)

// ---- Group B: Unity, legacy Google Mobile Ads SDK ------------------------------------------------------------------

private const val GMS = "Lcom/google/android/gms/ads"
private const val LEGACY_BRIDGE = "Lcom/google/unity/ads"

/** A static ad load of the legacy SDK, and the register that holds the load callback the game passed to it. */
internal class CallbackLoad(val fingerprint: Fingerprint, val callbackRegister: String)

private fun sdkLoad(owner: String, request: String, callback: String, vararg between: String) = CallbackLoad(
    Fingerprint(
        definingClass = "$GMS/$owner;",
        name = "load",
        returnType = "V",
        parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "$GMS/$request;") + between +
            "$GMS/$callback;",
    ),
    // Context, ad unit id and request are p0 to p2; an orientation int (app open) makes the callback p4.
    callbackRegister = if (between.isEmpty()) "p3" else "p4",
)

/**
 * Where the SDK itself loads a full-screen ad. The static methods are public API and keep their names; the game's own
 * calls (through the Unity bridge) all end here, so one answer per method covers every way the game asks for an ad.
 */
internal val legacySdkLoads = listOf(
    sdkLoad("interstitial/InterstitialAd", "AdRequest", "interstitial/InterstitialAdLoadCallback"),
    sdkLoad(
        "admanager/AdManagerInterstitialAd", "admanager/AdManagerAdRequest",
        "admanager/AdManagerInterstitialAdLoadCallback",
    ),
    sdkLoad("appopen/AppOpenAd", "AdRequest", "appopen/AppOpenAd\$AppOpenAdLoadCallback"),
    sdkLoad("appopen/AppOpenAd", "AdRequest", "appopen/AppOpenAd\$AppOpenAdLoadCallback", "I"),
    sdkLoad("appopen/AppOpenAd", "admanager/AdManagerAdRequest", "appopen/AppOpenAd\$AppOpenAdLoadCallback", "I"),
    sdkLoad("rewarded/RewardedAd", "AdRequest", "rewarded/RewardedAdLoadCallback"),
    sdkLoad("rewarded/RewardedAd", "admanager/AdManagerAdRequest", "rewarded/RewardedAdLoadCallback"),
    sdkLoad(
        "rewardedinterstitial/RewardedInterstitialAd", "AdRequest",
        "rewardedinterstitial/RewardedInterstitialAdLoadCallback",
    ),
    sdkLoad(
        "rewardedinterstitial/RewardedInterstitialAd", "admanager/AdManagerAdRequest",
        "rewardedinterstitial/RewardedInterstitialAdLoadCallback",
    ),
)

private fun bridgeLoad(cls: String, vararg params: String) = Fingerprint(
    definingClass = "$LEGACY_BRIDGE/$cls;",
    name = "loadAd",
    returnType = "V",
    parameters = params.toList(),
)

/**
 * Banner and native ads are not loaded by a static SDK method but by views and loaders the bridge builds, so they
 * are stopped where the bridge starts them. Nothing is loaded, and the game's banner view stays empty.
 */
internal val legacyBridgeLoads = listOf(
    bridgeLoad("Banner", "$GMS/AdRequest;"),
    bridgeLoad("admanager/UnityAdManagerBannerView", "$GMS/admanager/AdManagerAdRequest;"),
    bridgeLoad("UnityAdLoader", "$GMS/AdRequest;"),
    bridgeLoad(
        "nativead/UnityNativeTemplateAd", "Ljava/lang/String;", "$GMS/nativead/NativeAdOptions;", "$GMS/AdRequest;",
    ),
)

// ---- Group C: Cocos2d-x games, obfuscated Java side ----------------------------------------------------------------

/** The Cocos games' activities extend this class of Smart Study's own game library, which R8 does not rename. */
private const val COCOS_ACTIVITY = "Lkr/co/smartstudy/cocos2dx/common/SSGameLibActivity;"

/**
 * The Cocos client sends string commands to its activity, `GameActivity.onMessageFromClient(key, value)`. The
 * library's own handler (which this one calls first) knows only "check_permission", "movie_mode" and "toast"; the
 * game's own handler adds "preloadInterstitial", "showInterstitial", "showBanner" and "hideBanner". Found by those two
 * command names in a handler of a subclass of the library activity, because the class around it is only called
 * GameActivity and the ad helper it calls is renamed by R8 in every release.
 */
internal val cocosMessageHandlerFingerprint = Fingerprint(
    name = "onMessageFromClient",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    strings = listOf("preloadInterstitial", "showInterstitial"),
    custom = { _, classDef -> classDef.superclass == COCOS_ACTIVITY },
)
