/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.videoconverter.patches.rewarded

import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.videoconverter.patches.ads.ConfigFlag
import app.videoconverter.patches.shared.Constants.COMPATIBILITY_VIDEOCONVERTER

private val rewardsFlag = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.videoconverterrewards") }
    }
}

/**
 * Whether the Noise Reducer's balance dialog offers "Watch ad" (a button that earns credits from InverseAI's server).
 * The switch is read by that one button only, so turning it off hides the button and nothing else.
 */
private val noiseReducerWatchAd = ConfigFlag("vcon_is_rewarded_ad_enabled_for_nr_vms", required = true)

/**
 * Every "watch an ad to unlock this" button of Video Converter (the free-or-buy dialogs of the converter, cutter,
 * merger, audio tools and the media picker) stores the gated action in one controller and asks it to play a rewarded
 * ad. The controller already has a shortcut of its own for a user whose first process is free: it runs the stored
 * action straight away without an ad. The patch makes every such request take that same step, only without using up
 * the free-process counter, so the unlock is given at once and no ad is requested, shown or waited for. These are
 * the app's own one-shot local unlocks (one more file, a batch, a merge, a format).
 *
 * Two kinds of "watch an ad" are not unlocks of that kind, and neither is given for free:
 *
 * - The AI assistant's "watch an ad for credits". The credits are granted by InverseAI's server after it has verified
 *   the ad, and the app only reads the new balance back, so a reward without an ad would earn nothing and would make
 *   the chat claim it had. The hook that asks for that ad answers "not earned" at once instead: no ad, no dialog,
 *   no credits and no waiting, which is also what the AI assistant sees when no ad is available.
 * - The Noise Reducer's "Watch ad" button in its balance dialog (credits from the same server). The button is
 *   hidden through the app's own remote switch for it.
 *
 * Paid features, billing and the free-first-process counter are not touched. This patch does not depend on Hide ads
 * (Video Converter): the unlock is given before any ad is requested either way.
 */
@Suppress("unused")
val videoConverterRewardedAdsPatch = bytecodePatch(
    name = "Skip rewarded ads (Video Converter)",
    description = "Gives the unlock of a \"watch an ad\" button straight away, without playing an ad. The AI " +
        "assistant's credits (earned on InverseAI's server) and the Noise Reducer's \"Watch ad\" button are not " +
        "unlocked this way.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_VIDEOCONVERTER)
    dependsOn(menuPatch, rewardsFlag)

    execute {
        // The AI assistant first, so that a missing target stops the patch before anything is given away for free.
        val ai = aiCreditsAdFingerprint.matchAllOrNull().orEmpty()
        if (ai.size != 1) {
            throw PatchException("Skip rewarded ads (Video Converter): the AI credits ad hook matched ${ai.size} methods")
        }

        val gates = rewardedGateFingerprint.matchAllOrNull().orEmpty()
        if (gates.size != 1) {
            throw PatchException("Skip rewarded ads (Video Converter): the rewarded ad gate matched ${gates.size} methods")
        }
        val gate = gates[0].method
        val release = releaseCallOf(gate, gate.definingClass)
            ?: throw PatchException("Skip rewarded ads (Video Converter): the rewarded gate has no release call")

        val credits = noiseReducerWatchAd.fingerprint.matchAllOrNull().orEmpty()
        if (credits.size != 1) {
            throw PatchException("Skip rewarded ads (Video Converter): the Noise Reducer switch matched ${credits.size} methods")
        }

        // p1 is the continuation of the coroutine, which the body never reads again once it has answered.
        ai[0].method.addInstructions(
            0,
            """
                const/4 p1, 0x0
                invoke-static {p1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                move-result-object p1
                return-object p1
            """,
        )

        // p2 is "allow the free first process": true for every unlock of the app, false only for the AI assistant.
        gate.addInstructionsWithLabels(
            0,
            """
                if-eqz p2, :ad
                invoke-direct {p0}, ${release.definingClass}->${release.name}()V
                return-void
            """,
            ExternalLabel("ad", gate.getInstruction(0)),
        )

        credits[0].method.addInstructions(
            0,
            """
                const/4 p1, 0x0
                return p1
            """,
        )
    }
}
