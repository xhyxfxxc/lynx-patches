/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The checks and helpers around the price history card: what may be asked for, URLs, cache, limits, answers. */
public class PriceSupportTest {

    @Test
    public void onlyTenCapitalLettersAndDigitsAreAnAsin() {
        assertTrue(PriceLinks.validAsin("B076P3QK6K"));
        assertTrue(PriceLinks.validAsin("0123456789"));
        assertFalse(PriceLinks.validAsin("b076p3qk6k"));
        assertFalse(PriceLinks.validAsin("B076P3QK6"));
        assertFalse(PriceLinks.validAsin("B076P3QK6KX"));
        assertFalse(PriceLinks.validAsin("B076P3QK6K\n"));
        assertFalse(PriceLinks.validAsin("B076P3QK6K/../x"));
        assertFalse(PriceLinks.validAsin("B076P3Q K6K"));
        assertFalse(PriceLinks.validAsin("B076P3QK6É"));
        assertFalse(PriceLinks.validAsin(""));
        assertFalse(PriceLinks.validAsin(null));
    }

    @Test
    public void onlyMarketplacesCamelCamelCamelServeAreAccepted() {
        for (String code : new String[]{"us", "uk", "de", "fr", "jp", "ca", "it", "es", "au"}) {
            assertTrue(code, PriceLinks.cccSupports(code));
        }
        assertFalse(PriceLinks.cccSupports("in"));
        assertFalse(PriceLinks.cccSupports("br"));
        assertFalse(PriceLinks.cccSupports("AU"));
        assertFalse(PriceLinks.cccSupports("au."));
        assertFalse(PriceLinks.cccSupports("au/../"));
        assertFalse(PriceLinks.cccSupports(""));
        assertFalse(PriceLinks.cccSupports(null));
    }

    @Test
    public void keepaHasNoAustralianData() {
        assertTrue(PriceLinks.keepaSupports("us"));
        assertTrue(PriceLinks.keepaSupports("de"));
        assertFalse(PriceLinks.keepaSupports("au"));
        assertNull(PriceLinks.keepaProduct("au", "B076P3QK6K"));
        assertEquals("https://keepa.com/#!product/1-B076P3QK6K", PriceLinks.keepaProduct("us", "B076P3QK6K"));
        assertEquals("https://keepa.com/#!product/3-B076P3QK6K", PriceLinks.keepaProduct("de", "B076P3QK6K"));
        assertNull(PriceLinks.keepaProduct("us", "bad"));
    }

    @Test
    public void productLinksUseTheMarketplaceHost() {
        assertEquals("https://au.camelcamelcamel.com/product/B076P3QK6K", PriceLinks.cccProduct("au", "B076P3QK6K"));
        assertEquals("https://uk.camelcamelcamel.com/product/B076P3QK6K", PriceLinks.cccProduct("uk", "B076P3QK6K"));
        assertEquals("https://camelcamelcamel.com/product/B076P3QK6K", PriceLinks.cccProduct("us", "B076P3QK6K"));
        assertNull(PriceLinks.cccProduct("in", "B076P3QK6K"));
        assertNull(PriceLinks.cccProduct("au", "nope"));
    }

    @Test
    public void chartLinksClampTheWidthAndKnowTheRanges() {
        assertEquals("https://charts.camelcamelcamel.com/au/B076P3QK6K/amazon-new-used.png"
                + "?force=1&legend=0&w=800&h=480&tp=3m",
            PriceLinks.chartUrl("au", "B076P3QK6K", "3m", 800));
        assertTrue(PriceLinks.chartUrl("au", "B076P3QK6K", "1y", 100).contains("&w=300&h=180&tp=1y"));
        assertTrue(PriceLinks.chartUrl("au", "B076P3QK6K", "all", 5000).contains("&w=800&h=480&tp=all"));
        assertTrue(PriceLinks.chartUrl("us", "B076P3QK6K", "1m", 1000).contains("/us/B076P3QK6K/"));
        assertNull(PriceLinks.chartUrl("au", "B076P3QK6K", "3y", 800));
        assertNull(PriceLinks.chartUrl("au", "B076P3QK6K", "5y", 800));
        assertNull(PriceLinks.chartUrl("au", "B076P3QK6K", "3m&x=1", 800));
        assertNull(PriceLinks.chartUrl("in", "B076P3QK6K", "3m", 800));
        assertTrue(PriceLinks.chartUrl("au", "B076P3QK6K", "1m", 370).contains("&w=370&h=222&tp=1m"));
        // The built-in table of the image is never asked for: it cannot be read on a phone.
        assertFalse(PriceLinks.chartUrl("au", "B076P3QK6K", "3m", 370).contains("legend=1"));
        assertTrue(PriceLinks.chartUrl("au", "B076P3QK6K", "3m", 370).contains("legend=0"));
        assertEquals(300, PriceLinks.clampWidth(-5));
        assertEquals(300, PriceLinks.clampWidth(299));
        assertEquals(300, PriceLinks.clampWidth(300));
        assertEquals(800, PriceLinks.clampWidth(801));
        assertEquals(800, PriceLinks.clampWidth(1001));
        assertEquals(740, PriceLinks.clampWidth(740));
    }

    @Test
    public void theStoredRangeFallsBackToNinetyDays() {
        assertEquals("3m", PriceLinks.rangeOrDefault(null));
        assertEquals("3m", PriceLinks.rangeOrDefault("5y"));
        assertEquals("3m", PriceLinks.rangeOrDefault(""));
        assertEquals("1m", PriceLinks.rangeOrDefault("1m"));
        assertEquals("all", PriceLinks.rangeOrDefault("all"));
        assertEquals(PriceLinks.RANGES.length, PriceLinks.RANGE_LABELS.length);
        assertEquals("90 days", PriceLinks.RANGE_LABELS[1]);
    }

    @Test
    public void redirectsMayOnlyStayOnCamelCamelCamel() {
        assertTrue(PriceLinks.isCccHost("camelcamelcamel.com"));
        assertTrue(PriceLinks.isCccHost("au.camelcamelcamel.com"));
        assertFalse(PriceLinks.isCccHost("camelcamelcamel.com.evil.example"));
        assertFalse(PriceLinks.isCccHost("evilcamelcamelcamel.com"));
        assertFalse(PriceLinks.isCccHost(null));
    }

    @Test
    public void theCacheKeepsAnswersUntilTheyExpire() {
        PriceCache<String> cache = new PriceCache<>(2);
        cache.put("au:A", "one", 1000, 0);
        assertEquals("one", cache.get("au:A", 999));
        assertNull(cache.get("au:A", 1000));
        assertEquals(0, cache.size());
        assertNull(cache.get("au:missing", 0));
    }

    @Test
    public void theCacheDropsTheOldestWhenFull() {
        PriceCache<String> cache = new PriceCache<>(2);
        cache.put("a", "1", 1000, 0);
        cache.put("b", "2", 1000, 0);
        cache.put("c", "3", 1000, 0);
        assertNull(cache.get("a", 1));
        assertEquals("2", cache.get("b", 1));
        assertEquals("3", cache.get("c", 1));
        assertEquals(2, cache.size());
    }

    @Test
    public void goodAnswersAreKeptThirtyMinutesAndFailuresOneMinute() {
        assertEquals(30L * 60L * 1000L, PriceFetch.KEEP_MILLIS);
        assertEquals(60L * 1000L, PriceFetch.RETRY_MILLIS);
    }

    @Test
    public void theRequestUsesThePlatformsOwnHttpAgent() {
        String dalvik = "Dalvik/2.1.0 (Linux; U; Android 16; SM-S938B Build/BP2A)";
        assertEquals(dalvik, PriceBridge.userAgent(dalvik, "16"));
        assertEquals(dalvik, PriceBridge.userAgent("  " + dalvik + " ", "16"));
        assertEquals("Dalvik/2.1.0 (Linux; U; Android 16)", PriceBridge.userAgent(null, "16"));
        assertEquals("Dalvik/2.1.0 (Linux; U; Android 16)", PriceBridge.userAgent("", "16"));
        assertEquals("Dalvik/2.1.0 (Linux; U; Android 16)", PriceBridge.userAgent("   ", "16"));
        assertFalse(PriceBridge.userAgent(null, "16").contains("Mozilla"));
    }

    @Test
    public void aCloudflareChallengePageIsRecognised() {
        assertTrue(PriceBridge.looksLikeChallenge("<html><head><title>Just a moment...</title></head></html>"));
        assertTrue(PriceBridge.looksLikeChallenge("<script src=\"/cdn-cgi/challenge-platform/h/b/orchestrate\"></script>"));
        assertFalse(PriceBridge.looksLikeChallenge(TABLE));
        assertFalse(PriceBridge.looksLikeChallenge(""));
        assertFalse(PriceBridge.looksLikeChallenge(null));
    }

    @Test
    public void theRateLimiterAllowsAFixedNumberPerWindow() {
        RateLimiter limiter = new RateLimiter(3, 1000);
        assertTrue(limiter.tryAcquire(0));
        assertTrue(limiter.tryAcquire(100));
        assertTrue(limiter.tryAcquire(200));
        assertFalse(limiter.tryAcquire(300));
        assertFalse(limiter.tryAcquire(999));
        assertTrue(limiter.tryAcquire(1000));
        assertFalse(limiter.tryAcquire(1001));
        assertTrue(limiter.tryAcquire(1100));
    }

    private static final String TABLE = "<table><tr><th>Lowest Ever</th></tr>"
        + "<tr data-field=\"amazon\"><td>Amazon</td><td>$100.00 <small>(Nov 20, 2026)</small></td><td>$250.00</td>"
        + "<td>$100.00</td><td>$180.00</td></tr></table>";

    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void aReadablePageGivesTheNumbersAndIsKeptLong() {
        PriceFetch.Result result = PriceFetch.classify("B076P3QK6K", "au", 200, TABLE, NOW);
        assertEquals(PricePayload.OK, result.status);
        assertNotNull(result.stats);
        assertEquals(PriceFetch.KEEP_MILLIS, result.keepMillis);
        String json = PricePayload.of("B076P3QK6K", "au", result, Double.NaN, NOW);
        assertTrue(json, json.contains("\"status\":\"ok\""));
        assertTrue(json, json.contains("\"verdict\":\"Lowest price ever\""));
        assertTrue(json, json.contains("\"updated\":null"));
        assertTrue(json, json.contains("\"stale\":false"));
    }

    @Test
    public void aMissingProductMeansNoHistory() {
        for (int status : new int[]{404, 410}) {
            PriceFetch.Result result = PriceFetch.classify("B076P3QK6K", "au", status, null, NOW);
            assertEquals(PricePayload.NONE, result.status);
            assertNull(result.stats);
            assertEquals(PriceFetch.KEEP_MILLIS, result.keepMillis);
            assertTrue(PricePayload.of("B076P3QK6K", "au", result, Double.NaN, NOW).contains("\"status\":\"none\""));
        }
    }

    @Test
    public void aTableWithNothingInItMeansNoHistoryAndOneWithNoUsableRowIsJustTheChart() {
        String dashes = "<table><tr><th>Lowest Ever</th></tr>"
            + "<tr data-field=\"amazon\"><td>Amazon</td><td>-</td><td>-</td><td>-</td><td>-</td></tr></table>";
        PriceFetch.Result none = PriceFetch.classify("B076P3QK6K", "au", 200, dashes, NOW);
        assertEquals(PricePayload.NONE, none.status);
        assertEquals(PriceFetch.KEEP_MILLIS, none.keepMillis);

        String onlyUsed = "<table><tr><th>Lowest Ever</th></tr>"
            + "<tr data-field=\"used\"><td>Used</td><td>$5.00</td><td>$9.00</td><td>$6.00</td><td>$7.00</td></tr></table>";
        PriceFetch.Result chart = PriceFetch.classify("B076P3QK6K", "au", 200, onlyUsed, NOW);
        assertEquals(PricePayload.CHART, chart.status);
        assertNull(chart.stats);
        assertEquals(PriceFetch.KEEP_MILLIS, chart.keepMillis);
        assertTrue(PricePayload.of("B076P3QK6K", "au", chart, Double.NaN, NOW).contains("\"status\":\"chart\""));
    }

    @Test
    public void challengesAndErrorsShowTheChartWithoutNumbers() {
        String challenge = "<html><title>Just a moment...</title><body>cf-chl</body></html>";
        int[] statuses = {403, 429, 500, 502, 503, 0};
        for (int status : statuses) {
            PriceFetch.Result result = PriceFetch.classify("B076P3QK6K", "au", status, challenge, NOW);
            assertEquals(String.valueOf(status), PricePayload.CHART, result.status);
            assertNull(result.stats);
            assertEquals(PriceFetch.RETRY_MILLIS, result.keepMillis);
        }
        // A 200 that is a challenge page, not the table.
        PriceFetch.Result page = PriceFetch.classify("B076P3QK6K", "au", 200, challenge, NOW);
        assertEquals(PricePayload.CHART, page.status);
        assertEquals(PriceFetch.RETRY_MILLIS, page.keepMillis);
        // A 403 is never read as a table, even if the body happens to hold one.
        assertEquals(PricePayload.CHART, PriceFetch.classify("B076P3QK6K", "au", 403, TABLE, NOW).status);
    }

    @Test
    public void theCacheHoldsAnAnswerWhoseAgeIsCountedAtEachDelivery() {
        String page = TABLE + "<table><tr><td>Last update scan</td><td>6 hours ago</td></tr></table>";
        PriceCache<PriceFetch.Result> cache = new PriceCache<>(4);
        cache.put("au:B076P3QK6K", PriceFetch.classify("B076P3QK6K", "au", 200, page, NOW), PriceFetch.KEEP_MILLIS, NOW);
        PriceFetch.Result later = cache.get("au:B076P3QK6K", NOW + 20 * 60_000L);
        assertNotNull(later);
        String first = PricePayload.of("B076P3QK6K", "au", later, Double.NaN, NOW);
        String second = PricePayload.of("B076P3QK6K", "au", later, Double.NaN, NOW + 20 * 60_000L);
        assertTrue(first, first.contains("\"updated\":\"6 hours ago\""));
        assertTrue(second, second.contains("\"updated\":\"6 hours ago\""));
        String muchLater = PricePayload.of("B076P3QK6K", "au", later, Double.NaN, NOW + 3 * 60 * 60_000L);
        assertTrue(muchLater, muchLater.contains("\"updated\":\"9 hours ago\""));
    }
}
