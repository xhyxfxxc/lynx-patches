/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21.
 */

package app.amazon.patches.removeads

import app.morphe.patcher.Fingerprint

// Normal (non-jumpstarted) page loads
internal val MShopWebViewClientOnPageFinishedFingerprint = Fingerprint(
    definingClass = "Lcom/amazon/mShop/web/MShopWebViewClient;",
    name = "onPageFinished",
    returnType = "V",
    parameters = listOf("Landroid/webkit/WebView;", "Ljava/lang/String;"),
    strings = listOf("onPageFinished_withDuration"),
)

// All pages (normal and jumpstarted): fires when the WebView is assigned to the fragment
internal val InteractionWebFragmentSetWebViewFingerprint = Fingerprint(
    definingClass = "Lcom/amazon/mShop/web/InteractionWebFragment;",
    name = "setWebView",
    returnType = "V",
    parameters = listOf("Lcom/amazon/mShop/web/MShopWebView;"),
)
