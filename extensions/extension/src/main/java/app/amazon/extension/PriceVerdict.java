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

    /** The verdict for a chosen series. */
    static PriceVerdict forSeries(CccParser.Series series) {
        return of(value(series.current), value(series.lowest), value(series.average));
    }

    private static double value(CccParser.Price price) {
        return price == null ? Double.NaN : price.value;
    }
}
