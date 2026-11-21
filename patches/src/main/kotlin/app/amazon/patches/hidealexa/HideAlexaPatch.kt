/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21: renamed from "Hide Rufus tab" (Amazon renamed Rufus to Alexa for Shopping) and
 * switched off by default.
 */

package app.amazon.patches.hidealexa

import app.amazon.patches.shared.Constants.COMPATIBILITY_AMAZON
import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch

private val hideAlexaFlag = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.amazonalexa") }
    }
}

/**
 * Hides the Alexa for Shopping tab (formerly Rufus, the AI shopping assistant) from Amazon's bottom navigation bar.
 * SavXTabController.isEnabled() is replaced with a direct false return. Off by default: it is for people who do
 * not want the assistant, and the tab is useful to everyone else.
 */
@Suppress("unused")
val amazonHideAlexaPatch = bytecodePatch(
    name = "Hide Alexa for Shopping tab",
    description = "Removes the Alexa for Shopping (formerly Rufus) tab from the bottom navigation bar.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_AMAZON)
    dependsOn(menuPatch, hideAlexaFlag)

    execute {
        SavXTabControllerIsEnabledFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """.trimIndent(),
        )
    }
}
