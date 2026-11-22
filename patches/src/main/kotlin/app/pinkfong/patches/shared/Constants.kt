/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.pinkfong.patches.shared

import app.morphe.patcher.patch.*

/**
 * The kids games of The Pinkfong Company (Smart Study) that are on Google Play as
 * `kr.co.smartstudy.<id>_android_googlemarket`. They share their ad stack in three flavours (see HideAdsPatch), so one
 * set of patches serves all of them. The package is named `app.pinkfong` for that reason.
 *
 * Every app lists the one version the patch was written and checked against, plus "any other version" as experimental;
 * a new release of a game replaces its version (the audit allows at most the newest two). Each Compatibility is spelled
 * out with its package name in full so tools/audit.py can read it.
 */
object Constants {
    /** Smali type of the runtime helper for the next-gen Google Mobile Ads Unity bridge (extensions/extension). */
    const val NEXT_GEN_ADS = "Lapp/pinkfong/extension/NextGenAds;"

    /** Smali type of the runtime helper for the legacy Google Mobile Ads SDK and its Unity bridge. */
    const val LEGACY_ADS = "Lapp/pinkfong/extension/LegacyAds;"

    /** Smali type of the runtime helper that answers rewarded ads without playing them. */
    const val REWARDS = "Lapp/pinkfong/extension/PinkfongRewards;"

    /*
     * The Google Play releases are split bundles (base APK plus an ABI split, sometimes asset packs), so every app is
     * declared as XAPK.
     */

    val COMPATIBILITY_BEBEFINN_PARTY = Compatibility(
        name = "Bebefinn Party",
        packageName = "kr.co.smartstudy.bebefinnparty_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0xFA3E76,
        targets = listOf(
            AppTarget(version = "1.8"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_BBS_CHEF_GAME = Compatibility(
        name = "Baby Shark Chef Cooking Game",
        packageName = "kr.co.smartstudy.bbschefgame_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x70DCF9,
        targets = listOf(
            AppTarget(version = "4.04"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_BBS_DESSERT = Compatibility(
        name = "Baby Shark's Dessert",
        packageName = "kr.co.smartstudy.bbsdessert_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x4CD5F8,
        targets = listOf(
            AppTarget(version = "2.23"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_NINIMO_MART = Compatibility(
        name = "Ninimo Cat Supermarket",
        packageName = "kr.co.smartstudy.bbsninimomartgame_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0xFBC360,
        targets = listOf(
            AppTarget(version = "3.0"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_CAR_TOWN = Compatibility(
        name = "Car Town",
        packageName = "kr.co.smartstudy.cartown_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x1B59C3,
        targets = listOf(
            AppTarget(version = "32.53"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_HOGI_STAR = Compatibility(
        name = "Hogi Star Adventure",
        packageName = "kr.co.smartstudy.hogistaradventure_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x1F3577,
        targets = listOf(
            AppTarget(version = "3.2"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_MY_BODY = Compatibility(
        name = "My Body",
        packageName = "kr.co.smartstudy.mybody_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x0E4595,
        targets = listOf(
            AppTarget(version = "21.11"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_SHAPES_COLORS = Compatibility(
        name = "Pinkfong Shapes & Colors",
        packageName = "kr.co.smartstudy.shapescolors_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x41DFFB,
        targets = listOf(
            AppTarget(version = "18.30"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_POLICE_HEROES = Compatibility(
        name = "Police Heroes Game",
        packageName = "kr.co.smartstudy.thepolice_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x1D95D3,
        targets = listOf(
            AppTarget(version = "1.5"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_BBS_COLORING = Compatibility(
        name = "Baby Shark Coloring Book",
        packageName = "kr.co.smartstudy.babysharkcoloring_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x10C8FB,
        targets = listOf(
            AppTarget(version = "36.20"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_BBS_ENGLISH = Compatibility(
        name = "Baby Shark English",
        packageName = "kr.co.smartstudy.bbsenglish_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0xDEAD27,
        targets = listOf(
            AppTarget(version = "2.48"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_BBS_HOSPITAL = Compatibility(
        name = "Baby Shark Hospital Play",
        packageName = "kr.co.smartstudy.bbshospitalgame_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x27D5F7,
        targets = listOf(
            AppTarget(version = "2.1"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_DINO_WORLD = Compatibility(
        name = "Dino World",
        packageName = "kr.co.smartstudy.dinoworld_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0xE32F14,
        targets = listOf(
            AppTarget(version = "36.05"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_BBS_PHONE = Compatibility(
        name = "Baby Shark Phone",
        packageName = "kr.co.smartstudy.singingphone_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0xFA64A6,
        targets = listOf(
            AppTarget(version = "30.28"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_COLORING_FUN = Compatibility(
        name = "Pinkfong Coloring Fun",
        packageName = "kr.co.smartstudy.kidscoloringfun_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0xF7279B,
        targets = listOf(
            AppTarget(version = "38.07"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_TRACING_WORLD = Compatibility(
        name = "Tracing World",
        packageName = "kr.co.smartstudy.tracingworld_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0xF15C8C,
        targets = listOf(
            AppTarget(version = "33.21"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    val COMPATIBILITY_BIRTHDAY_PARTY = Compatibility(
        name = "Birthday Party",
        packageName = "kr.co.smartstudy.birthdayparty_android_googlemarket",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0xF12F98,
        targets = listOf(
            AppTarget(version = "24.23"),
            AppTarget(version = null, isExperimental = true),
        ),
    )

    /** Every game the Hide ads patch supports. */
    val ALL_GAMES = listOf(
        COMPATIBILITY_BEBEFINN_PARTY, COMPATIBILITY_BBS_CHEF_GAME, COMPATIBILITY_BBS_DESSERT,
        COMPATIBILITY_NINIMO_MART, COMPATIBILITY_CAR_TOWN, COMPATIBILITY_HOGI_STAR, COMPATIBILITY_MY_BODY,
        COMPATIBILITY_SHAPES_COLORS, COMPATIBILITY_POLICE_HEROES, COMPATIBILITY_BBS_COLORING,
        COMPATIBILITY_BBS_ENGLISH, COMPATIBILITY_BBS_HOSPITAL, COMPATIBILITY_DINO_WORLD, COMPATIBILITY_BBS_PHONE,
        COMPATIBILITY_COLORING_FUN, COMPATIBILITY_TRACING_WORLD, COMPATIBILITY_BIRTHDAY_PARTY,
    )

    /** The games that offer "watch a video for a reward": the Skip rewarded ads patch supports these. */
    val REWARDED_GAMES = listOf(
        COMPATIBILITY_BBS_CHEF_GAME, COMPATIBILITY_BBS_DESSERT, COMPATIBILITY_NINIMO_MART,
        COMPATIBILITY_POLICE_HEROES, COMPATIBILITY_BBS_COLORING, COMPATIBILITY_BBS_ENGLISH,
        COMPATIBILITY_BBS_HOSPITAL, COMPATIBILITY_BBS_PHONE,
    )
}
