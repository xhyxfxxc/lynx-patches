/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21. The idea comes from amznkiller by hxreborn.
 */

package app.amazon.patches.disableautoplay

import app.amazon.patches.shared.Constants.COMPATIBILITY_AMAZON
import app.amazon.patches.shared.Constants.HELPER
import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch

private val autoplayFlag = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.amazonautoplay") }
    }
}

/**
 * Ported from amznkiller v2.5.0 (feat: disable video autoplay in webviews), which hooks onPageFinished and injects
 * JavaScript. That is what this does too. An earlier version of this patch set
 * setMediaPlaybackRequiresUserGesture(true) when a WebView is created, which did nothing: Cordova's
 * SystemWebViewEngine.initWebViewSettings resets it to false afterwards, MASHWebView.enableMediaAutoplay sets it
 * again, and Chromium lets muted videos play on their own regardless.
 *
 * The script (AmazonHelper.injectNoAutoplay) removes autoplay attributes, pauses a video that starts without a tap
 * and watches for videos added later. It runs for normal page loads (onPageFinished) and, like Remove ads, when a
 * WebView is handed to a fragment, which also covers the pages that are loaded ahead of time.
 */
@Suppress("unused")
val amazonDisableVideoAutoplayPatch = bytecodePatch(
    name = "Disable video autoplay",
    description = "Stops videos in Amazon's in-app pages from starting on their own; a tap still plays them.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AMAZON)
    dependsOn(menuPatch, autoplayFlag)
    extendWith("extensions/extension.mpe")

    execute {
        // Normal page loads: p1=WebView
        MShopWebViewClientOnPageFinishedFingerprint.method.addInstructions(
            0,
            "invoke-static {p1}, $HELPER->injectNoAutoplay(Landroid/webkit/WebView;)V",
        )

        // All pages when the WebView is assigned to the fragment: p1=MShopWebView
        InteractionWebFragmentSetWebViewFingerprint.method.addInstructions(
            0,
            "invoke-static {p1}, $HELPER->injectNoAutoplay(Landroid/webkit/WebView;)V",
        )
    }
}
