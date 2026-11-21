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

/** Reads the price table of a CamelCamelCamel product page, from a small page written for these tests. */
public class CccParserTest {

    /** The structure of the real table (a header row, then one row per price type), with made-up numbers. */
    private static final String PAGE = "<html><head><title>Test Product - CamelCamelCamel</title></head><body>"
        + "<p>Tracking this since 2019. Lowest Ever prices are checked daily.</p>"
        + "<table class=\"other\"><tr><td>unrelated</td></tr></table>"
        + "<table>\n"
        + "  <thead><tr>\n"
        + "    <th class=\"fixed\"><span class=\"circle empty\"><!-- --></span>\n Price Type\n </th>\n"
        + "    <th>\n Lowest Ever\n </th><th>\n Highest Ever\n </th>\n"
        + "    <th>\n Current <span title=\"Last checked Nov 20, 2026.\">+</span>\n </th><th>\n Average\n </th>\n"
        + "  </tr></thead>\n"
        + "  <tbody>\n"
        + row("amazon on", "amazon", "Amazon", "$100.00", "(Nov 20, 2026)", "$250.00", "(Aug 08, 2020)",
            "$102.50", "(Nov 20, 2026)", "$180.25", null)
        + row("new ", "new", "3rd Party New", "$95.00", "(Nov 20, 2026)", "$300.00", "(Feb 01, 2022)",
            "$95.00", "(Nov 20, 2026)", "$190.10", null)
        + row("used ", "used", "3rd Party Used", "$70.00", "(May 06, 2023)", "$150.00", "(Jun 17, 2025)",
            "-", null, "$99.99", null)
        + "  </tbody>\n</table></body></html>";

    private static String row(String cls, String field, String label, String low, String lowDate, String high,
                              String highDate, String current, String currentDate, String average, String x) {
        return "<tr class=\"pt " + cls + "\" data-field=\"" + field + "\">\n"
            + "  <td class=\"fixed nowrap " + field + "\"><span class=\"circle " + field + "\">&nbsp;</span>\n "
            + label + "\n </td>\n"
            + cell(low, lowDate) + cell(high, highDate) + cell(current, currentDate) + cell(average, null)
            + "</tr>\n";
    }

    private static String cell(String price, String date) {
        return "  <td>\n    " + price + (date == null ? "" : "\n    <small>" + date + "</small>") + "\n  </td>\n";
    }

    @Test
    public void readsEveryRowAndColumn() {
        CccParser.Stats stats = CccParser.parse(PAGE);
        assertNotNull(stats);

        CccParser.Series amazon = stats.amazon;
        assertEquals("$100.00", amazon.lowest.text);
        assertEquals(100.0, amazon.lowest.value, 0.0001);
        assertEquals("Nov 20, 2026", amazon.lowest.date);
        assertEquals("$250.00", amazon.highest.text);
        assertEquals("Aug 08, 2020", amazon.highest.date);
        assertEquals(102.5, amazon.current.value, 0.0001);
        assertEquals(180.25, amazon.average.value, 0.0001);
        assertNull(amazon.average.date);

        assertEquals(95.0, stats.thirdPartyNew.lowest.value, 0.0001);
        assertEquals(190.10, stats.thirdPartyNew.average.value, 0.0001);

        assertEquals(70.0, stats.thirdPartyUsed.lowest.value, 0.0001);
        assertEquals("May 06, 2023", stats.thirdPartyUsed.lowest.date);
        assertNull("a dash is no price", stats.thirdPartyUsed.current);
        assertEquals(99.99, stats.thirdPartyUsed.average.value, 0.0001);
        assertTrue(stats.hasData());
    }

    @Test
    public void aTableOfDashesHasNoData() {
        String dashes = "<table><tr><th>Lowest Ever</th></tr>"
            + "<tr data-field=\"amazon\"><td>Amazon</td><td>-</td><td>-</td><td>-</td><td>-</td></tr>"
            + "<tr data-field=\"new\"><td>3rd Party New</td><td>-</td><td>-</td><td>-</td><td>-</td></tr>"
            + "<tr data-field=\"used\"><td>3rd Party Used</td><td>-</td><td>-</td><td>-</td><td>-</td></tr></table>";
        CccParser.Stats stats = CccParser.parse(dashes);
        assertNotNull(stats);
        assertTrue(!stats.hasData());
        assertNull(stats.amazon.lowest);
    }

    @Test
    public void findsRowsByTheirLabelWhenThereIsNoDataField() {
        String page = "<table><thead><tr><th>Price Type</th><th>Lowest Ever</th><th>Highest Ever</th><th>Current</th>"
            + "<th>Average</th></tr></thead><tbody>"
            + "<tr class=\"x\"><td>Amazon</td><td>$1.00</td><td>$3.00</td><td>$2.00</td><td>$2.00</td></tr>"
            + "<tr><td>3rd Party New</td><td>$1.50</td><td>$3.50</td><td>$2.50</td><td>$2.50</td></tr>"
            + "<tr><td>3rd Party Used</td><td>$0.50</td><td>$2.50</td><td>$1.50</td><td>$1.50</td></tr>"
            + "</tbody></table>";
        CccParser.Stats stats = CccParser.parse(page);
        assertNotNull(stats);
        assertEquals(1.0, stats.amazon.lowest.value, 0.0001);
        assertEquals(1.5, stats.thirdPartyNew.lowest.value, 0.0001);
        assertEquals(0.5, stats.thirdPartyUsed.lowest.value, 0.0001);
    }

    @Test
    public void aDateWrittenInTheCellInsteadOfInSmallIsStillFound() {
        String page = "<table><tr><th>Lowest Ever</th></tr><tr data-field=\"amazon\"><td>Amazon</td>"
            + "<td>$10.00 (Jan 05, 2024)</td><td>$20.00</td><td>$15.00</td><td>$16.00</td></tr></table>";
        CccParser.Stats stats = CccParser.parse(page);
        assertEquals("$10.00", stats.amazon.lowest.text);
        assertEquals("Jan 05, 2024", stats.amazon.lowest.date);
    }

    @Test
    public void entitiesAndExtraMarkupDoNotMatter() {
        String page = "<TABLE><TR><TH>LOWEST EVER</TH></TR>"
            + "<TR DATA-FIELD='amazon'><TD><b>Amazon</b></TD>"
            + "<TD><span class=\"price\">&#36;12.50</span> <SMALL>(Mar 02, 2025)</SMALL></TD>"
            + "<TD>&#x24;20.00</TD><TD>&euro;?</TD><TD>$14.00</TD></TR></TABLE>";
        CccParser.Stats stats = CccParser.parse(page);
        assertNotNull(stats);
        assertEquals("$12.50", stats.amazon.lowest.text);
        assertEquals(12.5, stats.amazon.lowest.value, 0.0001);
        assertEquals("Mar 02, 2025", stats.amazon.lowest.date);
        assertEquals("$20.00", stats.amazon.highest.text);
        assertEquals("$14.00", stats.amazon.average.text);
    }

    @Test
    public void pagesWithoutThePriceTableGiveNull() {
        assertNull(CccParser.parse(null));
        assertNull(CccParser.parse(""));
        assertNull(CccParser.parse("<html><title>Just a moment...</title><body>Checking your browser</body></html>"));
        assertNull(CccParser.parse("<html><body>Lowest Ever is mentioned, but there is no table</body></html>"));
        assertNull(CccParser.parse("<table><tr><th>Lowest Ever</th></tr><tr><td>nothing</td></tr></table>"));
    }

    @Test
    public void readsPricesInOtherFormats() {
        assertEquals(1234.56, CccParser.amount("$1,234.56"), 0.0001);
        assertEquals(1234.56, CccParser.amount("1.234,56 €"), 0.0001);
        assertEquals(1234.0, CccParser.amount("1.234 €"), 0.0001);
        assertEquals(12.5, CccParser.amount("12,50 €"), 0.0001);
        assertEquals(12345.0, CccParser.amount("¥12,345"), 0.0001);
        assertEquals(1234567.89, CccParser.amount("$1,234,567.89"), 0.0001);
        assertEquals(264.08, CccParser.amount("$264.08"), 0.0001);
        assertEquals(1234.5, CccParser.amount("£1 234,50"), 0.0001);
        assertEquals(99.0, CccParser.amount("99"), 0.0001);
        assertTrue(Double.isNaN(CccParser.amount("-")));
        assertTrue(Double.isNaN(CccParser.amount("")));
        assertTrue(Double.isNaN(CccParser.amount(null)));
    }

    @Test
    public void textDropsTagsAndDecodesEntities() {
        assertEquals("Price & more", CccParser.text("<b>Price</b>&nbsp;&amp; more"));
        assertEquals("a b", CccParser.text("a\n   <br>  b"));
        assertEquals("&bogus;", CccParser.text("&bogus;"));
    }
}
