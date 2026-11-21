/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

/**
 * What to do with CamelCamelCamel's answer, apart from fetching it: what the answer says and how long it may be
 * kept. The payload the card gets is built from it on every delivery (PricePayload), because the age of the data
 * and the verdict depend on the moment of delivery and on the price the page shows then. Free of Android APIs.
 */
final class PriceFetch {
    private PriceFetch() {
    }

    /** A good answer, or a product with no history, is kept this long. */
    static final long KEEP_MILLIS = 30L * 60L * 1000L;
    /** A failed or unreadable answer is only kept briefly, so a block is not hit again on every product page. */
    static final long RETRY_MILLIS = 60L * 1000L;

    static final class Result {
        /** PricePayload.OK, PricePayload.NONE or PricePayload.CHART. */
        final String status;
        /** The parsed table; not null exactly when the status is OK. */
        final CccParser.Stats stats;
        final long keepMillis;

        Result(String status, CccParser.Stats stats, long keepMillis) {
            this.status = status;
            this.stats = stats;
            this.keepMillis = keepMillis;
        }
    }

    /**
     * 200 with a readable table gives the numbers. 404 and 410 mean CamelCamelCamel has no such product. A challenge
     * page, a 403, 429 or 5xx, an unreadable page or no answer at all (status 0) give just the chart. [fetchedAt] is
     * the moment the page was fetched, which "6 hours ago" on it is counted back from.
     */
    static Result classify(String asin, String code, int status, String body, long fetchedAt) {
        if (status == 404 || status == 410) {
            return new Result(PricePayload.NONE, null, KEEP_MILLIS);
        }
        if (status == 200) {
            CccParser.Stats stats = CccParser.parse(body, fetchedAt);
            if (stats != null) {
                if (!stats.hasData()) return new Result(PricePayload.NONE, null, KEEP_MILLIS);
                if (PriceVerdict.choose(stats) == null) return new Result(PricePayload.CHART, null, KEEP_MILLIS);
                return new Result(PricePayload.OK, stats, KEEP_MILLIS);
            }
        }
        return new Result(PricePayload.CHART, null, RETRY_MILLIS);
    }
}
