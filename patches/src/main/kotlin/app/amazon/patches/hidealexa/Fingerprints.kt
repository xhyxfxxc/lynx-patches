/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21.
 */

package app.amazon.patches.hidealexa

import app.morphe.patcher.Fingerprint

internal val SavXTabControllerIsEnabledFingerprint = Fingerprint(
    definingClass = "Lcom/amazon/mShop/chrome/bottomtabs/SavXTabController;",
    name = "isEnabled",
    returnType = "Z",
    parameters = emptyList(),
)
