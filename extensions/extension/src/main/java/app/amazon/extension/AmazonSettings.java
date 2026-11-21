/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

import android.content.Context;

import app.loki.extension.LokiPrefs;

/**
 * The switches of the Amazon page on the Lynx Patches screen. They live in Lynx Patches' own preferences file
 * (LokiPrefs), so backup, restore and reset cover them, and are read when a page is set up.
 */
public final class AmazonSettings {
    /** Stored keys, on disk in users' installs: never rename or retype them. */
    public static final String PRICE_CARD = "amazon_price_card";
    public static final String PRICE_AUTO = "amazon_price_auto";
    public static final String PRICE_RANGE = "amazon_price_range";

    private AmazonSettings() {
    }

    /** The price history card on product pages. On unless the user turned it off. */
    public static boolean priceCard(Context context) {
        return LokiPrefs.getBoolean(context, PRICE_CARD, true);
    }

    /** Load the price history without a tap. Off unless the user turned it on. */
    public static boolean priceAuto(Context context) {
        return LokiPrefs.getBoolean(context, PRICE_AUTO, false);
    }

    /** The chart period a card opens with: 1m, 3m, 1y or all. 90 days by default. */
    public static String priceRange(Context context) {
        return PriceLinks.rangeOrDefault(LokiPrefs.getString(context, PRICE_RANGE, PriceLinks.DEFAULT_RANGE));
    }

    public static void setPriceRange(Context context, String range) {
        LokiPrefs.setString(context, PRICE_RANGE, PriceLinks.rangeOrDefault(range));
    }

    /** Index of the stored range in {@link #rangeLabels()}. */
    public static int priceRangeIndex(Context context) {
        String range = priceRange(context);
        for (int i = 0; i < PriceLinks.RANGES.length; i++) {
            if (PriceLinks.RANGES[i].equals(range)) return i;
        }
        return 1;
    }

    public static String[] rangeLabels() {
        return PriceLinks.RANGE_LABELS.clone();
    }

    /** The range stored for choice number [index] of {@link #rangeLabels()}. */
    public static void setPriceRangeIndex(Context context, int index) {
        if (index < 0 || index >= PriceLinks.RANGES.length) return;
        setPriceRange(context, PriceLinks.RANGES[index]);
    }
}
