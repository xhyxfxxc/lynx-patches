/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

import java.util.LinkedHashMap;
import java.util.Map;

/** A small in-memory cache of payloads by key, each with its own expiry. Free of Android APIs. */
final class PriceCache {
    private static final class Cached {
        final String json;
        final long expires;

        Cached(String json, long expires) {
            this.json = json;
            this.expires = expires;
        }
    }

    private final int capacity;
    private final Map<String, Cached> entries;

    PriceCache(final int capacity) {
        this.capacity = capacity;
        this.entries = new LinkedHashMap<String, Cached>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
                return size() > capacity;
            }
        };
    }

    /** The payload for [key] if it is still valid at [now], else null. */
    synchronized String get(String key, long now) {
        Cached entry = entries.get(key);
        if (entry == null) return null;
        if (now >= entry.expires) {
            entries.remove(key);
            return null;
        }
        return entry.json;
    }

    synchronized void put(String key, String json, long keepMillis, long now) {
        entries.put(key, new Cached(json, now + keepMillis));
    }

    synchronized int size() {
        return entries.size();
    }
}
