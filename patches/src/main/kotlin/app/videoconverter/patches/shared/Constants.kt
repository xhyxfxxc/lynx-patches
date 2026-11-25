/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.videoconverter.patches.shared

import app.morphe.patcher.patch.*

/**
 * Video Converter by InverseAI (package com.inverseai.video_converter). It is also the host of the Noise Reducer,
 * Video Merger and AI assistant screens, so one app carries three ad stacks (see the Hide ads patch).
 */
object Constants {
    const val PACKAGE = "com.inverseai.video_converter"

    /** The Google Play release is a split bundle (base APK plus ABI and density splits), so it is declared as XAPK. */
    val COMPATIBILITY_VIDEOCONVERTER = Compatibility(
        name = "Video Converter",
        packageName = PACKAGE,
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x895F7B,
        targets = listOf(
            AppTarget(version = "20.11.0"),
            AppTarget(version = null, isExperimental = true),
        ),
    )
}
