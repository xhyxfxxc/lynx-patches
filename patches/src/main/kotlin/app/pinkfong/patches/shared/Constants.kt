/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.pinkfong.patches.shared

import app.morphe.patcher.patch.*

/**
 * Bebefinn Party, a Unity game by The Pinkfong Company (Smart Study). The package is named `app.pinkfong` on purpose:
 * the company's other games ship the same ad stack and may join this bundle later.
 */
object Constants {
    const val PACKAGE = "kr.co.smartstudy.bebefinnparty_android_googlemarket"

    /** Smali type of the runtime helper the patch calls into (extensions/extension, package app.pinkfong.extension). */
    const val NEXT_GEN_ADS = "Lapp/pinkfong/extension/NextGenAds;"

    /** The Google Play release is a split bundle (base APK plus an ABI split), so it is declared as XAPK. */
    val COMPATIBILITY_BEBEFINN_PARTY = Compatibility(
        name = "Bebefinn Party",
        packageName = PACKAGE,
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0xFA3E76,
        targets = listOf(
            AppTarget(version = "1.8"),
            AppTarget(version = null, isExperimental = true),
        ),
    )
}
