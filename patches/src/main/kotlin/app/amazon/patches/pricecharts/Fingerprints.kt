/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21.
 */

package app.amazon.patches.pricecharts

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

// onPageFinished for non-jumpstarted pages
internal val MShopWebViewClientOnPageFinishedFingerprint = Fingerprint(
    definingClass = "Lcom/amazon/mShop/web/MShopWebViewClient;",
    name = "onPageFinished",
    returnType = "V",
    parameters = listOf("Landroid/webkit/WebView;", "Ljava/lang/String;"),
    strings = listOf("onPageFinished_withDuration"),
)

// Fires when any page (including jumpstarted ones) becomes visible to the user.
internal val InteractionWebFragmentPostShownFingerprint = Fingerprint(
    definingClass = "Lcom/amazon/mShop/web/InteractionWebFragment;",
    name = "onFragmentPostShown",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("No animation associated with "),
)

/**
 * MShopWebView.initialize(): called once per WebView instantiation (via <init>).
 * Unique by the mashService().setWebViewSettings(WebView) interface call sequence,
 * which only appears in this private method. The price history card's bridge object is added here, so every
 * WebView has it before it loads anything.
 */
internal val MShopWebViewInitializeFingerprint = Fingerprint(
    definingClass = "Lcom/amazon/mShop/web/MShopWebView;",
    name = "initialize",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/amazon/mShop/mash/api/MShopMASHService;",
            name = "setWebViewSettings",
        ),
    ),
)
