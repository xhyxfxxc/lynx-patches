/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.utils

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.FeatureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.utils.Constants.FSTS_DESCRIPTOR
import app.crimera.patches.twitter.utils.Constants.SSTS_DESCRIPTOR
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

fun enableSettings(functionName: String) {
    SettingsStatusLoadFingerprint.method.addInstruction(
        0,
        "$SSTS_DESCRIPTOR->$functionName()V",
    )
}

fun flagSettings(functionName: String) {
    FeatureFlagLoadFingerprint.method.addInstruction(
        0,
        "$FSTS_DESCRIPTOR->$functionName()V",
    )
}

/**
 * Type referenced by a matched instruction such as a check-cast or new-instance.
 *
 * Lynx: copied from app.crimera.patches.instagram.utils.Utils.kt of upstream Piko, because this
 * pack does not carry the Instagram half of Piko. Same code, only the package differs.
 */
internal val Match.InstructionMatch.typeReference: String
    get() = instruction.getReference<TypeReference>()!!.type
