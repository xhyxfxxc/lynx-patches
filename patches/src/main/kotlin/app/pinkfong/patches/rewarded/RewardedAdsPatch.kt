/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.pinkfong.patches.rewarded

import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.pinkfong.patches.shared.Constants.REWARDED_GAMES
import app.pinkfong.patches.shared.Constants.REWARDS

private val rewardsFlag = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.pinkfongrewards") }
    }
}

/**
 * Some of the games have a "watch a video to get a reward" button. This patch gives the reward without any video:
 * the bridge is told an ad loaded, and when the game shows it, that the ad was shown, the reward was earned and the ad
 * was closed, which is what the game waits for before it hands the reward out.
 *
 * The Google Mobile Ads Unity bridge has a class per rewarded format (rewarded, rewarded interstitial), in two
 * generations (next-gen and legacy; a game uses one, and some carry the other as dead code). In each class `load`
 * and `show` are replaced with a call into the extension (PinkfongRewards) that runs the game's own callbacks the way
 * the bridge does: on the bridge's executor in the next-gen bridge, on a new thread in the legacy bridge. The game's
 * callback and the executor are read from the bridge object's own fields and handed over; the extension finds the
 * callback methods through the interfaces of the callback (a proxy that Unity makes). Because `load` is replaced,
 * the load never reaches the ad SDK, whether or not Hide ads (Pinkfong) is on.
 *
 * No ad is ever requested, so no reward item exists: `getRewardItem` returns null with a log in both bridges when
 * there is no ad (their own check), which the game's reward code does not depend on.
 */
@Suppress("unused")
val pinkfongRewardedAdsPatch = bytecodePatch(
    name = "Skip rewarded ads (Pinkfong)",
    description = "Gives the reward of a \"watch a video\" button straight away, without playing an ad: the game is " +
        "told the ad was watched to the end.",
    default = true,
) {
    compatibleWith(*REWARDED_GAMES.toTypedArray())
    dependsOn(menuPatch, rewardsFlag)
    extendWith("extensions/extension.mpe")

    execute {
        var replaced = 0

        for (bridge in rewardedBridges) {
            val load = bridge.load.matchOrNull()?.method ?: continue
            val show = bridge.show.matchOrNull()?.method ?: continue
            // Two free locals hold the callback and the executor; the methods have two or more in every game.
            requireLocals(bridge.label, load)
            requireLocals(bridge.label, show)

            // The replaced bodies read the callback (and executor) from p0 and call the extension, which answers on
            // the right thread. The type of the register does not matter to the extension: it takes Objects.
            fun body(entry: String) = if (bridge.executorField != null) {
                """
                    iget-object v0, p0, ${bridge.callbackField}
                    iget-object v1, p0, ${bridge.executorField}
                    invoke-static {v0, v1}, $REWARDS->$entry(Ljava/lang/Object;Ljava/util/concurrent/Executor;)V
                    return-void
                """
            } else {
                """
                    iget-object v0, p0, ${bridge.callbackField}
                    invoke-static {v0}, $REWARDS->$entry(Ljava/lang/Object;)V
                    return-void
                """
            }
            load.addInstructions(0, body("loaded"))
            show.addInstructions(0, body("shown"))
            replaced++
        }

        if (replaced == 0) {
            throw PatchException("Skip rewarded ads (Pinkfong): no rewarded class of the Unity ads bridge was found")
        }
    }
}

private fun requireLocals(label: String, method: MutableMethod) {
    val locals = (method.implementation?.registerCount ?: 0) - (method.parameterTypes.size + 1)
    if (locals < 2) throw PatchException("Skip rewarded ads (Pinkfong): $label ${method.name} has $locals free registers")
}
