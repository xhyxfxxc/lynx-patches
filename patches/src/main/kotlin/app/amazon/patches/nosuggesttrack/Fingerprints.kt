/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21.
 */

package app.amazon.patches.nosuggesttrack

import app.morphe.patcher.Fingerprint

internal val SearchSuggestionsSetEventFingerprint = Fingerprint(
    definingClass = "Lcom/amazon/search/resources/dependency/client/suggestions/SearchSuggestionsV2Request\$Builder;",
    name = "setEvent",
    returnType = "Lcom/amazon/search/resources/dependency/client/suggestions/SearchSuggestionsV2Request\$Builder;",
    parameters = listOf("Lcom/amazon/search/resources/dependency/client/suggestions/Event;"),
)
