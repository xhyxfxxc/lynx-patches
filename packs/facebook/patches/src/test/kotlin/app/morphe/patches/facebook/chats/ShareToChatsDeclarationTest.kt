/*
 * Copyright 2026 xhyxfxxc
 * Lynx build of Hushfacebook: https://github.com/xhyxfxxc/lynx-patches
 */
package app.morphe.patches.facebook.chats

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/**
 * Lynx test (2026-11-28): the share screen of "Share to Facebook chats" ships exported but switched
 * off, taking photos, videos and text only, and is declared once. This is the basis of the owner's
 * decision to publish the patch on by default: nobody sees it in Android's share menu until the
 * switch turns it on. It builds its own manifest, so it needs no Facebook file and runs in the plain
 * test task, unlike ShareToChatsManifestTest, which reads the real manifests from the fixtures.
 */
class ShareToChatsDeclarationTest {
    private fun Element.all(tag: String): List<Element> =
        getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

    @Test
    fun `the share target is exported but off, takes photos videos and text only, and is declared once`() {
        val manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.facebook.katana"><application/></manifest>""".byteInputStream(),
        )
        manifest.declareShareToChats()
        manifest.declareShareToChats()
        val targets = manifest.documentElement.all("activity")
        assertEquals("one share target", 1, targets.size)
        val target = targets.single()
        assertEquals(SHARE_TO_CHATS_ACTIVITY, target.getAttribute("android:name"))
        assertEquals("exported", "true", target.getAttribute("android:exported"))
        assertEquals("off until the switch turns it on", "false", target.getAttribute("android:enabled"))
        val filters = target.all("intent-filter")
        assertEquals("one filter per action", 2, filters.size)
        val taken = filters.associate { filter ->
            assertEquals(listOf("android.intent.category.DEFAULT"), filter.all("category").map { it.getAttribute("android:name") })
            filter.all("action").single().getAttribute("android:name") to filter.all("data").map { it.getAttribute("android:mimeType") }
        }
        assertEquals(
            mapOf(
                "android.intent.action.SEND" to listOf("image/*", "video/*", "text/plain"),
                "android.intent.action.SEND_MULTIPLE" to listOf("image/*", "video/*"),
            ),
            taken,
        )
    }
}
