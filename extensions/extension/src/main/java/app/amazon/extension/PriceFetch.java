/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

/**
 * What to do with CamelCamelCamel's answer, apart from fetching it: which payload the card gets and how long that
 * answer may be kept. Free of Android APIs.
 */
final class PriceFetch {
    private PriceFetch() {
    }

    /** A good answer, or a product with no history, is kept this long. */
    static final long KEEP_MILLIS = 30L * 60L * 1000L;
    /** A failed or unreadable answer is only kept briefly, so a block is not hit again on every product page. */
    static final long RETRY_MILLIS = 60L * 1000L;

    static final class Result {
        final String json;
        final long keepMillis;

        Result(String json, long keepMillis) {
            this.json = json;
            this.keepMillis = keepMillis;
        }
    }

    /**
     * 200 with a readable table gives the numbers. 404 and 410 mean CamelCamelCamel has no such product. A challenge
     * page, a 403, 429 or 5xx, an unreadable page or no answer at all (status 0) give just the chart.
     */
    static Result classify(String asin, String code, int status, String body) {
        if (status == 404 || status == 410) {
            return new Result(PricePayload.status(asin, code, PricePayload.NONE), KEEP_MILLIS);
        }
        if (status == 200) {
            CccParser.Stats stats = CccParser.parse(body);
            if (stats != null) {
                return new Result(PricePayload.of(asin, code, stats), KEEP_MILLIS);
            }
        }
        return new Result(PricePayload.status(asin, code, PricePayload.CHART), RETRY_MILLIS);
    }
}
