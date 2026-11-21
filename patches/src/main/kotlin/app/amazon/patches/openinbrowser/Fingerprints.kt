/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21.
 */

package app.amazon.patches.openinbrowser

import app.morphe.patcher.Fingerprint

// shouldOverrideUrlLoading, unique by the "404.html" string.
internal val MShopWebViewClientShouldOverrideFingerprint = Fingerprint(
    definingClass = "Lcom/amazon/mShop/web/MShopWebViewClient;",
    name = "shouldOverrideUrlLoading",
    returnType = "Z",
    parameters = listOf("Landroid/webkit/WebView;", "Ljava/lang/String;"),
    strings = listOf("404.html"),
)
