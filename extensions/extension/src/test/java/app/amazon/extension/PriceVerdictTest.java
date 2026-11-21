/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The verdict line and which price series the card is about. */
public class PriceVerdictTest {

    private static final double NA = Double.NaN;
    private static final long NOW = 1_800_000_000_000L;
    private static final long MINUTE = 60L * 1000L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    private static void verdict(String kind, String text, double current, double lowest, double average) {
        PriceVerdict v = PriceVerdict.of(current, lowest, average);
        assertEquals(kind, v.kind);
        assertEquals(text, v.text);
    }

    @Test
    public void lowestEverOnlyWhenEqualWithinRounding() {
        verdict(PriceVerdict.LOW, "Lowest price ever", 100.0, 100.0, 200.0);
        verdict(PriceVerdict.LOW, "Lowest price ever", 90.0, 100.0, 200.0);
        // 0.2 percent above the lowest still counts as the lowest; 100 * 1.002 is not exactly 100.2 in floating
        // point, so the boundary must not be decided by rounding.
        verdict(PriceVerdict.LOW, "Lowest price ever", 100.2, 100.0, 200.0);
        verdict(PriceVerdict.NEAR, "Near the lowest price ever", 100.21, 100.0, 200.0);
        verdict(PriceVerdict.NEAR, "Near the lowest price ever", 101.0, 100.0, 200.0);
    }

    @Test
    public void twoDollarsAboveTheLowestIsNearNotLowest() {
        // The case seen on a phone: $266.08 now, lowest ever $264.08 (0.76 percent above).
        verdict(PriceVerdict.NEAR, "Near the lowest price ever", 266.08, 264.08, 319.34);
        verdict(PriceVerdict.LOW, "Lowest price ever", 264.08, 264.08, 319.34);
        verdict(PriceVerdict.LOW, "Lowest price ever", 264.5, 264.08, 319.34);
    }

    @Test
    public void nearTheLowestWithinFivePercent() {
        verdict(PriceVerdict.NEAR, "Near the lowest price ever", 103.0, 100.0, 200.0);
        verdict(PriceVerdict.NEAR, "Near the lowest price ever", 105.0, 100.0, 200.0);
        // Just above 5 percent falls through to the average rules: 105.01 vs average 200 is 47% below.
        verdict(PriceVerdict.GOOD, "Good price: 47% below average", 105.01, 100.0, 200.0);
    }

    @Test
    public void goodPriceTenPercentBelowAverage() {
        verdict(PriceVerdict.GOOD, "Good price: 10% below average", 90.0, 50.0, 100.0);
        verdict(PriceVerdict.GOOD, "Good price: 25% below average", 75.0, 50.0, 100.0);
        verdict(PriceVerdict.GOOD, "Good price: 20% below average", 80.0, NA, 100.0);
    }

    @Test
    public void higherThanUsualTenPercentAboveAverage() {
        verdict(PriceVerdict.HIGH, "Higher than usual: 10% above average", 110.0, 50.0, 100.0);
        verdict(PriceVerdict.HIGH, "Higher than usual: 50% above average", 150.0, 50.0, 100.0);
        verdict(PriceVerdict.HIGH, "Higher than usual: 21% above average", 121.0, NA, 100.0);
    }

    @Test
    public void typicalPriceBetweenTheBands() {
        verdict(PriceVerdict.TYPICAL, "Typical price", 100.0, 50.0, 100.0);
        verdict(PriceVerdict.TYPICAL, "Typical price", 95.0, 50.0, 100.0);
        verdict(PriceVerdict.TYPICAL, "Typical price", 109.0, 50.0, 100.0);
        // 90 is exactly 10 percent below an average of 100, which is good; 90.5 is not.
        verdict(PriceVerdict.TYPICAL, "Typical price", 90.5, 50.0, 100.0);
    }

    @Test
    public void missingNumbersOnlySkipTheirOwnRule() {
        verdict(PriceVerdict.NONE, "", NA, 50.0, 100.0);
        verdict(PriceVerdict.NONE, "", 0.0, 50.0, 100.0);
        verdict(PriceVerdict.NONE, "", -3.0, 50.0, 100.0);
        verdict(PriceVerdict.TYPICAL, "Typical price", 100.0, NA, NA);
        verdict(PriceVerdict.LOW, "Lowest price ever", 100.0, 100.0, NA);
        verdict(PriceVerdict.TYPICAL, "Typical price", 100.0, 0.0, 100.0);
    }

    @Test
    public void theLowestRuleComesBeforeTheAverageRule() {
        // Current is the lowest ever and also far below average: the lowest wins.
        verdict(PriceVerdict.LOW, "Lowest price ever", 50.0, 50.0, 100.0);
    }

    private static CccParser.Price price(double value) {
        return new CccParser.Price("$" + value, value, null);
    }

    private static CccParser.Series series(String key, Double low, Double high, Double current, Double average) {
        return new CccParser.Series(key,
            low == null ? null : price(low), high == null ? null : price(high),
            current == null ? null : price(current), average == null ? null : price(average));
    }

    private static CccParser.Stats stats(CccParser.Series amazon, CccParser.Series thirdNew, CccParser.Series used) {
        return new CccParser.Stats(amazon, thirdNew, used, -1);
    }

    /** The verdict for a series with no page price and no known scan time. */
    private static PriceVerdict forSeries(CccParser.Series series) {
        return PriceVerdict.forSeries(series, NA, -1, NOW);
    }

    @Test
    public void usesAmazonWhenItHasACurrentPrice() {
        CccParser.Stats stats = stats(
            series("amazon", 100.0, 250.0, 100.0, 180.0),
            series("new", 90.0, 300.0, 90.0, 190.0), null);
        PriceVerdict.Chosen chosen = PriceVerdict.choose(stats);
        assertEquals("Amazon", chosen.label);
        assertEquals("amazon", chosen.series.key);
        assertEquals(PriceVerdict.LOW, forSeries(chosen.series).kind);
    }

    @Test
    public void fallsBackToThirdPartyNewWhenAmazonHasNoCurrentPrice() {
        CccParser.Stats stats = stats(
            series("amazon", 100.0, 250.0, null, 180.0),
            series("new", 90.0, 300.0, 94.0, 190.0), null);
        PriceVerdict.Chosen chosen = PriceVerdict.choose(stats);
        assertEquals("3rd party new", chosen.label);
        assertEquals("new", chosen.series.key);
        assertEquals(PriceVerdict.NEAR, forSeries(chosen.series).kind);
    }

    @Test
    public void showsHistoryWithoutAVerdictWhenNothingIsOnSaleNow() {
        CccParser.Stats stats = stats(
            series("amazon", 100.0, 250.0, null, 180.0),
            series("new", 90.0, 300.0, null, 190.0), null);
        PriceVerdict.Chosen chosen = PriceVerdict.choose(stats);
        assertEquals("Amazon", chosen.label);
        assertEquals(PriceVerdict.NONE, forSeries(chosen.series).kind);

        CccParser.Stats onlyThirdParty = stats(
            series("amazon", null, null, null, null),
            series("new", 90.0, 300.0, null, 190.0), null);
        assertEquals("3rd party new", PriceVerdict.choose(onlyThirdParty).label);
    }

    @Test
    public void chooseGivesNullWhenThereIsNothingToShow() {
        assertNull(PriceVerdict.choose(null));
        assertNull(PriceVerdict.choose(stats(null, null, null)));
        assertNull(PriceVerdict.choose(stats(
            series("amazon", null, null, null, null), series("new", null, null, null, null),
            series("used", 5.0, 9.0, 6.0, 7.0))));
    }

    @Test
    public void theVerdictJudgesThePagePriceBeforeCamelCamelCamelsCurrentPrice() {
        // The case seen on a phone: CamelCamelCamel says $266.08 now, lowest ever $264.08, average $319.34.
        CccParser.Series s = series("amazon", 264.08, 479.0, 266.08, 319.34);
        assertEquals(PriceVerdict.LOW, PriceVerdict.forSeries(s, 258.00, NOW - HOUR, NOW).kind);
        assertEquals(PriceVerdict.NEAR, PriceVerdict.forSeries(s, NA, NOW - HOUR, NOW).kind);
        assertEquals(PriceVerdict.NEAR, PriceVerdict.forSeries(s, 269.00, NOW - HOUR, NOW).kind);
        assertEquals(PriceVerdict.NEAR, PriceVerdict.forSeries(s, 0.0, NOW - HOUR, NOW).kind);
        assertEquals(PriceVerdict.HIGH, PriceVerdict.forSeries(s, 400.0, NOW - HOUR, NOW).kind);
        // Nothing on sale according to CamelCamelCamel, but the page has a price.
        CccParser.Series none = series("amazon", 264.08, 479.0, null, 319.34);
        assertEquals(PriceVerdict.NONE, PriceVerdict.forSeries(none, NA, NOW - HOUR, NOW).kind);
        assertEquals(PriceVerdict.NEAR, PriceVerdict.forSeries(none, 269.00, NOW - HOUR, NOW).kind);
    }

    @Test
    public void oldDataGetsNoVerdictAndSaysWhy() {
        CccParser.Series s = series("amazon", 264.08, 479.0, 266.08, 319.34);
        PriceVerdict v = PriceVerdict.forSeries(s, 258.00, NOW - 3 * 365 * DAY, NOW);
        assertEquals(PriceVerdict.STALE, v.kind);
        assertEquals("No verdict: CamelCamelCamel last checked 3 years ago", v.text);
        PriceVerdict week = PriceVerdict.forSeries(s, NA, NOW - 8 * DAY, NOW);
        assertEquals(PriceVerdict.STALE, week.kind);
        assertEquals("No verdict: CamelCamelCamel last checked 8 days ago", week.text);
        // An unknown scan time keeps the normal verdict.
        assertEquals(PriceVerdict.NEAR, PriceVerdict.forSeries(s, NA, -1, NOW).kind);
    }

    @Test
    public void dataIsOldOnlyAfterMoreThanSevenDays() {
        assertEquals(7L * 24L * 60L * 60L * 1000L, PriceVerdict.STALE_AFTER_MILLIS);
        assertFalse(PriceVerdict.isStale(NOW - 7 * DAY, NOW));
        assertTrue(PriceVerdict.isStale(NOW - 7 * DAY - 1, NOW));
        assertFalse(PriceVerdict.isStale(NOW - HOUR, NOW));
        assertFalse(PriceVerdict.isStale(-1, NOW));
        assertFalse(PriceVerdict.isStale(NOW + HOUR, NOW));
    }

    @Test
    public void theAgeIsSaidInTheRightUnit() {
        assertEquals("less than an hour ago", PriceVerdict.agePhrase(NOW, NOW));
        assertEquals("less than an hour ago", PriceVerdict.agePhrase(NOW - 59 * MINUTE, NOW));
        assertEquals("less than an hour ago", PriceVerdict.agePhrase(NOW + 5 * MINUTE, NOW));
        assertEquals("1 hour ago", PriceVerdict.agePhrase(NOW - HOUR, NOW));
        assertEquals("1 hour ago", PriceVerdict.agePhrase(NOW - 119 * MINUTE, NOW));
        assertEquals("2 hours ago", PriceVerdict.agePhrase(NOW - 2 * HOUR, NOW));
        assertEquals("6 hours ago", PriceVerdict.agePhrase(NOW - 6 * HOUR, NOW));
        assertEquals("47 hours ago", PriceVerdict.agePhrase(NOW - 47 * HOUR, NOW));
        assertEquals("2 days ago", PriceVerdict.agePhrase(NOW - 48 * HOUR, NOW));
        assertEquals("5 days ago", PriceVerdict.agePhrase(NOW - 5 * DAY, NOW));
        assertEquals("59 days ago", PriceVerdict.agePhrase(NOW - 59 * DAY, NOW));
        assertEquals("2 months ago", PriceVerdict.agePhrase(NOW - 60 * DAY, NOW));
        assertEquals("24 months ago", PriceVerdict.agePhrase(NOW - 729 * DAY, NOW));
        assertEquals("2 years ago", PriceVerdict.agePhrase(NOW - 730 * DAY, NOW));
        assertEquals("3 years ago", PriceVerdict.agePhrase(NOW - 3 * 365 * DAY, NOW));
    }

    @Test
    public void thePriceOnThePageMustLookLikeAPrice() {
        assertEquals(269.0, PriceVerdict.pagePrice("$269.00"), 0.0001);
        assertEquals(1299.0, PriceVerdict.pagePrice("$1,299.00"), 0.0001);
        assertEquals(12.5, PriceVerdict.pagePrice("12,50 €"), 0.0001);
        assertEquals(9_999_999.0, PriceVerdict.pagePrice("$9999999"), 0.0001);
        assertTrue(Double.isNaN(PriceVerdict.pagePrice(null)));
        assertTrue(Double.isNaN(PriceVerdict.pagePrice("")));
        assertTrue(Double.isNaN(PriceVerdict.pagePrice("abc")));
        assertTrue(Double.isNaN(PriceVerdict.pagePrice("$0.00")));
        assertTrue(Double.isNaN(PriceVerdict.pagePrice("$99999999")));
        StringBuilder longText = new StringBuilder("$");
        for (int i = 0; i < 40; i++) longText.append('1');
        assertEquals(41, longText.length());
        assertTrue(Double.isNaN(PriceVerdict.pagePrice(longText.toString())));
    }

    private static PriceFetch.Result ok(CccParser.Stats stats) {
        return new PriceFetch.Result(PricePayload.OK, stats, PriceFetch.KEEP_MILLIS);
    }

    private static CccParser.Stats amazonStats(long scannedAt) {
        return new CccParser.Stats(
            new CccParser.Series("amazon",
                new CccParser.Price("$264.08", 264.08, "Nov 20, 2026"),
                new CccParser.Price("$479.00", 479.0, "Aug 08, 2020"),
                new CccParser.Price("$266.08", 266.08, "Nov 20, 2026"),
                new CccParser.Price("$319.34", 319.34, null)),
            null, null, scannedAt);
    }

    @Test
    public void thePayloadCarriesTheTilesAndTheVerdict() {
        String json = PricePayload.of("B076P3QK6K", "au", ok(amazonStats(NOW - 6 * HOUR)), NA, NOW);
        assertTrue(json, json.contains("\"asin\":\"B076P3QK6K\""));
        assertTrue(json, json.contains("\"code\":\"au\""));
        assertTrue(json, json.contains("\"status\":\"ok\""));
        assertTrue(json, json.contains("\"label\":\"Amazon\""));
        assertTrue(json, json.contains("\"lowest\":{\"p\":\"$264.08\",\"d\":\"Nov 20, 2026\"}"));
        assertTrue(json, json.contains("\"average\":{\"p\":\"$319.34\",\"d\":null}"));
        assertTrue(json, json.contains("\"highest\":{\"p\":\"$479.00\",\"d\":\"Aug 08, 2020\"}"));
        // 266.08 is 0.76 percent above the lowest 264.08: near the lowest, not the lowest.
        assertTrue(json, json.contains("\"kind\":\"near\""));
        assertTrue(json, json.contains("\"verdict\":\"Near the lowest price ever\""));
        assertTrue(json, json.contains("\"updated\":\"6 hours ago\""));
        assertTrue(json, json.contains("\"stale\":false"));
    }

    @Test
    public void thePayloadJudgesThePriceOnThePageWhenThereIsOne() {
        String low = PricePayload.of("B076P3QK6K", "au", ok(amazonStats(NOW - 6 * HOUR)), 258.00, NOW);
        assertTrue(low, low.contains("\"kind\":\"low\""));
        assertTrue(low, low.contains("\"verdict\":\"Lowest price ever\""));
    }

    @Test
    public void thePayloadSaysHowOldTheDataIsAndGivesNoVerdictOnOldData() {
        String old = PricePayload.of("B0869B4Q66", "au", ok(amazonStats(NOW - 3 * 365 * DAY)), 258.00, NOW);
        assertTrue(old, old.contains("\"kind\":\"stale\""));
        assertTrue(old, old.contains("\"verdict\":\"No verdict: CamelCamelCamel last checked 3 years ago\""));
        assertTrue(old, old.contains("\"updated\":\"3 years ago\""));
        assertTrue(old, old.contains("\"stale\":true"));
        assertTrue(old, old.contains("\"lowest\":{"));

        String unknown = PricePayload.of("B076P3QK6K", "au", ok(amazonStats(-1)), NA, NOW);
        assertTrue(unknown, unknown.contains("\"updated\":null"));
        assertTrue(unknown, unknown.contains("\"stale\":false"));
        assertTrue(unknown, unknown.contains("\"kind\":\"near\""));
    }

    @Test
    public void thePayloadEscapesWhatItGotFromTheNetwork() {
        CccParser.Stats stats = new CccParser.Stats(
            new CccParser.Series("amazon",
                new CccParser.Price("</script><script>alert(1)</script>\"'", 5.0, "\\u2028"),
                null, new CccParser.Price("$5.00", 5.0, null), null),
            null, null, NOW - HOUR);
        String json = PricePayload.of("B076P3QK6K", "au", ok(stats), NA, NOW);
        assertTrue(json, !json.contains("<"));
        assertTrue(json, !json.contains(">"));
        assertTrue(json, !json.contains("'"));
        assertTrue(json, json.contains("\\u003c"));
        assertTrue(json, json.contains("\\\""));
        assertTrue(json, json.contains("\"updated\":\"1 hour ago\""));
    }

    @Test
    public void thePayloadStatusesCoverEveryOutcome() {
        assertEquals("{\"asin\":\"B076P3QK6K\",\"code\":\"au\",\"status\":\"none\"}",
            PricePayload.status("B076P3QK6K", "au", PricePayload.NONE));
        assertEquals("{\"asin\":\"B076P3QK6K\",\"code\":\"au\",\"status\":\"none\"}",
            PricePayload.of("B076P3QK6K", "au", new PriceFetch.Result(PricePayload.NONE, null, 1), NA, NOW));
        assertEquals("{\"asin\":\"B076P3QK6K\",\"code\":\"au\",\"status\":\"chart\"}",
            PricePayload.of("B076P3QK6K", "au", new PriceFetch.Result(PricePayload.CHART, null, 1), NA, NOW));
        // An OK answer whose table has nothing the card can use falls back to the chart.
        assertTrue(PricePayload.of("B076P3QK6K", "au", ok(stats(null, null, series("used", 5.0, 9.0, 6.0, 7.0))),
            NA, NOW).contains("\"status\":\"chart\""));
    }
}
