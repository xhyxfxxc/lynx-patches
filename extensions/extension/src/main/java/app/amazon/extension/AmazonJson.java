/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21: moved out of AmazonHelper so plain JVM tests can reach it.
 */

package app.amazon.extension;

/** JSON text for the JavaScript the patches generate. Free of Android APIs. */
final class AmazonJson {
    private AmazonJson() {
    }

    /**
     * JSON-encodes a value for embedding in generated JavaScript. Quotes,
     * backslashes, control characters, angle brackets, ampersands, apostrophes
     * and the JS line terminators are escaped, so no runtime value (ASIN,
     * marketplace, option string, label, CSS or selector) can be parsed as
     * JavaScript syntax.
     */
    static String string(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20 || c > 0x7e || c == '<' || c == '>' || c == '&'
                        || c == '\'' || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append("\"").toString();
    }

    /** A JSON array of strings. */
    static String array(String[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(string(values[i]));
        }
        return sb.append("]").toString();
    }
}
