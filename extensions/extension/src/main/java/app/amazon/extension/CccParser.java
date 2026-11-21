/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the price table of a CamelCamelCamel product page: one row each for Amazon, 3rd Party New and 3rd Party
 * Used, with the columns Lowest Ever, Highest Ever, Current and Average. Tolerant on purpose: it looks for the
 * table by its "Lowest Ever" header, takes the rows it can read and ignores everything else, so a changed class
 * name or extra markup costs one row at worst, not the whole card. Free of Android APIs.
 */
final class CccParser {
    private CccParser() {
    }

    /** One cell: the price as the page shows it, its number, and the date when there is one. */
    static final class Price {
        final String text;
        /** NaN when the number could not be read. */
        final double value;
        final String date;

        Price(String text, double value, String date) {
            this.text = text;
            this.value = value;
            this.date = date;
        }

        boolean hasValue() {
            return !Double.isNaN(value) && value > 0;
        }
    }

    /** One row of the table. Any cell may be null: the page shows "-" where there is nothing. */
    static final class Series {
        final String key;
        final Price lowest;
        final Price highest;
        final Price current;
        final Price average;

        Series(String key, Price lowest, Price highest, Price current, Price average) {
            this.key = key;
            this.lowest = lowest;
            this.highest = highest;
            this.current = current;
            this.average = average;
        }

        boolean hasData() {
            return lowest != null || highest != null || current != null || average != null;
        }
    }

    /** The table; a row the page does not have is null. */
    static final class Stats {
        final Series amazon;
        final Series thirdPartyNew;
        final Series thirdPartyUsed;

        Stats(Series amazon, Series thirdPartyNew, Series thirdPartyUsed) {
            this.amazon = amazon;
            this.thirdPartyNew = thirdPartyNew;
            this.thirdPartyUsed = thirdPartyUsed;
        }

        boolean hasData() {
            return (amazon != null && amazon.hasData())
                || (thirdPartyNew != null && thirdPartyNew.hasData())
                || (thirdPartyUsed != null && thirdPartyUsed.hasData());
        }
    }

    private static final int FLAGS = Pattern.DOTALL | Pattern.CASE_INSENSITIVE;
    private static final Pattern ROW = Pattern.compile("<tr\\b([^>]*)>(.*?)</tr\\s*>", FLAGS);
    private static final Pattern CELL = Pattern.compile("<td\\b[^>]*>(.*?)</td\\s*>", FLAGS);
    private static final Pattern SMALL = Pattern.compile("<small\\b[^>]*>(.*?)</small\\s*>", FLAGS);
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern FIELD = Pattern.compile("data-field\\s*=\\s*[\"']([a-z]+)[\"']", FLAGS);
    private static final Pattern DATE = Pattern.compile("([A-Za-z]{3,9}\\.?\\s+\\d{1,2},?\\s+\\d{4})");
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d.,\\u00a0\\u202f]*");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");

    /**
     * The price table of a product page, or null when the page has none (a challenge page, an error page, a product
     * that is not tracked). A table whose rows are all "-" gives a Stats with no data.
     */
    static Stats parse(String html) {
        if (html == null) return null;
        // "Lowest Ever" may also appear in prose before the table, so every occurrence gets a try.
        int header = indexOfIgnoreCase(html, "Lowest Ever", 0);
        while (header >= 0) {
            Stats stats = parseTable(tableAround(html, header));
            if (stats != null) return stats;
            header = indexOfIgnoreCase(html, "Lowest Ever", header + 1);
        }
        return null;
    }

    private static String tableAround(String html, int header) {
        int start = html.lastIndexOf("<table", header);
        if (start < 0) start = Math.max(0, header - 2000);
        int end = indexOfIgnoreCase(html, "</table", header);
        if (end < 0) end = html.length();
        return html.substring(start, end);
    }

    private static Stats parseTable(String table) {
        Series amazon = null;
        Series thirdNew = null;
        Series used = null;
        Matcher rows = ROW.matcher(table);
        while (rows.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = CELL.matcher(rows.group(2));
            while (cell.find()) cells.add(cell.group(1));
            if (cells.size() < 5) continue; // the header row has th cells, so it never gets here

            String key = rowKey(rows.group(1), cells.get(0));
            if (key == null) continue;
            Series series = new Series(key, price(cells.get(1)), price(cells.get(2)),
                price(cells.get(3)), price(cells.get(4)));
            if (key.equals("amazon") && amazon == null) amazon = series;
            else if (key.equals("new") && thirdNew == null) thirdNew = series;
            else if (key.equals("used") && used == null) used = series;
        }
        if (amazon == null && thirdNew == null && used == null) return null;
        return new Stats(amazon, thirdNew, used);
    }

    /** amazon, new or used: from the row's data-field when it has one, else from the first cell's label. */
    private static String rowKey(String rowAttributes, String labelCell) {
        Matcher field = FIELD.matcher(rowAttributes);
        if (field.find()) {
            String f = field.group(1).toLowerCase(Locale.ROOT);
            if (f.equals("amazon") || f.equals("new") || f.equals("used")) return f;
        }
        String label = text(labelCell).toLowerCase(Locale.ROOT);
        if (label.contains("used")) return "used";
        if (label.contains("new")) return "new";
        if (label.contains("amazon")) return "amazon";
        return null;
    }

    private static Price price(String cell) {
        String date = null;
        Matcher small = SMALL.matcher(cell);
        if (small.find()) {
            Matcher d = DATE.matcher(text(small.group(1)));
            if (d.find()) date = d.group(1);
        }
        String shown = text(SMALL.matcher(cell).replaceAll(" "));
        if (date == null) {
            Matcher d = DATE.matcher(shown);
            if (d.find()) {
                date = d.group(1);
                shown = shown.replace(d.group(0), " ").replaceAll("\\(\\s*\\)", " ").replaceAll("\\s+", " ").trim();
            }
        }
        if (shown.isEmpty() || shown.matches("^[-\\u2013\\u2014\\s]+$")) return null;
        return new Price(shown, amount(shown), date);
    }

    /** Visible text of a fragment: tags dropped, entities decoded, white space collapsed. */
    static String text(String html) {
        String s = TAG.matcher(html).replaceAll(" ");
        Matcher m = ENTITY.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(entity(m.group(1))));
        m.appendTail(out);
        return out.toString().replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String entity(String name) {
        switch (name) {
            case "nbsp": return " ";
            case "amp": return "&";
            case "lt": return "<";
            case "gt": return ">";
            case "quot": return "\"";
            case "apos": return "'";
            default:
                try {
                    if (name.startsWith("#x") || name.startsWith("#X")) {
                        return new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
                    }
                    if (name.startsWith("#")) {
                        return new String(Character.toChars(Integer.parseInt(name.substring(1))));
                    }
                } catch (RuntimeException ignored) {
                    // An entity that is not a valid code point: keep the text as it was.
                }
                return "&" + name + ";";
        }
    }

    /**
     * The number in a price such as "$1,234.56", "1.234,56 EUR", "\u00a512,345" or "$264.08", or NaN. When a price has
     * both separators the last one is the decimal point; a single one followed by exactly three digits is a thousands
     * separator, otherwise it is the decimal point.
     */
    static double amount(String shown) {
        if (shown == null) return Double.NaN;
        Matcher m = NUMBER.matcher(shown);
        if (!m.find()) return Double.NaN;
        String n = m.group().replace("\u00a0", "").replace("\u202f", "");
        while (n.endsWith(".") || n.endsWith(",")) n = n.substring(0, n.length() - 1);
        if (n.isEmpty()) return Double.NaN;

        int dot = n.lastIndexOf('.');
        int comma = n.lastIndexOf(',');
        String plain;
        if (dot >= 0 && comma >= 0) {
            char decimal = dot > comma ? '.' : ',';
            char thousands = decimal == '.' ? ',' : '.';
            plain = n.replace(String.valueOf(thousands), "").replace(decimal, '.');
        } else if (dot >= 0 || comma >= 0) {
            char sep = dot >= 0 ? '.' : ',';
            int last = Math.max(dot, comma);
            int count = n.length() - n.replace(String.valueOf(sep), "").length();
            int after = n.length() - last - 1;
            plain = (count > 1 || after == 3) ? n.replace(String.valueOf(sep), "") : n.replace(sep, '.');
        } else {
            plain = n;
        }
        try {
            return Double.parseDouble(plain);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        int limit = haystack.length() - needle.length();
        for (int i = Math.max(0, from); i <= limit; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) return i;
        }
        return -1;
    }
}
