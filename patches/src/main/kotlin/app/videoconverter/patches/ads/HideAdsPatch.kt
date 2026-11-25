/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.videoconverter.patches.ads

import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.loki.patches.universal.stopAdSdks
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.videoconverter.patches.shared.Constants.COMPATIBILITY_VIDEOCONVERTER
import org.w3c.dom.Element

private val hideAdsFlag = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.hideads") }
    }
}

/**
 * Where the app keeps an ad slot in its layouts. A slot is empty when no ad loads, but for a free user the screens
 * make the slot's views visible before any ad arrives (the holder has a minimum height and a gray background, and
 * the "remove ad" button sits next to it), so an empty slot would stay as a strip. These layouts are collapsed
 * instead of relying on which code path runs.
 *
 * Checked against 20.11.0: no code refers to the roots below by id (the ids of the containers and of the include
 * tags have no literal in the app), the code only touches the views inside them (holder, remove-ad button, native
 * frame), and none of the `<include>` tags that bring the layouts in sets a visibility or an id the code uses.
 * A root that is gone hides everything inside it whatever the code does to the children.
 */
private val SLOT_ROOTS = listOf(
    // Banner holders, included at the top and bottom of every tool screen.
    "layout_banner_ad_holder_bottom",
    "layout_banner_ad_holder_top",
    // The native ad frame (min height 250 dp) of the merger's progress screen.
    "layout_native_ad_holder",
    // The holder of the older banner layout (not included anywhere in 20.11.0; collapsed so an update cannot revive it).
    "ad_holder_top_layout",
)

/** Layouts that carry their own ad container instead of including one: layout file to the id of the container. */
private val SLOT_CONTAINERS = listOf(
    // The progress screen and the progress dialog hold a native ad frame (min 250 dp) and the remove-ad button.
    "activity_progress" to "ad_container_bottom",
    "custom_dialog_layout" to "ad_container_bottom",
    // The batch screen's banner holder and remove-ad button.
    "activity_batch_process" to "ad_container_top",
)

/** Constraints that tie the width of a view to the container's edges. */
private val WIDTH_CONSTRAINTS = listOf(
    "app:layout_constraintStart_toStartOf",
    "app:layout_constraintEnd_toEndOf",
    "app:layout_constraintLeft_toLeftOf",
    "app:layout_constraintRight_toRightOf",
)

private fun Element.collapse() = setAttribute("android:visibility", "gone")

private val adSlotLayouts = resourcePatch {
    execute {
        val collapsed = mutableSetOf<String>()

        for (name in SLOT_ROOTS) {
            val document = try {
                document("res/layout/$name.xml")
            } catch (e: Exception) {
                continue // this layout is not in this version
            }
            document.use {
                it.documentElement.collapse()
                collapsed += name
            }
        }

        for ((name, id) in SLOT_CONTAINERS) {
            val document = try {
                document("res/layout/$name.xml")
            } catch (e: Exception) {
                continue
            }
            document.use { doc ->
                val all = doc.getElementsByTagName("*")
                val elements = (0 until all.length).map { all.item(it) as Element }
                for (element in elements) {
                    if (element.getAttribute("android:id") == "@id/$id") {
                        element.collapse()
                        collapsed += name
                    }
                }
                // A gone view in a ConstraintLayout shrinks to a point. The progress screen's message lines are as
                // wide as the container ("start and end of the container"), so they would shrink with it. The
                // container itself spans its parent, so the parent gives the same width.
                for (element in elements) {
                    for (attribute in WIDTH_CONSTRAINTS) {
                        if (element.getAttribute(attribute) == "@id/$id") element.setAttribute(attribute, "parent")
                    }
                }
            }
        }

        // The two banner holders are what every tool screen includes; without them the layouts have changed.
        if (!collapsed.containsAll(listOf("layout_banner_ad_holder_bottom", "layout_banner_ad_holder_top"))) {
            throw PatchException("Hide ads (Video Converter): the banner holder layouts were not found")
        }
    }
}

/** The one method a fingerprint must name; more than one match means the fingerprint is too loose. */
private fun BytecodePatchContext.single(fingerprint: Fingerprint, what: String, required: Boolean) =
    fingerprint.matchAllOrNull().orEmpty().let { matches ->
        when {
            matches.size == 1 -> matches[0].method
            matches.isEmpty() && !required -> null
            matches.isEmpty() -> throw PatchException("Hide ads (Video Converter): $what was not found")
            else -> throw PatchException("Hide ads (Video Converter): $what matched ${matches.size} methods")
        }
    }

/**
 * Video Converter shows ads from three places, all driven by InverseAI's own code on top of Google AdMob:
 *
 * 1. The app's ad controllers (banner, native, interstitial, app-open and rewarded views) read their on/off
 *    switches from Firebase Remote Config through one boolean getter per key. The ads-only switches are forced to
 *    false, which is the path the app itself takes when ads are switched off for a user, and every ad screen goes on
 *    without an ad (with them off the splash screen goes straight to Home, and the interstitial and app-open paths
 *    continue the user's action at once).
 * 2. InverseAI's cross-promotion house ads (banner, native, interstitial, rewarded). The interstitial is started by
 *    one launcher method that the app calls directly, so that method is made to answer "not shown".
 * 3. The Noise Reducer screens are Jetpack Compose with an ad module of their own, whose banner and
 *    interstitial/rewarded composables (and the containers that reserve room for them) draw nothing.
 *
 * The ad SDKs themselves are stopped as well (`stopAdSdks`): no request reaches Google AdMob. The layouts that hold
 * the ad slots are collapsed so that no empty strip, gray bar or lone "remove ad" button is left where an ad was.
 *
 * Nothing about premium, subscriptions, billing or the free-first-process window is touched.
 */
@Suppress("unused")
val videoConverterHideAdsPatch = bytecodePatch(
    name = "Hide ads (Video Converter)",
    description = "Removes Video Converter's ads: banners, native, full-screen and app-open ads from Google AdMob " +
        "and InverseAI's own cross-promotion ads, including the Noise Reducer screens. Billing and premium are " +
        "untouched.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_VIDEOCONVERTER)
    dependsOn(menuPatch, hideAdsFlag, adSlotLayouts)

    execute {
        stopAdSdks()

        configFlags.forEach { flag ->
            single(flag.fingerprint, "the switch ${flag.key}", flag.required)?.addInstructions(
                0,
                """
                    const/4 p1, 0x0
                    return p1
                """,
            )
        }

        single(crossInterstitialLauncherFingerprint, "the cross-promotion interstitial launcher", required = true)
            ?.addInstructions(
                0,
                """
                    const/4 p1, 0x0
                    return p1
                """,
            )

        composeAds.forEach { ad ->
            single(ad.fingerprint, "the Compose ad view ${ad.trace}", ad.required)?.addInstructions(0, "return-void")
        }
    }
}
