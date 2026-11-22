// Copyright (C) 2026 xhyxfxxc
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.jason.gboardpatches.patches.gboard.features.signaturebypass

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import dev.jason.gboardpatches.patches.gboard.shared.invokeRegisters
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// Runs the real transformation on a method built from the smali of Lsfr;->a in Gboard 18.5.4.
class GboardSignatureBypassTransformationTest {
    @Test
    fun `stock method gets the own package guard in front of an untouched original body`() {
        val method = stockMethod()
        val stockShapes = shapes(method)
        assertEquals(STOCK_INSTRUCTION_COUNT, stockShapes.size)

        method.applyGboardSignatureBypass()

        val instructions = method.implementation!!.instructions
        assertEquals(STOCK_INSTRUCTION_COUNT + GUARD_LENGTH, instructions.size)
        assertTrue(instructions[0].isOpcode("INVOKE_VIRTUAL"))
        assertEquals(
            listOf(6),
            instructions[0].invokeRegisters("Landroid/content/Context;->getPackageName()Ljava/lang/String;"),
        )
        assertTrue(instructions[1].isOpcode("MOVE_RESULT_OBJECT"))
        assertTrue(instructions[2].isOpcode("INVOKE_VIRTUAL"))
        assertEquals(
            listOf(0, 7),
            instructions[2].invokeRegisters("Ljava/lang/String;->equals(Ljava/lang/Object;)Z"),
        )
        assertTrue(instructions[3].isOpcode("MOVE_RESULT"))
        assertTrue(instructions[4].isOpcode("IF_EQZ"))
        assertTrue(instructions[5].isOpcode("CONST_4"))
        assertTrue(instructions[6].isOpcode("RETURN"))
        assertEquals(0, (instructions[6] as OneRegisterInstruction).registerA)
        // The guard's branch lands on the first instruction of the original body.
        val branch = instructions[4] as BuilderOffsetInstruction
        assertEquals(GUARD_LENGTH, branch.target.location.index)
        assertSame(instructions[GUARD_LENGTH], branch.target.location.instruction)
        // Everything after the guard is exactly the stock body (same opcodes, registers, literals,
        // references and branch offsets), so the original three returns are still there.
        assertEquals(stockShapes, shapes(method).drop(GUARD_LENGTH))
        assertEquals(
            listOf(6, 4, 3),
            instructions.drop(GUARD_LENGTH)
                .filter { it.isOpcode("RETURN") }
                .map { (it as OneRegisterInstruction).registerA },
        )
    }

    @Test
    fun `an already patched method is recognised and left alone`() {
        val method = stockMethod()
        method.applyGboardSignatureBypass()
        val once = shapes(method)

        method.applyGboardSignatureBypass()

        assertEquals(once, shapes(method))
    }

    @Test
    fun `upstream old shape with every return forced to true is refused`() {
        val method = method(
            STOCK_BODY
                .replace("return p0", "const/4 p0, 0x1\n    return p0")
                .replace("return v4", "const/4 v4, 0x1\n    return v4")
                .replace("return v3", "const/4 v3, 0x1\n    return v3"),
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            method.applyGboardSignatureBypass()
        }
        assertTrue(failure.message!!, failure.message!!.contains("MALFORMED"))
    }

    @Test
    fun `a guarded method whose original body was altered is refused`() {
        val method = stockMethod()
        method.applyGboardSignatureBypass()
        // Alter the original body: drop its first instruction (const/4 v0, 0x3).
        method.removeInstruction(GUARD_LENGTH)

        assertThrows(IllegalStateException::class.java) {
            method.applyGboardSignatureBypass()
        }
    }

    @Test
    fun `a method with a changed body is refused before any change`() {
        val method = method(STOCK_BODY.replace("const/4 v5, 0x2", "const/4 v5, 0x3"))
        val before = shapes(method)

        assertThrows(IllegalStateException::class.java) {
            method.applyGboardSignatureBypass()
        }
        assertEquals(before, shapes(method))
    }

    private fun stockMethod(): MutableMethod = method(STOCK_BODY)

    private fun method(body: String): MutableMethod {
        val placeholder = ImmutableMethodImplementation(
            REGISTER_COUNT,
            listOf(ImmutableInstruction10x(Opcode.RETURN_VOID)),
            null,
            null,
        )
        val immutable = ImmutableMethod(
            "Lsfr;",
            "a",
            listOf(
                ImmutableMethodParameter("Landroid/content/Context;", null, null),
                ImmutableMethodParameter("Ljava/lang/String;", null, null),
            ),
            "Z",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            null,
            null,
            placeholder,
        )
        val method = MutableMethod(immutable)
        method.addInstructions(0, body.trimIndent())
        method.removeInstruction(method.implementation!!.instructions.size - 1)
        return method
    }

    private fun shapes(method: MutableMethod): List<String> {
        val instructions = method.implementation!!.instructions
        return instructions.mapIndexed { index, instruction -> instruction.shape(instructions, index) }
    }

    private fun Instruction.shape(instructions: List<Instruction>, index: Int): String = buildString {
        append(opcode.name)
        (this@shape as? OneRegisterInstruction)?.let { append(" a=").append(it.registerA) }
        (this@shape as? com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction)
            ?.let { append(" b=").append(it.registerB) }
        (this@shape as? com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction)
            ?.let { append(" b=").append(it.registerB).append(" c=").append(it.registerC) }
        (this@shape as? com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction)
            ?.let { append(" lit=").append(it.narrowLiteral) }
        (this@shape as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)
            ?.let { append(" ref=").append(it.reference) }
        (this@shape as? com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction)
            ?.let { append(" regs=").append(it.registerC).append(',').append(it.registerD) }
        // Jump distance in instructions, found through the target instruction itself (the builder's
        // code addresses and cached indexes are only final after the method is compiled).
        (this@shape as? BuilderOffsetInstruction)?.let {
            append(" jump=").append(instructions.indexOf(it.target.location.instruction) - index)
        }
    }

    private companion object {
        const val REGISTER_COUNT = 8
        const val GUARD_LENGTH = 7
        const val STOCK_INSTRUCTION_COUNT = 26

        // Lsfr;->a(Landroid/content/Context;Ljava/lang/String;)Z of Gboard 18.5.4 (p0 = v6, p1 = v7).
        val STOCK_BODY = """
            const/4 v0, 0x3
            new-array v1, v0, [[B
            sget-object v2, Lsfr;->e:[B
            const/4 v3, 0x0
            aput-object v2, v1, v3
            sget-object v2, Lsfr;->d:[B
            const/4 v4, 0x1
            aput-object v2, v1, v4
            sget-object v2, Lsfr;->c:[B
            const/4 v5, 0x2
            aput-object v2, v1, v5
            invoke-static {p0, p1}, Lsfr;->c(Landroid/content/Context;Ljava/lang/String;)[B
            move-result-object p0
            if-nez p0, :cond_1b
            sget-boolean p0, Lses;->b:Z
            return p0
            :cond_1b
            move p1, v3
            :goto_1c
            if-ge p1, v0, :cond_2a
            aget-object v2, v1, p1
            invoke-static {p0, v2}, Ljava/util/Arrays;->equals([B[B)Z
            move-result v2
            if-eqz v2, :cond_27
            return v4
            :cond_27
            add-int/lit8 p1, p1, 0x1
            goto :goto_1c
            :cond_2a
            return v3
        """
    }
}
