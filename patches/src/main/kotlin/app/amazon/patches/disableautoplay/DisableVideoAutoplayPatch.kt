/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21. The idea comes from amznkiller by hxreborn.
 */

package app.amazon.patches.disableautoplay

import app.amazon.patches.shared.Constants.COMPATIBILITY_AMAZON
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
 * Ported from amznkiller v2.5.0 (feat: disable video autoplay in webviews).
 * The original uses Xposed to hook onPageFinished and inject JS; here a bytecode
 * patch on MShopWebView.initialize() calls setMediaPlaybackRequiresUserGesture(true)
 * at WebView creation time, which is cleaner and fires before any page loads.
 *
 * Registers in initialize(): .registers 3 gives p0=this, v0 and v1 available.
 * We call p0.getSettings() into v0, then v0.setMediaPlaybackRequiresUserGesture(true).
 */
@Suppress("unused")
val amazonDisableVideoAutoplayPatch = bytecodePatch(
    name = "Disable video autoplay",
    description = "Prevents videos from autoplaying in Amazon's in-app WebView pages.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AMAZON)
    dependsOn(menuPatch, autoplayFlag)

    execute {
        MShopWebViewInitializeFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, Landroid/webkit/WebView;->getSettings()Landroid/webkit/WebSettings;
                move-result-object v0
                const/4 v1, 0x1
                invoke-virtual {v0, v1}, Landroid/webkit/WebSettings;->setMediaPlaybackRequiresUserGesture(Z)V
            """.trimIndent(),
        )
    }
}
