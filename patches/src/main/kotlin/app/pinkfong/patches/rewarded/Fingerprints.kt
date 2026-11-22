/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.pinkfong.patches.rewarded

import app.morphe.patcher.Fingerprint

/*
 * The rewarded classes of the two Google Mobile Ads Unity bridges, matched by class, name, return type and exact
 * parameters (public API of the plugin, kept by R8). A game uses one bridge; the other is dead code in some of them,
 * so every class is optional and the patch needs only one to be found.
 */

private const val NEXT_GEN = "Lcom/google/unity/ads/nextgen"
private const val NEXT_GEN_SDK = "Lcom/google/android/libraries/ads/mobile/sdk"
private const val LEGACY = "Lcom/google/unity/ads"

/**
 * One rewarded class of a bridge: its load and show methods, and the fields the replacement reads. The next-gen bridge
 * keeps the callback as an Object and has an executor that runs the callbacks; the legacy bridge has a typed callback
 * and starts a thread for each.
 */
internal class RewardedBridge(
    val label: String,
    cls: String,
    val loadName: String,
    loadParameters: List<String>,
    /** The callback field as the bridge's own code reads it: owner, name and type. */
    val callbackField: String,
    /** The executor field, or null for the legacy bridge. */
    val executorField: String?,
) {
    val load = Fingerprint(definingClass = cls, name = loadName, returnType = "V", parameters = loadParameters)
    val show = Fingerprint(definingClass = cls, name = "show", returnType = "V", parameters = listOf())
}

private fun nextGen(format: String) = "$NEXT_GEN/Unity$format".let { cls ->
    RewardedBridge(
        label = "next-gen $format",
        cls = "$cls;",
        loadName = "load",
        loadParameters = listOf("$NEXT_GEN_SDK/common/AdRequest;"),
        callbackField = "$cls;->callback:Ljava/lang/Object;",
        executorField = "$cls;->executor:Ljava/util/concurrent/Executor;",
    )
}

private fun legacy(format: String) = "$LEGACY/Unity$format".let { cls ->
    RewardedBridge(
        label = "legacy $format",
        cls = "$cls;",
        loadName = "loadAd",
        loadParameters = listOf("Ljava/lang/String;", "Lcom/google/android/gms/ads/AdRequest;"),
        callbackField = "$cls;->callback:${cls}Callback;",
        executorField = null,
    )
}

internal val rewardedBridges = listOf(
    nextGen("RewardedAd"),
    nextGen("RewardedInterstitialAd"),
    legacy("RewardedAd"),
    legacy("RewardedInterstitialAd"),
)
