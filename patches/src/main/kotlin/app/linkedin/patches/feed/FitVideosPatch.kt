/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.linkedin.patches.feed

import app.linkedin.patches.shared.Constants.COMPATIBILITY_LINKEDIN
import app.linkedin.patches.shared.Constants.EXTENSION_PACKAGE
import app.linkedin.patches.shared.markIncluded
import app.linkedin.patches.shared.settingsPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val VIDEO_FIT = "$EXTENSION_PACKAGE/VideoFit;"
private const val FRAME_CLASS = "Lcom/linkedin/android/media/player/ui/AspectRatioFrameLayout;"
private const val TEXTURE_CLASS = "Lcom/linkedin/android/media/player/view/VideoTextureView;"

/** The frame around the video: measures itself in resize mode 0 (fit) to 4 (zoom, which crops). */
private object FrameMeasureFingerprint : Fingerprint(
    definingClass = FRAME_CLASS,
    name = "onMeasure",
    returnType = "V",
    parameters = listOf("I", "I"),
)

/** The texture view: builds a matrix from its scalingMode, 0 = stretch, 1 = crop, 2 = fit. */
private object TextureTransformFingerprint : Fingerprint(
    definingClass = TEXTURE_CLASS,
    name = "updateTransform",
    returnType = "V",
    parameters = listOf(),
)

/**
 * Puts a call to [hook] right after the first read of [field] in this method. The call gets the view (p0) and the
 * value just read, and its answer replaces that value in the same register.
 */
private fun MutableMethod.hookFieldRead(owner: String, field: String, hook: String) {
    val code = implementation ?: throw PatchException("$name of $definingClass has no code")
    val index = instructions.indexOfFirst { instruction ->
        if (instruction.opcode != Opcode.IGET) return@indexOfFirst false
        val reference = (instruction as ReferenceInstruction).reference as? FieldReference
        reference != null && reference.name == field && reference.definingClass == owner
    }
    if (index < 0) throw PatchException("$owner.$field is not read in $name, LinkedIn changed this class")

    val valueRegister = getInstruction<TwoRegisterInstruction>(index).registerA

    // Parameter registers sit at the end of the frame; p0 is the view itself.
    val parameterRegisters = 1 + parameterTypes.map { if (it.toString() == "J" || it.toString() == "D") 2 else 1 }.sum()
    val thisRegister = code.registerCount - parameterRegisters
    if (thisRegister > 15 || valueRegister > 15) {
        throw PatchException("$owner.$field hook needs v$thisRegister and v$valueRegister, but invoke-static takes v0..v15")
    }

    addInstructions(
        index + 1,
        """
            invoke-static { p0, v$valueRegister }, $VIDEO_FIT->$hook(Landroid/view/View;I)I
            move-result v$valueRegister
        """
    )
}

@Suppress("unused")
val fitVideosPatch = bytecodePatch(
    name = "Fit videos to screen",
    description = "Shows the whole video in full screen instead of cutting off the sides, sized to your screen.",
    default = true
) {
    compatibleWith(COMPATIBILITY_LINKEDIN)
    dependsOn(settingsPatch)

    execute {
        markIncluded("isFitVideosIncluded")

        FrameMeasureFingerprint.method.hookFieldRead(FRAME_CLASS, "resizeMode", "frameResizeMode")
        TextureTransformFingerprint.method.hookFieldRead(TEXTURE_CLASS, "scalingMode", "textureScalingMode")
    }
}
