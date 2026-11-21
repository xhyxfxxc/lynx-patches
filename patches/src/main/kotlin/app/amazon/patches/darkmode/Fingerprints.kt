/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21.
 */

package app.amazon.patches.darkmode

import app.morphe.patcher.Fingerprint

// Unique by definingClass + name: no other getTabIcon in BaseTabController.
internal val BaseTabControllerGetTabIconFingerprint = Fingerprint(
    definingClass = "Lcom/amazon/mShop/chrome/bottomtabs/BaseTabController;",
    name = "getTabIcon",
    returnType = "Landroid/widget/ImageView;",
    parameters = emptyList(),
)

internal val MShopWebViewContainerOnPageFinishedFingerprint = Fingerprint(
    definingClass = "Lcom/amazon/mShop/web/MShopWebViewContainer;",
    name = "onPageFinished",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf("about:blank"),
)
