/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Where the price history comes from: the marketplaces CamelCamelCamel and Keepa serve, and every URL the card
 * uses. Built here from validated pieces only, never from page text. Free of Android APIs.
 */
final class PriceLinks {
    private PriceLinks() {
    }

    /** CamelCamelCamel tracks fewer marketplaces than Amazon has; its host is {code}.camelcamelcamel.com. */
    private static final Set<String> CCC_CODES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
        "us", "uk", "de", "fr", "jp", "ca", "it", "es", "au")));

    /**
     * Keepa's numeric marketplace ids for its product links. Keepa serves no data for Amazon Australia, so "au" is
     * absent and no Keepa link is offered there.
     */
    private static final Map<String, Integer> KEEPA_IDS;

    static {
        Map<String, Integer> ids = new HashMap<>();
        ids.put("us", 1);  ids.put("uk", 2);  ids.put("de", 3);
        ids.put("fr", 4);  ids.put("jp", 5);  ids.put("ca", 6);
        ids.put("it", 8);  ids.put("es", 9);  ids.put("in", 10);
        ids.put("mx", 11); ids.put("br", 12); ids.put("nl", 14);
        KEEPA_IDS = Collections.unmodifiableMap(ids);
    }

    private static final Pattern ASIN = Pattern.compile("^[A-Z0-9]{10}$");

    /** The chart periods CamelCamelCamel draws (its tp parameter). */
    static final String[] RANGES = {"1m", "3m", "1y", "all"};
    static final String[] RANGE_LABELS = {"30 days", "90 days", "1 year", "All"};
    static final String DEFAULT_RANGE = "3m";

    static boolean validAsin(String asin) {
        return asin != null && ASIN.matcher(asin).matches();
    }

    static boolean cccSupports(String code) {
        return code != null && CCC_CODES.contains(code);
    }

    static boolean keepaSupports(String code) {
        return code != null && KEEPA_IDS.containsKey(code);
    }

    static boolean validRange(String range) {
        return Arrays.asList(RANGES).contains(range);
    }

    /** The stored range if it is one CamelCamelCamel draws, else the default (90 days). */
    static String rangeOrDefault(String range) {
        return validRange(range) ? range : DEFAULT_RANGE;
    }

    /** https://camelcamelcamel.com for the US, https://{code}.camelcamelcamel.com for the others. */
    static String cccHost(String code) {
        return "us".equals(code) ? "camelcamelcamel.com" : code + ".camelcamelcamel.com";
    }

    /** The product page; also where the numbers on the card are read from. */
    static String cccProduct(String code, String asin) {
        if (!cccSupports(code) || !validAsin(asin)) return null;
        return "https://" + cccHost(code) + "/product/" + asin;
    }

    static String keepaProduct(String code, String asin) {
        if (!keepaSupports(code) || !validAsin(asin)) return null;
        return "https://keepa.com/#!product/" + KEEPA_IDS.get(code) + "-" + asin;
    }

    /** The chart image folder for a marketplace, ending in a slash. */
    static String chartBase(String code) {
        return "https://charts.camelcamelcamel.com/" + code + "/";
    }

    /** The chart image URL; the width is clamped to what CamelCamelCamel serves well (400 to 1000 pixels). */
    static String chartUrl(String code, String asin, String range, int width) {
        if (!cccSupports(code) || !validAsin(asin) || !validRange(range)) return null;
        int w = clampWidth(width);
        int h = (int) Math.round(w * 0.55);
        return chartBase(code) + asin + "/amazon-new-used.png?force=1&legend=1&w=" + w + "&h=" + h + "&tp=" + range;
    }

    static int clampWidth(int width) {
        return Math.max(400, Math.min(1000, width));
    }

    /** A host that may answer a request for a product page, after any redirect. */
    static boolean isCccHost(String host) {
        return host != null && (host.equals("camelcamelcamel.com") || host.endsWith(".camelcamelcamel.com"));
    }
}
