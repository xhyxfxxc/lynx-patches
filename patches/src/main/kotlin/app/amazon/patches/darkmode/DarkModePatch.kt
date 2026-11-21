/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21. The dark-mode CSS in the extension comes from amznkiller by hxreborn.
 */

package app.amazon.patches.darkmode

import app.amazon.patches.shared.Constants.COMPATIBILITY_AMAZON
import app.amazon.patches.shared.Constants.HELPER
import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import org.w3c.dom.Element

/**
 * Amazon's app theme is a light theme (AmazonTheme, Theme.AppCompat.Light), and Amazon sets
 * `android:forceDarkAllowed` to false on it. WebView's own algorithmic darkening
 * (WebSettings.setAlgorithmicDarkeningAllowed) only applies when the app theme is dark, so with this theme it does
 * nothing. What does darken the whole app, WebView pages included, is Android's Force Dark: with a light theme that
 * allows it, the system applies it whenever the phone is in dark mode, and WebView then darkens page content that
 * has no dark styles of its own ("if Android is applying Force Dark to WebView then WebView ... behave as if it were
 * set to true", developer.android.com WebSettings reference). So the patch only has to allow Force Dark, and it
 * follows the phone's dark mode by itself. There is no "always on": Force Dark cannot be started from inside the app
 * while the phone is in light mode without changing the app's theme.
 */
private val darkModeOption = stringOption(
    key = "amazonDarkMode",
    default = "follow_system",
    values = mapOf(
        "Off" to "off",
        "Follow system" to "follow_system",
    ),
    title = "Dark mode",
    description = "Off leaves Amazon's app untouched. Follow system darkens Amazon's pages and bottom bar while " +
        "your phone is in dark mode.",
)

/** Anything but Off (an old saved choice such as "on" included) is Follow system. */
private fun darkModeOn() = darkModeOption.value != "off"

private val amazonDarkModeResourcePatch = resourcePatch {
    execute {
        if (!darkModeOn()) return@execute

        // v32.12 and below: forceDarkAllowed lives in res/values-v29/styles.xml
        // v32.13+: values-v29 removed, attribute moved to res/values/styles.xml
        // Try both so the patch works across versions without an ENOENT crash.
        val candidates = listOf("res/values-v29/styles.xml", "res/values/styles.xml")
        var patched = 0
        for (path in candidates) {
            runCatching {
                document(path).use { doc ->
                    val items = doc.getElementsByTagName("item")
                    for (i in 0 until items.length) {
                        val item = items.item(i) as? Element ?: continue
                        if (item.getAttribute("name") == "android:forceDarkAllowed") {
                            item.textContent = "true"
                            patched++
                        }
                    }
                }
            }
        }
        if (patched == 0) throw Exception(
            "android:forceDarkAllowed not found in any styles.xml, " +
                "check resource layout for this version",
        )

        // Recorded only when dark mode is on, so Patch status does not list an Off patch as applied.
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.amazondark") }
    }
}

@Suppress("unused")
val amazonDarkModePatch = bytecodePatch(
    name = "Dark mode",
    description = "Lets Amazon Shopping follow your phone's dark mode, pages and bottom bar included.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AMAZON)
    dependsOn(menuPatch, amazonDarkModeResourcePatch)
    extendWith("extensions/extension.mpe")
    darkModeOption()

    execute {
        if (!darkModeOn()) return@execute

        // Page fixes (product images, buttons) while the phone is dark. .locals 1, so v0 is free here: the method's
        // first original instruction writes v0 before anything reads it. The url (p1, v2) is read later by
        // "about:blank".equals(p1), so it must not be touched.
        MShopWebViewContainerOnPageFinishedFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/amazon/mShop/web/MShopWebViewContainer;->mWebView:Lcom/amazon/mShop/web/MShopWebView;
                invoke-static {v0}, $HELPER->injectDarkMode(Landroid/webkit/WebView;)V
            """.trimIndent(),
        )

        // Tab bar icon tint: inject before every return-object in getTabIcon() so the returned ImageView is
        // tinted for the dark bar. getTabIcon() returns Landroid/widget/ImageView; we intercept the result
        // register at each return site (working reversed to preserve indices). No scratch register is needed.
        val tabMethod = BaseTabControllerGetTabIconFingerprint.method
        val returnIndices = tabMethod.implementation!!.instructions
            .mapIndexedNotNull { i, instr -> if (instr.opcode == Opcode.RETURN_OBJECT) i else null }
            .reversed()

        for (idx in returnIndices) {
            val reg = tabMethod.getInstruction<OneRegisterInstruction>(idx).registerA
            tabMethod.addInstructions(
                idx,
                "invoke-static {v$reg}, $HELPER->tintTabIconIfDark(Landroid/widget/ImageView;)V",
            )
        }
    }
}
