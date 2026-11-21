/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21: the Keepa and CamelCamelCamel chart blocks became one price history card.
 */

package app.amazon.patches.pricecharts

import app.amazon.patches.shared.Constants.COMPATIBILITY_AMAZON
import app.amazon.patches.shared.Constants.HELPER
import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch

private val priceChartsFlag = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { it.addMenuFlag("app.loki.menu.amazonprice") }
    }
}

/**
 * A price history card on product pages, in the style of Amazon's own (which is not offered in Australia): a
 * collapsed "Price history" row that opens to a verdict, the lowest, average and highest price, range buttons and
 * the CamelCamelCamel chart. The numbers come from the CamelCamelCamel product page, fetched in the background
 * when the user taps the card. The card, "Load automatically" and the default range are switches on the Amazon page
 * of the Lynx Patches screen, not patch options, so they can be changed without patching again.
 *
 * Three hooks: the page is set up when it finishes loading (normal loads) and when its WebView is shown (pages
 * loaded ahead of time), and `window.lynxPrice`, the object the card asks for the numbers through, is added to each
 * WebView as it is created.
 */
@Suppress("unused")
val amazonPriceChartsPatch = bytecodePatch(
    name = "Price history card",
    description = "Adds a price history card to Amazon product pages: lowest, average and highest price and a " +
        "chart for 30 days, 90 days, 1 year or all time, from CamelCamelCamel. Nothing is requested until you tap it.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AMAZON)
    dependsOn(menuPatch, priceChartsFlag)
    extendWith("extensions/extension.mpe")

    execute {
        // Non-jumpstarted: p1=WebView, p2=url
        MShopWebViewClientOnPageFinishedFingerprint.method.addInstructions(
            0,
            "invoke-static {p1, p2}, $HELPER->injectPriceCard(Landroid/webkit/WebView;Ljava/lang/String;)V",
        )

        // Jumpstarted + all: .locals 4, get mWebView + url via getNavRequestUrl
        InteractionWebFragmentPostShownFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/amazon/mobile/mash/MASHWebFragment;->mWebView:Lcom/amazon/mobile/mash/MASHWebView;
                invoke-virtual {p0}, Lcom/amazon/mobile/mash/MASHWebFragment;->getNavRequestUrl()Ljava/lang/String;
                move-result-object v1
                invoke-static {v0, v1}, $HELPER->injectPriceCard(Landroid/webkit/WebView;Ljava/lang/String;)V
            """.trimIndent(),
        )

        // Every WebView, once, as it is created: p0=this. No register is needed.
        MShopWebViewInitializeFingerprint.method.addInstructions(
            0,
            "invoke-static {p0}, $HELPER->attachPriceBridge(Landroid/webkit/WebView;)V",
        )
    }
}
