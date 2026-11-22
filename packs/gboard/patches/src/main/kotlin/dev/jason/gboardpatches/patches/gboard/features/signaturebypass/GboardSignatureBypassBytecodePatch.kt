package dev.jason.gboardpatches.patches.gboard.features.signaturebypass

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationPlan
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationState
import dev.jason.gboardpatches.patches.gboard.shared.applyVerified
import dev.jason.gboardpatches.patches.gboard.shared.invokeRegisters
import dev.jason.gboardpatches.patches.gboard.shared.isFieldReference
import dev.jason.gboardpatches.patches.gboard.shared.isLiteralWrite
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private const val SIGNATURE_UTILS_CLASS = "Lsfr;"

internal val gboardSignatureBypassBytecodePatch = bytecodePatch(
    description = "強制通過 Gboard 自身的簽章白名單檢查。"
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        injectSignatureBypass()
    }
}

context(context: BytecodePatchContext)
private fun injectSignatureBypass() = with(context) {
    mutableClass(SIGNATURE_UTILS_CLASS)
        .methods
        .findGboardSignatureBypassTargetOrThrow()
        .applyGboardSignatureBypass()
}

internal fun Iterable<MutableMethod>.findGboardSignatureBypassTargetOrThrow(): MutableMethod {
    val targets = filter(MutableMethod::isExactGboardSignatureBypassTarget)
    check(targets.size == 1) {
        "Expected exactly one $SIGNATURE_CHECK_DESCRIPTOR target"
    }
    return targets.single()
}

// Lynx change: upstream forced all three returns of this method to true. That also let any app that
// is merely NAMED like a Google tool through Gboard's exported debug bridge provider
// (WebDebugBridgeContentProvider checks the caller's package name and then calls this method for
// the caller). Now only Gboard's own start-up self check is let through, because it passes its own
// package name (a re-signed Gboard fails the check otherwise). Every other call falls through into
// the unmodified original body.
internal fun MutableMethod.applyGboardSignatureBypass() {
    applyVerified(
        VerifiedTransformationPlan(
            targetName = SIGNATURE_CHECK_DESCRIPTOR,
            classify = MutableMethod::classifyGboardSignatureBypass,
            mutate = { method ->
                method.insertOwnPackageGuard()
                method
            },
        ),
    )
}

private fun MutableMethod.classifyGboardSignatureBypass(): VerifiedTransformationState {
    check(isExactGboardSignatureBypassTarget()) {
        "Refusing non-target signature bypass method $definingClass->$name"
    }
    val allInstructions = implementation?.instructions
        ?: error("No instructions available in $SIGNATURE_CHECK_DESCRIPTOR")
    check(implementation!!.registerCount == TARGET_REGISTER_COUNT) {
        "Unexpected register count in $SIGNATURE_CHECK_DESCRIPTOR"
    }
    check(implementation!!.tryBlocks.isEmpty()) {
        "Unexpected try blocks in $SIGNATURE_CHECK_DESCRIPTOR"
    }
    // The stock body checks below run on the instructions after the Lynx guard, when it is present.
    val hasGuard = allInstructions.hasOwnPackageGuard()
    val instructions = if (hasGuard) allInstructions.drop(OWN_PACKAGE_GUARD_LENGTH) else allInstructions

    val returnIndices = instructions.indices.filter { index ->
        instructions[index].isOpcode("RETURN")
    }
    check(returnIndices.size == TARGET_RETURN_REGISTERS.size) {
        "Expected three normal returns in $SIGNATURE_CHECK_DESCRIPTOR"
    }
    val returnRegisters = returnIndices.map { returnIndex ->
        (instructions[returnIndex] as? OneRegisterInstruction)?.registerA
            ?: error("RETURN at $returnIndex has no register")
    }
    check(returnRegisters == TARGET_RETURN_REGISTERS) {
        "Unexpected normal return registers in $SIGNATURE_CHECK_DESCRIPTOR"
    }
    check(instructions.count { it.isMethodReference(DIGEST_METHOD_DESCRIPTOR) } == 1) {
        "Expected exact digest call in $SIGNATURE_CHECK_DESCRIPTOR"
    }
    check(instructions.count { it.isMethodReference(ARRAYS_EQUALS_DESCRIPTOR) } == 1) {
        "Expected exact digest comparison in $SIGNATURE_CHECK_DESCRIPTOR"
    }
    TARGET_FIELD_DESCRIPTORS.forEach { descriptor ->
        check(instructions.count { it.isFieldReference(descriptor) } == 1) {
            "Expected exact field $descriptor in $SIGNATURE_CHECK_DESCRIPTOR"
        }
    }
    TARGET_BASELINE_LITERALS.forEach { expected ->
        check(instructions.any { instruction -> instruction.matchesLiteral(expected) }) {
            "Expected exact literal shape $expected in $SIGNATURE_CHECK_DESCRIPTOR; " +
                "actual=${instructions.mapNotNull { instruction -> instruction.literalShape() }}"
        }
    }

    // Upstream's old shape (every return forced to true) has extra "const/4 vX, 0x1" literals in
    // the body, so it is reported as MALFORMED and refused; it is never patched a second time.
    val completedReturns = returnIndices.count { returnIndex ->
        val returned = instructions[returnIndex] as OneRegisterInstruction
        instructions.getOrNull(returnIndex - 1).isForcedTrueFor(returned.registerA)
    }
    val oneLiteralCount = instructions.count { instruction ->
        instruction.normalizedOpcode().startsWith("CONST") &&
            (instruction as? NarrowLiteralInstruction)?.narrowLiteral == 1
    }
    val bodyIsStock = completedReturns == 0 && oneLiteralCount == TARGET_STOCK_ONE_LITERAL_COUNT
    return when {
        !bodyIsStock -> VerifiedTransformationState.MALFORMED
        hasGuard -> VerifiedTransformationState.PATCHED
        else -> VerifiedTransformationState.STOCK
    }
}

private fun MutableMethod.insertOwnPackageGuard() {
    val originalFirstInstruction = implementation?.instructions?.firstOrNull()
        ?: error("No instructions available in $SIGNATURE_CHECK_DESCRIPTOR")
    addInstructionsWithLabels(
        0,
        OWN_PACKAGE_GUARD,
        ExternalLabel(ORIGINAL_BODY_LABEL, originalFirstInstruction),
    )
}

// True when the method starts with exactly the Lynx guard and its branch jumps to the first
// instruction after the guard, which is the start of the original body.
private fun List<Instruction>.hasOwnPackageGuard(): Boolean {
    if (size <= OWN_PACKAGE_GUARD_LENGTH) return false
    val branch = this[4] as? BuilderOffsetInstruction ?: return false
    return this[0].isOpcode("INVOKE_VIRTUAL") &&
        this[0].invokeRegisters(CONTEXT_GET_PACKAGE_NAME_DESCRIPTOR) == listOf(CONTEXT_REGISTER) &&
        this[1].isOpcode("MOVE_RESULT_OBJECT") && this[1].isRegisterA(0) &&
        this[2].isOpcode("INVOKE_VIRTUAL") &&
        this[2].invokeRegisters(STRING_EQUALS_DESCRIPTOR) == listOf(0, PACKAGE_NAME_REGISTER) &&
        this[3].isOpcode("MOVE_RESULT") && this[3].isRegisterA(0) &&
        this[4].isOpcode("IF_EQZ") && this[4].isRegisterA(0) &&
        branch.target.location.index == OWN_PACKAGE_GUARD_LENGTH &&
        this[5].isForcedTrueFor(0) &&
        this[6].isOpcode("RETURN") && this[6].isRegisterA(0)
}

private fun Instruction.isRegisterA(register: Int): Boolean =
    (this as? OneRegisterInstruction)?.registerA == register

private fun MutableMethod.isExactGboardSignatureBypassTarget(): Boolean =
    definingClass == SIGNATURE_UTILS_CLASS &&
        name == SIGNATURE_CHECK_METHOD_NAME &&
        returnType == "Z" &&
        parameterTypes == SIGNATURE_CHECK_PARAMETERS &&
        accessFlags == TARGET_ACCESS_FLAGS

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction?.isForcedTrueFor(
    register: Int,
): Boolean = this?.isOpcode("CONST_4") == true && isLiteralWrite(register, 1)

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.matchesLiteral(
    expected: LiteralShape,
): Boolean = literalShape() == expected

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.literalShape(): LiteralShape? {
    if (!isOpcode("CONST_4")) return null
    val register = (this as? OneRegisterInstruction)?.registerA ?: return null
    val literal = (this as? NarrowLiteralInstruction)?.narrowLiteral ?: return null
    return LiteralShape(register, literal)
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction
    .normalizedOpcode(): String = opcode.name.uppercase().replace('-', '_').replace('/', '_')

private data class LiteralShape(
    val register: Int,
    val literal: Int,
)

private const val SIGNATURE_CHECK_METHOD_NAME = "a"
private const val SIGNATURE_CHECK_DESCRIPTOR =
    "Lsfr;->a(Landroid/content/Context;Ljava/lang/String;)Z"
private val SIGNATURE_CHECK_PARAMETERS =
    listOf("Landroid/content/Context;", "Ljava/lang/String;")
private const val DIGEST_METHOD_DESCRIPTOR =
    "Lsfr;->c(Landroid/content/Context;Ljava/lang/String;)[B"
private const val ARRAYS_EQUALS_DESCRIPTOR = "Ljava/util/Arrays;->equals([B[B)Z"
private val TARGET_FIELD_DESCRIPTORS = listOf(
    "Lsfr;->e:[B",
    "Lsfr;->d:[B",
    "Lsfr;->c:[B",
    "Lses;->b:Z",
)
private val TARGET_BASELINE_LITERALS = listOf(
    LiteralShape(0, 3),
    LiteralShape(3, 0),
    LiteralShape(4, 1),
    LiteralShape(5, 2),
)
private val TARGET_RETURN_REGISTERS = listOf(6, 4, 3)
private const val TARGET_REGISTER_COUNT = 8
private const val TARGET_STOCK_ONE_LITERAL_COUNT = 1
private const val CONTEXT_GET_PACKAGE_NAME_DESCRIPTOR =
    "Landroid/content/Context;->getPackageName()Ljava/lang/String;"
private const val STRING_EQUALS_DESCRIPTOR = "Ljava/lang/String;->equals(Ljava/lang/Object;)Z"
// The method has 8 registers and 2 parameters: p0 (the Context) is v6, p1 (the package name) is v7.
private const val CONTEXT_REGISTER = 6
private const val PACKAGE_NAME_REGISTER = 7
private const val ORIGINAL_BODY_LABEL = "lynx_original"
private const val OWN_PACKAGE_GUARD_LENGTH = 7
// v0 is free at method entry: the original body writes it first (const/4 v0, 0x3).
private val OWN_PACKAGE_GUARD = """
    invoke-virtual {p0}, $CONTEXT_GET_PACKAGE_NAME_DESCRIPTOR
    move-result-object v0
    invoke-virtual {v0, p1}, $STRING_EQUALS_DESCRIPTOR
    move-result v0
    if-eqz v0, :$ORIGINAL_BODY_LABEL
    const/4 v0, 0x1
    return v0
""".trimIndent()
private val TARGET_ACCESS_FLAGS = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value
