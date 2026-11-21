/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.patches.clone

import app.amazon.patches.shared.Constants.COMPATIBILITY_AMAZON
import app.loki.patches.universal.OriginalApp
import app.loki.patches.universal.recordOriginalAppPatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

/**
 * Amazon's sign-in library finds the other Amazon apps on the phone by the prefix of their provider authorities, so
 * the two sign-in providers keep their prefix and only what follows it changes.
 */
private const val MAP_INFO_PREFIX = "com.amazon.identity.auth.device.MapInfoProvider."
private const val CROSS_APP_PREFIX = "com.amazon.identity.auth.device.CrossAppContextProvider."

/**
 * Companion of "Change package name" for Amazon Shopping, on by default and harmless without it.
 *
 * "Change package name" renames every authority that starts with the app's package, including the ones Amazon keeps
 * in string resources. Two authorities of Amazon's sign-in library do not start with it
 * (`com.amazon.identity.auth.device.MapInfoProvider.mShop.lite.m2b` and `...CrossAppContextProvider.<package>`), so
 * the copy would still clash with the real Amazon app and Android would refuse to install it. They are renamed here,
 * keeping the library's prefixes. Amazon's code never spells either full authority out (it matches the prefix, or
 * builds the name from its own package), so nothing else has to change.
 *
 * This runs in the finalize step so it sees the package name whichever order the patches ran in.
 */
@Suppress("unused")
val amazonCloneSupportPatch = resourcePatch(
    name = "Change package name support (Amazon)",
    description = "Lets the copy made by \"Change package name\" install next to the real Amazon app: the sign-in " +
        "provider authorities that do not contain the package name are renamed as well. Does nothing when " +
        "\"Change package name\" is off.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_AMAZON)
    dependsOn(recordOriginalAppPatch)

    finalize {
        document("AndroidManifest.xml").use { document ->
            val original = OriginalApp.packageName
            val renamed = document.documentElement.getAttribute("package")
            if (original.isEmpty() || !renamed.startsWith("$original.")) return@use // not renamed: nothing to do
            val suffix = renamed.removePrefix(original)

            val providers = document.getElementsByTagName("provider")
            for (index in 0 until providers.length) {
                val provider = providers.item(index) as Element
                val authorities = provider.getAttribute("android:authorities")
                if (authorities.isEmpty()) continue
                provider.setAttribute(
                    "android:authorities",
                    authorities.split(";").joinToString(";") { authority ->
                        when {
                            authority.startsWith(MAP_INFO_PREFIX) && !authority.endsWith(suffix) -> authority + suffix
                            authority == CROSS_APP_PREFIX + original -> CROSS_APP_PREFIX + renamed
                            authority.startsWith(CROSS_APP_PREFIX) && !authority.endsWith(suffix) -> authority + suffix
                            else -> authority
                        }
                    },
                )
            }
        }
    }
}
