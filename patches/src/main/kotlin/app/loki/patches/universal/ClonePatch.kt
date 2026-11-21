/*
 * Copyright (C) 2026 Lokesh Sparrow
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.loki.patches.universal

import app.loki.patches.universal.OriginalApp
import app.loki.patches.universal.recordOriginalAppPatch
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.patch.stringOption
import org.w3c.dom.Element

/** Manifest attributes whose value is a component class name, which the manifest may write relative to the package. */
private val CLASS_ATTRIBUTES = listOf("name", "targetActivity", "backupAgent", "appComponentFactory", "zygotePreloadName")

@Suppress("unused")
val clonePatch = resourcePatch(
    name = "Change package name",
    description = "Changes the package name by adding a suffix (\".loki\" by default), so the patched app installs " +
        "next to the original app instead of replacing it. Some apps check their own package name and need a small " +
        "extra patch to run under the new one.",
    default = false,
) {
    dependsOn(recordOriginalAppPatch)
    dependsOn(menuPatch)

    val suffix by stringOption(
        key = "suffix",
        default = ".loki",
        title = "Package suffix",
        description = "Appended to the original package name. Must start with a dot.",
        required = true,
    ) { it != null && Regex("""\.[A-Za-z][A-Za-z0-9_]*""").matches(it) }

    execute {
        renamePackage(suffix!!)
    }
}

/**
 * Renames the app's package to its original name plus [suffix], with everything that would clash with the original
 * app (provider authorities, the app's own permissions). Returns the provider authorities it renamed, old to new, so
 * an app-specific patch can rewrite the same names where the app's code spells them out. Does nothing, and returns
 * nothing, if the package was already renamed.
 *
 * An authority may be written as a string resource (`android:authorities="@string/name"`); its value is renamed in
 * the resource itself, so every other use of that resource (code that builds content URIs from it) stays in step.
 */
internal fun app.morphe.patcher.patch.ResourcePatchContext.renamePackage(suffix: String): Map<String, String> {
    val old = OriginalApp.packageName
    val new = old + suffix
    val authorities = mutableMapOf<String, String>()
    val resourceAuthorities = mutableSetOf<String>()
    document("AndroidManifest.xml").use { document ->
        val root = document.documentElement
        if (old.isEmpty() || root.getAttribute("package") != old) return@use // nothing safe to rename
        val all = document.getElementsByTagName("*")
        val elements = (0 until all.length).map { all.item(it) as Element }

        // Names written as ".Foo" resolve against the package, so spell them out before the package changes.
        for (element in elements) for (attribute in CLASS_ATTRIBUTES) {
            val value = element.getAttribute("android:$attribute")
            if (value.startsWith(".")) element.setAttribute("android:$attribute", old + value)
        }

        // What would clash with the original app, which stays installed: content provider authorities and
        // the app's own permissions.
        for (element in elements) when (element.tagName) {
            "provider" -> element.setAttribute(
                "android:authorities",
                element.getAttribute("android:authorities").split(";").joinToString(";") { authority ->
                    if (authority.startsWith("@string/")) {
                        resourceAuthorities += authority.removePrefix("@string/")
                        authority
                    } else {
                        authority.renameIn(old, new).also { if (it != authority) authorities[authority] = it }
                    }
                },
            )
            "permission", "uses-permission", "uses-permission-sdk-23" -> element.setAttribute(
                "android:name", element.getAttribute("android:name").renameIn(old, new),
            )
        }
        for (element in elements) {
            val guard = element.getAttribute("android:permission")
            if (guard.isNotEmpty()) element.setAttribute("android:permission", guard.renameIn(old, new))
        }

        root.setAttribute("package", new)
        (document.getElementsByTagName("application").item(0) as Element).appendChild(
            document.createElement("meta-data").also {
                it.setAttribute("android:name", "app.loki.menu.clone")
                it.setAttribute("android:value", "true")
            },
        )
    }
    if (resourceAuthorities.isNotEmpty()) renameStringAuthorities(resourceAuthorities, old, new, authorities)
    return authorities
}

/**
 * Renames the value of each string resource in [names] that starts with the original package. Such a resource is
 * a provider authority, so left alone it would clash with the original app exactly like a literal authority does.
 */
private fun app.morphe.patcher.patch.ResourcePatchContext.renameStringAuthorities(
    names: Set<String>,
    old: String,
    new: String,
    authorities: MutableMap<String, String>,
) {
    val remaining = names.toMutableSet()
    val valueFiles = get("res/values").listFiles { file -> file.isFile && file.extension == "xml" }
        ?.sortedBy { it.name } ?: emptyList()
    for (file in valueFiles) {
        if (remaining.isEmpty()) break
        document("res/values/${file.name}").use { document ->
            val strings = document.getElementsByTagName("string")
            for (index in 0 until strings.length) {
                val string = strings.item(index) as? Element ?: continue
                val name = string.getAttribute("name")
                if (name !in remaining) continue
                remaining -= name
                val value = string.textContent
                val renamed = value.renameIn(old, new)
                if (renamed != value) {
                    string.textContent = renamed
                    authorities[value] = renamed
                }
            }
        }
    }
    if (remaining.isNotEmpty()) {
        throw PatchException(
            "Provider authority string resource not found in res/values: ${remaining.sorted().joinToString()}",
        )
    }
}

/** Renames [old] where it is the whole value or a dotted prefix of it. */
private fun String.renameIn(old: String, new: String) =
    if (this == old || startsWith("$old.")) new + removePrefix(old) else this
