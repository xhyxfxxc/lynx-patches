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
import app.loki.patches.universal.replaceStrings
import app.morphe.patcher.patch.bytecodePatch
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

/** The permissions the manifest step renamed, old name to new name, for the code step to follow. */
private val permissionRenames = linkedMapOf<String, String>()

/**
 * Renames every permission the app declares, except its own, so other Amazon apps that declare the same ones can be
 * installed beside it. "Its own" are the names that start with the app's package (the
 * DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION): Amazon's code builds that name at run time as
 * getPackageName() + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" and registers receivers with it, so renaming it
 * here would make that registration throw on Android 12. With "Change package name" the package name has already
 * changed at this point (patches run in name order and "Change package name" comes first), the clone patch renamed
 * those permissions along with it, and they are left alone here as well.
 */
private val renameManifestPermissions = resourcePatch {
    dependsOn(menuPatch)

    execute {
        permissionRenames.clear()
        document("AndroidManifest.xml").use { document ->
            document.addMenuFlag("app.loki.menu.amazonmanifest")

            val manifest = document.documentElement
            val packageName = manifest.getAttribute("package")
            val uniquePrefix = "$packageName.morphe"

            PERMISSION_DECLARATION_TAGS.forEach { tag ->
                val nodes = document.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as Element
                    val oldName = node.getAttribute("android:name")
                    if (oldName.isBlank() || oldName.startsWith("$packageName.")) continue

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

/**
 * Amazon's sign-in library broadcasts account and property changes with a receiver permission
 * (com.amazon.dcp.sso.permission.account.changed and ...property.changed), and the app's own SSOAccountListener
 * listens for them. After the manifest rename the app only holds the renamed permissions, so those names in the
 * code are rewritten to match; otherwise the broadcasts reach nobody and the background cookie sync and the
 * sign-out cleanup never run. Every exact string constant that names a renamed permission is rewritten.
 */
@Suppress("unused")
val amazonFixDuplicatePermissionsPatch = bytecodePatch(
    name = "Fix Amazon manifest conflicts",
    description = "Updates shared Amazon permissions so other Amazon apps can coexist.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AMAZON)
    dependsOn(menuPatch, renameManifestPermissions)

    execute {
        if (permissionRenames.isEmpty()) return@execute
        replaceStrings { string -> permissionRenames[string] }
    }
}
