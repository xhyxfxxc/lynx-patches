/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

/**
 * The one-line verdict on the card ("Lowest price ever", "Good price: 12% below average", ...) and which price
 * series it is about. Free of Android APIs.
 */
final class PriceVerdict {
    static final String NONE = "none";
    static final String LOW = "low";
    static final String NEAR = "near";
    static final String GOOD = "good";
    static final String HIGH = "high";
    static final String TYPICAL = "typical";
    /** CamelCamelCamel's data is too old to judge a price by. */
    static final String STALE = "stale";

    /** Data CamelCamelCamel last scanned more than this long ago gets no verdict. */
    static final long STALE_AFTER_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    private static final long HOUR_MILLIS = 60L * 60L * 1000L;
    private static final long DAY_MILLIS = 24L * HOUR_MILLIS;

    /** Prices are cents, so a boundary such as 100 * 1.1 must not be decided by floating point rounding. */
    private static final double EPSILON = 1e-9;

    /** One of the constants above; NONE when there is no current price to judge. */
    final String kind;
    final String text;

    private PriceVerdict(String kind, String text) {
        this.kind = kind;
        this.text = text;
    }

    /**
     * Judges the current price against the lowest ever and the average. "Lowest price ever" means equal to the lowest
     * within rounding (0.2 percent); up to 5 percent above it is "Near the lowest price ever". A value that is NaN or
     * not above zero is treated as missing: without a current price there is no verdict, and a missing lowest or
     * average only skips its own rule.
     */
    static PriceVerdict of(double current, double lowest, double average) {
        if (!usable(current)) return new PriceVerdict(NONE, "");
        if (usable(lowest)) {
            if (current <= lowest * 1.002 + EPSILON) return new PriceVerdict(LOW, "Lowest price ever");
            if (current <= lowest * 1.05 + EPSILON) return new PriceVerdict(NEAR, "Near the lowest price ever");
        }
        if (usable(average)) {
            if (current <= average * 0.9 + EPSILON) {
                return new PriceVerdict(GOOD, "Good price: " + percent(1 - current / average) + "% below average");
            }
            if (current >= average * 1.1 - EPSILON) {
                return new PriceVerdict(HIGH, "Higher than usual: " + percent(current / average - 1) + "% above average");
            }
        }
        return new PriceVerdict(TYPICAL, "Typical price");
    }

    private static boolean usable(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0;
    }

    private static int percent(double fraction) {
        return (int) Math.round(fraction * 100);
    }

    /** The series the tiles and the verdict are about, with the name shown for it. */
    static final class Chosen {
        final CccParser.Series series;
        final String label;

        Chosen(CccParser.Series series, String label) {
            this.series = series;
            this.label = label;
        }
    }

    /**
     * Amazon's own price when it has a current one, else the 3rd party new price when that has one. With no current
     * price at all, the first of the two that has any history, so the tiles still show; null when neither does.
     */
    static Chosen choose(CccParser.Stats stats) {
        if (stats == null) return null;
        CccParser.Series amazon = stats.amazon;
        CccParser.Series thirdNew = stats.thirdPartyNew;
        if (hasCurrent(amazon)) return new Chosen(amazon, "Amazon");
        if (hasCurrent(thirdNew)) return new Chosen(thirdNew, "3rd party new");
        if (amazon != null && amazon.hasData()) return new Chosen(amazon, "Amazon");
        if (thirdNew != null && thirdNew.hasData()) return new Chosen(thirdNew, "3rd party new");
        return null;
    }

    private static boolean hasCurrent(CccParser.Series series) {
        return series != null && series.current != null && series.current.hasValue();
    }

    /**
     * The verdict for a chosen series. The price judged is [pagePrice] (what the Amazon page shows now) when it is
     * usable, else the series' own current price from CamelCamelCamel. When CamelCamelCamel last scanned the product
     * more than a week ago ([scannedAt] is epoch millis, -1 when unknown) there is no verdict, only the reason.
     */
    static PriceVerdict forSeries(CccParser.Series series, double pagePrice, long scannedAt, long now) {
        if (isStale(scannedAt, now)) {
            return new PriceVerdict(STALE, "No verdict: CamelCamelCamel last checked " + agePhrase(scannedAt, now));
        }
        double current = usable(pagePrice) ? pagePrice : value(series.current);
        return of(current, value(series.lowest), value(series.average));
    }

    /** True when the scan is known and older than a week (exactly a week is still fresh). Unknown is not stale. */
    static boolean isStale(long scannedAt, long now) {
        return scannedAt >= 0 && now - scannedAt > STALE_AFTER_MILLIS;
    }

    /**
     * How long ago the scan was, in words: "less than an hour ago", "6 hours ago" (up to 23), "1 day ago",
     * "5 days ago", "2 months ago", "3 years ago".
     */
    static String agePhrase(long scannedAt, long now) {
        long age = Math.max(0, now - scannedAt);
        if (age < HOUR_MILLIS) return "less than an hour ago";
        if (age < DAY_MILLIS) return count(age / HOUR_MILLIS, "hour") + " ago";
        long days = age / DAY_MILLIS;
        if (days < 60) return count(days, "day") + " ago";
        if (days < 730) return count(days / 30, "month") + " ago";
        return count(days / 365, "year") + " ago";
    }

    private static String count(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }

    /**
     * The number in the price the Amazon page shows ("$269.00"), or NaN when there is none worth trusting: nothing,
     * more than 40 characters, no readable number, zero or below, or ten million and up.
     */
    static double pagePrice(String text) {
        if (text == null || text.isEmpty() || text.length() > 40) return Double.NaN;
        double value = CccParser.amount(text);
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0 || value >= 10_000_000) return Double.NaN;
        return value;
    }

    private static double value(CccParser.Price price) {
        return price == null ? Double.NaN : price.value;
    }
}
