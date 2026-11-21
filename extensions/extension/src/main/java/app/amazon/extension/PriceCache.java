/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

import java.util.LinkedHashMap;
import java.util.Map;

/** A small in-memory cache of answers by key, each with its own expiry. Free of Android APIs. */
final class PriceCache<V> {
    private static final class Cached<V> {
        final V value;
        final long expires;

        Cached(V value, long expires) {
            this.value = value;
            this.expires = expires;
        }
    }

    private final int capacity;
    private final Map<String, Cached<V>> entries;

    PriceCache(final int capacity) {
        this.capacity = capacity;
        this.entries = new LinkedHashMap<String, Cached<V>>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Cached<V>> eldest) {
                return size() > capacity;
            }
        };
    }

    /** The answer for [key] if it is still valid at [now], else null. */
    synchronized V get(String key, long now) {
        Cached<V> entry = entries.get(key);
        if (entry == null) return null;
        if (now >= entry.expires) {
            entries.remove(key);
            return null;
        }
        return entry.value;
    }

    synchronized void put(String key, V value, long keepMillis, long now) {
        entries.put(key, new Cached<V>(value, now + keepMillis));
    }

    synchronized int size() {
        return entries.size();
    }
}
