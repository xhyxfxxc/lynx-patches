/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21.
 */

package app.amazon.patches.manifest

import app.amazon.patches.shared.Constants.COMPATIBILITY_AMAZON
import app.loki.patches.universal.addMenuFlag
import app.loki.patches.universal.menuPatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private val PERMISSION_DECLARATION_TAGS = listOf(
    "permission",
    "permission-group",
    "permission-tree",
)

private val PERMISSION_REFERENCE_TAGS = listOf(
    "uses-permission",
    "uses-permission-sdk-23",
    "uses-permission-sdk-m",
)

private val PERMISSION_REFERENCE_ATTRIBUTES = listOf(
    "android:permission",
    "android:readPermission",
    "android:writePermission",
)

@Suppress("unused")
val amazonFixDuplicatePermissionsPatch = resourcePatch(
    name = "Fix Amazon manifest conflicts",
    description = "Updates shared Amazon permissions so other Amazon apps can coexist.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AMAZON)
    dependsOn(menuPatch)

    execute {
        document("AndroidManifest.xml").use { document ->
            document.addMenuFlag("app.loki.menu.amazonmanifest")

            val manifest = document.documentElement
            val packageName = manifest.getAttribute("package")
            val uniquePrefix = "$packageName.morphe"
            val permissionRenames = linkedMapOf<String, String>()

            PERMISSION_DECLARATION_TAGS.forEach { tag ->
                val nodes = document.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as Element
                    val oldName = node.getAttribute("android:name")
                    if (oldName.isBlank() || oldName.startsWith("$uniquePrefix.")) continue

                    val newName = "$uniquePrefix.$oldName"
                    node.setAttribute("android:name", newName)
                    permissionRenames[oldName] = newName
                }
            }

            PERMISSION_REFERENCE_TAGS.forEach { tag ->
                val nodes = document.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as Element
                    val newName = permissionRenames[node.getAttribute("android:name")] ?: continue
                    node.setAttribute("android:name", newName)
                }
            }

            val allNodes = document.getElementsByTagName("*")
            for (i in 0 until allNodes.length) {
                val node = allNodes.item(i) as Element

                PERMISSION_REFERENCE_ATTRIBUTES.forEach { attribute ->
                    val newName = permissionRenames[node.getAttribute(attribute)] ?: return@forEach
                    node.setAttribute(attribute, newName)
                }
            }
        }
    }
}
