/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The verdict line and which price series the card is about. */
public class PriceVerdictTest {

    private static final double NA = Double.NaN;

    private static void verdict(String kind, String text, double current, double lowest, double average) {
        PriceVerdict v = PriceVerdict.of(current, lowest, average);
        assertEquals(kind, v.kind);
        assertEquals(text, v.text);
    }

    @Test
    public void lowestEverWithinOnePercent() {
        verdict(PriceVerdict.LOW, "Lowest price ever", 100.0, 100.0, 200.0);
        verdict(PriceVerdict.LOW, "Lowest price ever", 90.0, 100.0, 200.0);
        verdict(PriceVerdict.LOW, "Lowest price ever", 101.0, 100.0, 200.0);
        // 100 * 1.01 is not exactly 101 in floating point; the boundary still counts as lowest.
        verdict(PriceVerdict.NEAR, "Near the lowest price ever", 101.01, 100.0, 200.0);
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

    @Test
    public void usesAmazonWhenItHasACurrentPrice() {
        CccParser.Stats stats = new CccParser.Stats(
            series("amazon", 100.0, 250.0, 100.5, 180.0),
            series("new", 90.0, 300.0, 90.0, 190.0), null);
        PriceVerdict.Chosen chosen = PriceVerdict.choose(stats);
        assertEquals("Amazon", chosen.label);
        assertEquals("amazon", chosen.series.key);
        assertEquals(PriceVerdict.LOW, PriceVerdict.forSeries(chosen.series).kind);
    }

    @Test
    public void fallsBackToThirdPartyNewWhenAmazonHasNoCurrentPrice() {
        CccParser.Stats stats = new CccParser.Stats(
            series("amazon", 100.0, 250.0, null, 180.0),
            series("new", 90.0, 300.0, 94.0, 190.0), null);
        PriceVerdict.Chosen chosen = PriceVerdict.choose(stats);
        assertEquals("3rd party new", chosen.label);
        assertEquals("new", chosen.series.key);
        assertEquals(PriceVerdict.NEAR, PriceVerdict.forSeries(chosen.series).kind);
    }

    @Test
    public void showsHistoryWithoutAVerdictWhenNothingIsOnSaleNow() {
        CccParser.Stats stats = new CccParser.Stats(
            series("amazon", 100.0, 250.0, null, 180.0),
            series("new", 90.0, 300.0, null, 190.0), null);
        PriceVerdict.Chosen chosen = PriceVerdict.choose(stats);
        assertEquals("Amazon", chosen.label);
        assertEquals(PriceVerdict.NONE, PriceVerdict.forSeries(chosen.series).kind);

        CccParser.Stats onlyThirdParty = new CccParser.Stats(
            series("amazon", null, null, null, null),
            series("new", 90.0, 300.0, null, 190.0), null);
        assertEquals("3rd party new", PriceVerdict.choose(onlyThirdParty).label);
    }

    @Test
    public void chooseGivesNullWhenThereIsNothingToShow() {
        assertNull(PriceVerdict.choose(null));
        assertNull(PriceVerdict.choose(new CccParser.Stats(null, null, null)));
        assertNull(PriceVerdict.choose(new CccParser.Stats(
            series("amazon", null, null, null, null), series("new", null, null, null, null),
            series("used", 5.0, 9.0, 6.0, 7.0))));
    }

    @Test
    public void thePayloadCarriesTheTilesAndTheVerdict() {
        CccParser.Stats stats = new CccParser.Stats(
            new CccParser.Series("amazon",
                new CccParser.Price("$264.08", 264.08, "Nov 20, 2026"),
                new CccParser.Price("$479.00", 479.0, "Aug 08, 2020"),
                new CccParser.Price("$266.08", 266.08, "Nov 20, 2026"),
                new CccParser.Price("$319.34", 319.34, null)),
            null, null);
        String json = PricePayload.of("B076P3QK6K", "au", stats);
        assertTrue(json, json.contains("\"asin\":\"B076P3QK6K\""));
        assertTrue(json, json.contains("\"code\":\"au\""));
        assertTrue(json, json.contains("\"status\":\"ok\""));
        assertTrue(json, json.contains("\"label\":\"Amazon\""));
        assertTrue(json, json.contains("\"lowest\":{\"p\":\"$264.08\",\"d\":\"Nov 20, 2026\"}"));
        assertTrue(json, json.contains("\"average\":{\"p\":\"$319.34\",\"d\":null}"));
        assertTrue(json, json.contains("\"highest\":{\"p\":\"$479.00\",\"d\":\"Aug 08, 2020\"}"));
        // 266.08 is 0.7 percent above the lowest 264.08, so it is the lowest price ever.
        assertTrue(json, json.contains("\"kind\":\"low\""));
        assertTrue(json, json.contains("\"verdict\":\"Lowest price ever\""));
    }

    @Test
    public void thePayloadEscapesWhatItGotFromTheNetwork() {
        CccParser.Stats stats = new CccParser.Stats(
            new CccParser.Series("amazon",
                new CccParser.Price("</script><script>alert(1)</script>\"'", 5.0, "\\u2028"),
                null, new CccParser.Price("$5.00", 5.0, null), null),
            null, null);
        String json = PricePayload.of("B076P3QK6K", "au", stats);
        assertTrue(json, !json.contains("<"));
        assertTrue(json, !json.contains(">"));
        assertTrue(json, !json.contains("'"));
        assertTrue(json, json.contains("\\u003c"));
        assertTrue(json, json.contains("\\\""));
    }

    @Test
    public void thePayloadStatusesCoverEveryOutcome() {
        assertEquals("{\"asin\":\"B076P3QK6K\",\"code\":\"au\",\"status\":\"none\"}",
            PricePayload.status("B076P3QK6K", "au", PricePayload.NONE));
        assertTrue(PricePayload.of("B076P3QK6K", "au", null).contains("\"status\":\"chart\""));
        assertTrue(PricePayload.of("B076P3QK6K", "au", new CccParser.Stats(
            series("amazon", null, null, null, null), null, null)).contains("\"status\":\"none\""));
        assertNotNull(PricePayload.of("B076P3QK6K", "au", new CccParser.Stats(
            null, null, series("used", 5.0, 9.0, 6.0, 7.0))));
        assertTrue(PricePayload.of("B076P3QK6K", "au", new CccParser.Stats(
            null, null, series("used", 5.0, 9.0, 6.0, 7.0))).contains("\"status\":\"chart\""));
    }
}
