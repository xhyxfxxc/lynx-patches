/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21. The ad selectors in the extension come from amznkiller by hxreborn.
 */

package app.amazon.patches.removeads

import app.amazon.patches.shared.Constants.COMPATIBILITY_AMAZON
import app.amazon.patches.shared.Constants.HELPER
import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch

private val removeAdsFlag = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.amazonadblock") }
    }
}

@Suppress("unused")
val amazonRemoveAdsPatch = bytecodePatch(
    name = "Remove ads",
    description = "Hides sponsored and ad content in Amazon Shopping via CSS injection.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AMAZON)
    dependsOn(menuPatch, removeAdsFlag)
    extendWith("extensions/extension.mpe")

    execute {
        // Normal page loads: p1=WebView
        MShopWebViewClientOnPageFinishedFingerprint.method.addInstructions(
            0,
            "invoke-static {p1}, $HELPER->injectAdBlock(Landroid/webkit/WebView;)V",
        )

        // All pages when the WebView is assigned to the fragment: p1=MShopWebView
        InteractionWebFragmentSetWebViewFingerprint.method.addInstructions(
            0,
            "invoke-static {p1}, $HELPER->injectAdBlock(Landroid/webkit/WebView;)V",
        )
    }
}
