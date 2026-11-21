/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21: Amazon India dropped, versions and file type updated.
 */

package app.amazon.patches.shared

import app.morphe.patcher.patch.*

/**
 * Amazon Shopping. The global app (com.amazon.mShop.android.shopping) is also the one Amazon Australia uses.
 * The APKMirror upload is a bundle (base + ABI + density + language splits), so it is declared as APKM. The hooks
 * name Amazon's own classes (they are not obfuscated), so another build is offered as experimental.
 */
object Constants {
    const val PACKAGE = "com.amazon.mShop.android.shopping"

    /** Smali type of the runtime helper the patches call into (extensions/extension, package app.amazon.extension). */
    const val HELPER = "Lapp/amazon/extension/AmazonHelper;"

    val COMPATIBILITY_AMAZON = Compatibility(
        name = "Amazon Shopping",
        packageName = PACKAGE,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0xFF9900,
        targets = listOf(
            AppTarget(version = "32.19.0.100"),
            AppTarget(version = "32.20.2.100"),
            AppTarget(version = null, isExperimental = true),
        ),
    )
}
