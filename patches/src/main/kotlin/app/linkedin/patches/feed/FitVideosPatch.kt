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

/** The texture view with matrix scaling: builds a matrix from its scalingMode, 0 = stretch, 1 = crop, 2 = fit. */
private object TextureTransformFingerprint : Fingerprint(
    definingClass = TEXTURE_CLASS,
    name = "updateTransform",
    returnType = "V",
    parameters = listOf(),
)

/**
 * The texture view without matrix scaling (the Video tab and the older media viewer, VoyagerVideoView): onMeasure
 * reads scalingMode and in crop mode grows past its parent.
 */
private object TextureMeasureFingerprint : Fingerprint(
    definingClass = TEXTURE_CLASS,
    name = "onMeasure",
    returnType = "V",
    parameters = listOf("I", "I"),
)

/**
 * Puts a call to [hook] right after the first read of [field] in this method. The call gets the view (p0), the value
 * just read, then [passedParameters] of the method's own int parameters (p1, p2), and its answer replaces that value
 * in the same register.
 */
private fun MutableMethod.hookFieldRead(owner: String, field: String, hook: String, passedParameters: Int = 0) {
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
    // p1 and p2 of the hooked methods are ints, one register each, right after p0.
    val lastRegister = maxOf(valueRegister, thisRegister + passedParameters)
    if (thisRegister > 15 || lastRegister > 15) {
        throw PatchException(
            "$owner.$field hook needs registers up to v$lastRegister (p0 is v$thisRegister), but invoke-static takes v0..v15"
        )
    }

    val passed = (1..passedParameters).joinToString("") { ", p$it" }
    val descriptor = "Landroid/view/View;" + "I".repeat(1 + passedParameters)
    addInstructions(
        index + 1,
        """
            invoke-static { p0, v$valueRegister$passed }, $VIDEO_FIT->$hook($descriptor)I
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
        // p1 and p2 already hold the available width and height where scalingMode is read.
        TextureMeasureFingerprint.method.hookFieldRead(TEXTURE_CLASS, "scalingMode", "textureMeasureMode", passedParameters = 2)
    }
}
