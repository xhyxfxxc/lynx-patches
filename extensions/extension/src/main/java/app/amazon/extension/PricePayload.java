/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

/**
 * The JSON object the card receives from window.__lynxPriceData. Every string goes through AmazonJson, so nothing
 * read from a third party page can become script. Free of Android APIs.
 */
final class PricePayload {
    private PricePayload() {
    }

    /** The numbers could be read: the tiles, and the verdict when there is a current price. */
    static final String OK = "ok";
    /** CamelCamelCamel has no history for this product. */
    static final String NONE = "none";
    /** Nothing to show beside the chart (a challenge page, an error, a table the card cannot use). */
    static final String CHART = "chart";

    static String status(String asin, String code, String status) {
        return "{\"asin\":" + AmazonJson.string(asin) + ",\"code\":" + AmazonJson.string(code)
            + ",\"status\":" + AmazonJson.string(status) + "}";
    }

    /** The tiles and the verdict for a parsed table, or the right status when the table has nothing to show. */
    static String of(String asin, String code, CccParser.Stats stats) {
        if (stats == null) return status(asin, code, CHART);
        if (!stats.hasData()) return status(asin, code, NONE);
        PriceVerdict.Chosen chosen = PriceVerdict.choose(stats);
        if (chosen == null) return status(asin, code, CHART);

        CccParser.Series s = chosen.series;
        PriceVerdict verdict = PriceVerdict.forSeries(s);
        return "{\"asin\":" + AmazonJson.string(asin) + ",\"code\":" + AmazonJson.string(code)
            + ",\"status\":" + AmazonJson.string(OK)
            + ",\"label\":" + AmazonJson.string(chosen.label)
            + ",\"lowest\":" + price(s.lowest)
            + ",\"average\":" + price(s.average)
            + ",\"highest\":" + price(s.highest)
            + ",\"kind\":" + AmazonJson.string(verdict.kind)
            + ",\"verdict\":" + AmazonJson.string(verdict.text) + "}";
    }

    private static String price(CccParser.Price price) {
        if (price == null) return "null";
        return "{\"p\":" + AmazonJson.string(price.text) + ",\"d\":" + AmazonJson.string(price.date) + "}";
    }
}
