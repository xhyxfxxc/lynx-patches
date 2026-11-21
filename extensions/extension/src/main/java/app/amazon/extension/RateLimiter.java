/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

import java.util.ArrayDeque;

/** At most [max] calls in any [windowMillis]. Free of Android APIs. */
final class RateLimiter {
    private final int max;
    private final long windowMillis;
    private final ArrayDeque<Long> calls = new ArrayDeque<>();

    RateLimiter(int max, long windowMillis) {
        this.max = max;
        this.windowMillis = windowMillis;
    }

    /** True and counted when a call is allowed at [now]; false, and not counted, when the limit is reached. */
    synchronized boolean tryAcquire(long now) {
        while (!calls.isEmpty() && now - calls.peekFirst() >= windowMillis) calls.pollFirst();
        if (calls.size() >= max) return false;
        calls.addLast(now);
        return true;
    }
}
