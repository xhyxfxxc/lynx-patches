/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21.
 */

package app.amazon.patches.disableautoplay

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

/**
 * MShopWebView.initialize(): called once per WebView instantiation (via <init>).
 * Unique by the mashService().setWebViewSettings(WebView) interface call sequence,
 * which only appears in this private method.
 * We prepend setMediaPlaybackRequiresUserGesture(true) so every WebView in the app
 * requires user interaction before any video autoplays.
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
