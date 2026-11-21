/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21.
 */

package app.amazon.patches.openinbrowser

import app.amazon.patches.shared.Constants.COMPATIBILITY_AMAZON
import app.amazon.patches.shared.Constants.HELPER
import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch

private val openLinksFlag = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.amazonlinks") }
    }
}

@Suppress("unused")
val amazonOpenLinksInBrowserPatch = bytecodePatch(
    name = "Open links in browser",
    description = "Opens non-Amazon URLs in the default browser instead of the in-app WebView.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AMAZON)
    dependsOn(menuPatch, openLinksFlag)
    extendWith("extensions/extension.mpe")

    execute {
        // p1=WebView, p2=url (.locals 3)
        // The helper decides using the parsed hostname, not a substring match, so
        // hosts that merely contain "amazon." (evilamazon.com,
        // amazon.com.evil.example, amazon.com@evil.example) leave the WebView.
        // It returns true once the destination has been handed to the browser.
        MShopWebViewClientShouldOverrideFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $HELPER->openExternally(Landroid/webkit/WebView;Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :skip
                const/4 v0, 0x1
                return v0
                :skip
                nop
            """.trimIndent(),
        )
    }
}
