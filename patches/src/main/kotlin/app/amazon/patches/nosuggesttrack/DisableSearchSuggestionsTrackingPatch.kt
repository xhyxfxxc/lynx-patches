/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21.
 */

package app.amazon.patches.nosuggesttrack

import app.amazon.patches.shared.Constants.COMPATIBILITY_AMAZON
import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch

private val suggestionsFlag = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.amazonsuggest") }
    }
}

@Suppress("unused")
val amazonDisableSearchSuggestionsTrackingPatch = bytecodePatch(
    name = "Disable search suggestions tracking",
    description = "Prevents search keypress and focus events from being sent with suggestion requests.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AMAZON)
    dependsOn(menuPatch, suggestionsFlag)

    execute {
        // return p0 (builder) without setting event, .locals 0
        SearchSuggestionsSetEventFingerprint.method.addInstructions(
            0,
            "return-object p0",
        )
    }
}
