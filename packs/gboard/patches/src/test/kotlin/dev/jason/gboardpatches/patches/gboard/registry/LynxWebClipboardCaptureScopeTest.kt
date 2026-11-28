// Copyright (C) 2026 xhyxfxxc
// SPDX-License-Identifier: GPL-3.0-or-later
package dev.jason.gboardpatches.patches.gboard.registry

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lynx decision: with the Web Clipboard patch off (the Lynx default) no Web Clipboard capture code is
 * installed in the patched keyboard. That holds only while `gboardWebClipboardCapturePatch` (the hook
 * that copies clipboard items, now also images and screenshots, to the Web Clipboard service) is
 * reachable ONLY through the Web Clipboard patch. These tests read the patches source tree and fail
 * if another patch starts to depend on the capture patch, or if Web Clipboard is switched back on by
 * default.
 */
class LynxWebClipboardCaptureScopeTest {
    private val repositoryRoot = repositoryRoot()

    @Test
    fun `capture patch is referenced only by the dependsOn of the Web Clipboard patch`() {
        val definitionFile = repositoryRoot.resolve(CAPTURE_DEFINITION_PATH)
        assertTrue("capture patch definition file moved: $CAPTURE_DEFINITION_PATH", Files.isRegularFile(definitionFile))
        assertTrue(
            "capture patch is no longer declared in $CAPTURE_DEFINITION_PATH",
            Regex("""\bval\s+$CAPTURE_PATCH_NAME\b""").containsMatchIn(read(definitionFile)),
        )

        val mainRoot = repositoryRoot.resolve(PATCHES_MAIN_PATH)
        val referencingFiles = Files.walk(mainRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) }
                .filter { path -> path.fileName.toString().substringAfterLast('.', "") in TEXT_EXTENSIONS }
                .filter { path -> path.toAbsolutePath().normalize() != definitionFile.toAbsolutePath().normalize() }
                .filter { path -> referenceRegex.containsMatchIn(read(path)) }
                .map { path -> mainRoot.relativize(path).toString().replace('\\', '/') }
                .toList()
                .sorted()
        }
        assertEquals(
            "$CAPTURE_PATCH_NAME must be referenced only in GboardPatchRegistry.kt",
            listOf(REGISTRY_RELATIVE_PATH),
            referencingFiles,
        )

        val registry = read(repositoryRoot.resolve(PATCHES_MAIN_PATH).resolve(REGISTRY_RELATIVE_PATH))
        val usages = referenceRegex.findAll(registry)
            .filterNot { match -> isImportLine(registry, match.range.first) }
            .map { match -> match.range.first }
            .toList()
        assertEquals("exactly one non-import reference in the registry", 1, usages.size)

        val declaration = declarationBlock(registry, WEB_CLIPBOARD_PATCH_NAME)
        val dependsOnRange = dependsOnRange(registry, declaration)
        assertTrue(
            "$CAPTURE_PATCH_NAME is referenced outside the dependsOn of $WEB_CLIPBOARD_PATCH_NAME",
            usages.single() in dependsOnRange,
        )
    }

    @Test
    fun `Web Clipboard patch is off by default`() {
        val registry = read(repositoryRoot.resolve(PATCHES_MAIN_PATH).resolve(REGISTRY_RELATIVE_PATH))
        val declaration = declarationBlock(registry, WEB_CLIPBOARD_PATCH_NAME)
        val text = registry.substring(declaration)
        assertTrue(
            "$WEB_CLIPBOARD_PATCH_NAME must declare default = false",
            Regex("""\bdefault\s*=\s*false\b""").containsMatchIn(text),
        )
        assertFalse(
            "$WEB_CLIPBOARD_PATCH_NAME must not declare default = true",
            Regex("""\bdefault\s*=\s*true\b""").containsMatchIn(text),
        )
    }

    private val referenceRegex = Regex("""\b$CAPTURE_PATCH_NAME\b""")

    private fun isImportLine(text: String, index: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', index) + 1
        return text.substring(lineStart, index).trimStart().startsWith("import ")
    }

    // The text of one top-level `val <name> = ...` declaration, from its `val` up to the next
    // top-level declaration (a line that starts at column 0 with `@`, `val`, `internal`, `private`,
    // `fun` or `object`) or the end of the file.
    private fun declarationBlock(source: String, name: String): IntRange {
        val start = Regex("""(?m)^val\s+$name\s*=""").find(source)?.range?.first
            ?: error("declaration of $name not found in $REGISTRY_RELATIVE_PATH")
        val bodyStart = source.indexOf('\n', start) + 1
        val next = Regex("""(?m)^(?:@|val\s|internal\s|private\s|fun\s|object\s|class\s)""")
            .find(source, bodyStart)?.range?.first ?: source.length
        return start until next
    }

    // The span of the `dependsOn( ... )` call inside the declaration, parentheses balanced.
    private fun dependsOnRange(source: String, declaration: IntRange): IntRange {
        val call = Regex("""\bdependsOn\s*\(""").find(source.substring(declaration))
            ?: error("$WEB_CLIPBOARD_PATCH_NAME has no dependsOn(...) call")
        val open = declaration.first + call.range.last
        var depth = 0
        var index = open
        while (index < source.length) {
            when (source[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return open..index
                }
            }
            index++
        }
        error("unbalanced dependsOn( in $WEB_CLIPBOARD_PATCH_NAME")
    }

    private fun read(path: Path): String = Files.readString(path, StandardCharsets.UTF_8)

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { directory -> directory.parent }
            .firstOrNull { directory ->
                Files.isRegularFile(directory.resolve("settings.gradle.kts"))
            } ?: error("Could not locate repository root from $workingDirectory")
    }

    private companion object {
        const val CAPTURE_PATCH_NAME = "gboardWebClipboardCapturePatch"
        const val WEB_CLIPBOARD_PATCH_NAME = "gboardWebClipboardPatch"
        const val PATCHES_MAIN_PATH = "patches/src/main"
        val TEXT_EXTENSIONS = setOf("kt", "kts", "java", "json")
        const val CAPTURE_DEFINITION_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/webclipboard/" +
                "GboardWebClipboardCapturePatch.kt"
        const val REGISTRY_RELATIVE_PATH =
            "kotlin/dev/jason/gboardpatches/patches/gboard/registry/GboardPatchRegistry.kt"
    }
}
