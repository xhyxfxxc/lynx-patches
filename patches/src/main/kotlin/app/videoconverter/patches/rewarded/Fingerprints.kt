/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.videoconverter.patches.rewarded

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private val Method.code: List<Instruction>
    get() = implementation?.instructions?.toList() ?: emptyList()

private fun Method.startsClass(type: String) = code.any { instruction ->
    instruction.opcode == Opcode.CONST_CLASS &&
        ((instruction as? ReferenceInstruction)?.reference as? TypeReference)?.type == type
}

private fun Method.hasString(value: String) = code.any { instruction ->
    (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
        ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string == value
}

// ---- The "watch an ad to unlock" gate -------------------------------------------------------------------------------

/** The cross-promotion rewarded screen. Only the app's one rewarded-ad controller starts it. */
private const val CROSS_REWARDED_ACTIVITY = "Lorg/inverseai/cross_promo/activity/RewardedAdActivity;"

/**
 * Where the controller hands the stored action on. Every "watch an ad" button first stores the gated action in the
 * controller (a Runnable) and then asks it to play an ad. When the ad has been rewarded and closed, the controller
 * posts that Runnable once through a private method with no parameters, and the app's own "your first process is
 * free" shortcut (the first branch of the gate below) does the same without an ad: it ends with a call of that
 * method straight before its `return-void`. That call is what is looked for; no name is used.
 *
 * Returns the reference of that method, or null when the shape is not there.
 */
internal fun releaseCallOf(method: Method, definingClass: String): MethodReference? {
    val code = method.code
    for (index in 0 until code.size - 1) {
        val instruction = code[index]
        if (instruction.opcode != Opcode.INVOKE_DIRECT || code[index + 1].opcode != Opcode.RETURN_VOID) continue
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: continue
        if (reference.definingClass == definingClass && reference.parameterTypes.isEmpty() &&
            reference.returnType == "V"
        ) {
            return reference
        }
    }
    return null
}

/**
 * The gate: `show(Context context, boolean allowFreeProcess)` of the controller class, public and not static. All of
 * the app's own unlocks reach it as `show(context, true)` (a one-argument convenience method passes true); the AI
 * assistant is the only caller that passes false, and the AI patch below stops it before it gets here.
 *
 * The controller is the class that starts the cross-promotion rewarded activity. Static synthetic bridges
 * (`show(controller, context, flag, int, Object)`) have more parameters and do not match.
 */
internal val rewardedGateFingerprint = Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Z"),
    custom = { method, classDef ->
        AccessFlags.PUBLIC.isSet(method.accessFlags) && !AccessFlags.STATIC.isSet(method.accessFlags) &&
            classDef.methods.any { it.startsClass(CROSS_REWARDED_ACTIVITY) } &&
            releaseCallOf(method, classDef.type) != null
    },
)

// ---- AI assistant credits --------------------------------------------------------------------------------------------

/**
 * The AI assistant's "watch an ad for credits" hook, a coroutine body that stores a Runnable in the same controller,
 * shows an ad and answers with whether the ad was earned. The credits themselves are granted by InverseAI's server
 * (the ad is verified there and the credit balance is read back afterwards), so an ad that did not play must never
 * count as watched. Found by the two log strings it carries; the AI library's own copy of the log call has a different
 * first string.
 */
internal val aiCreditsAdFingerprint = Fingerprint(
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    strings = listOf("showRewardedAdForCredits"),
    custom = { method, _ -> method.hasString("showRewardedAdForCredits") && method.hasString("AppHooks") },
)
