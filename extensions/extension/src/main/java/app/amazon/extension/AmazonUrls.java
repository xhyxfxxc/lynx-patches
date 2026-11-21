/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21: moved to package app.amazon.extension.
 */

package app.amazon.extension;

/**
 * Parsed-origin trust checks for Amazon URLs.
 *
 * Deliberately free of Android APIs so the logic is covered by plain JVM unit
 * tests (see AmazonUrlsTest). Trust is decided from a parsed URI and a
 * validated hostname, never from substring matching on the whole URL.
 */

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AmazonUrls {

    private AmazonUrls() {
    }

    /**
     * Registrable domains of the Amazon marketplaces these patches target,
     * mapped to the marketplace country code used by the price-history
     * providers. A small maintained allowlist rather than a broad pattern:
     * an unknown "amazon-ish" host must fail closed.
     */
    private static final Map<String, String> MARKETPLACES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("amazon.com", "us");
        m.put("amazon.co.uk", "uk");
        m.put("amazon.de", "de");
        m.put("amazon.fr", "fr");
        m.put("amazon.co.jp", "jp");
        m.put("amazon.ca", "ca");
        m.put("amazon.it", "it");
        m.put("amazon.es", "es");
        m.put("amazon.in", "in");
        m.put("amazon.nl", "nl");
        m.put("amazon.se", "se");
        m.put("amazon.pl", "pl");
        m.put("amazon.ie", "ie");
        m.put("amazon.ae", "ae");
        m.put("amazon.sa", "sa");
        m.put("amazon.eg", "eg");
        m.put("amazon.sg", "sg");
        m.put("amazon.com.au", "au");
        m.put("amazon.com.br", "br");
        m.put("amazon.com.mx", "mx");
        m.put("amazon.com.be", "be");
        m.put("amazon.com.tr", "tr");
        MARKETPLACES = Collections.unmodifiableMap(m);
    }

    /**
     * Amazon-operated asset domains ordinary in-app navigation touches.
     * Amazon URL shorteners (a.co, amzn.to, ...) are intentionally absent: for
     * trust decisions (price history) an unfamiliar shortener is not Amazon.
     * See {@link #isAmazonOwnedHost(String)} for what navigation leaves alone.
     */
    private static final String[] ASSET_DOMAINS = {
        "media-amazon.com",
        "ssl-images-amazon.com",
    };

    /**
     * Other hosts the Amazon app itself sends the WebView to: the ad system,
     * Prime Video and Amazon's short links, which the app resolves and routes
     * itself. Navigation to them is never diverted to the browser.
     */
    private static final String[] AMAZON_OWNED_DOMAINS = {
        "amazon-adsystem.com",
        "primevideo.com",
        "a.co",
        "amzn.to",
        "amzn.asia",
        "amzn.eu",
        "amzn.com",
    };

    /**
     * Pages that carry a purchase or a sign-in (checkout, payment, cart,
     * sign-in, Amazon Pay), as whole path segments: "/gp/buy" matches
     * /gp/buy/spc/handlers/x.html but not /gp/buyagain. Australian checkout
     * redirects to PayPal, Afterpay, Zip and bank 3-D Secure pages inside the
     * same WebView, so a link on such a page must never leave the app.
     */
    private static final String[] PAYMENT_SEGMENTS = {
        "/gp/buy", "/gp/cart", "/gp/checkoutportal", "/gp/payment", "/gp/prime",
        "/checkout", "/cart", "/cpe", "/ap", "/ax", "/apay", "/payment", "/payments",
        "/payments-portal",
    };

    /** Sign-in pages whose names continue after the prefix (/gp/aw/sign-in.html, /gp/sign-in.html). */
    private static final String[] PAYMENT_RAW_PREFIXES = {
        "/gp/aw/sign", "/gp/sign",
    };

    private static final Pattern ASIN_PATH = Pattern.compile(
        "/(?:dp|gp/product|gp/aw/d)/([A-Za-z0-9]{10})(?![A-Za-z0-9])");

    /**
     * Returns the normalized hostname of a well-formed http(s) URL, or null.
     *
     * Null for: malformed URIs, relative/hostless URLs, opaque URIs
     * (javascript:, data:, mailto:), non-web schemes (file:, content:, intent:)
     * and URLs carrying userinfo (https://amazon.com@evil.example/).
     */
    public static String webHost(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return null;

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            return null;
        }

        if (!uri.isAbsolute() || uri.isOpaque()) return null;

        String scheme = uri.getScheme();
        if (scheme == null) return null;
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return null;

        // Userinfo is never legitimate for Amazon page navigation and is the
        // classic way to make a hostile host look like amazon.com.
        if (uri.getRawUserInfo() != null) return null;

        // getHost() is null when the authority is not a valid registry host,
        // which is exactly the fail-closed outcome we want.
        String host = uri.getHost();
        if (host == null) return null;

        host = host.toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.isEmpty() ? null : host;
    }

    /**
     * The host of an http(s) URL read the way a browser would, or null.
     *
     * Unlike {@link #webHost(String)} this does not insist on a strictly valid
     * URI: spaces, pipes or braces in the query do not matter, and userinfo is
     * dropped instead of rejecting the URL, so https://amazon.com@evil.example/
     * has the host evil.example. Null for every non-http(s) scheme (amznapp:,
     * appnav:, intent:, tel:, mailto:, market:, javascript:, ...) and for a URL
     * whose host is missing or holds whitespace or control characters.
     */
    public static String lenientWebHost(String url) {
        String authority = webAuthority(url);
        if (authority == null) return null;
        int at = authority.lastIndexOf('@');
        if (at >= 0) authority = authority.substring(at + 1);
        String host;
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close < 0) return null;
            host = authority.substring(0, close + 1);
        } else {
            int colon = authority.indexOf(':');
            host = colon >= 0 ? authority.substring(0, colon) : authority;
        }
        host = host.toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty()) return null;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c <= 0x20 || c == 0x7f) return null;
        }
        return host;
    }

    /** True when the host is an Amazon marketplace, asset, ad-system, Prime Video or short-link host. */
    public static boolean isAmazonOwnedHost(String host) {
        if (host == null) return false;
        if (isAmazonHost(host)) return true;
        for (String domain : AMAZON_OWNED_DOMAINS) {
            if (matches(host, domain)) return true;
        }
        return false;
    }

    /**
     * True for an Amazon marketplace page that carries checkout, payment, cart,
     * sign-in or Amazon Pay, judged by the path (a leading /-/xx language
     * segment is ignored).
     */
    public static boolean isPaymentPage(String url) {
        String host = lenientWebHost(url);
        if (host == null || !isAmazonHost(host)) return false;
        String path = webPath(url).toLowerCase(Locale.ROOT);
        Matcher language = LANGUAGE_PREFIX.matcher(path);
        if (language.find()) path = path.substring(language.end());
        if (path.isEmpty()) path = "/";
        for (String prefix : PAYMENT_SEGMENTS) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) return true;
        }
        for (String prefix : PAYMENT_RAW_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    /** True when the host is an Amazon marketplace/asset host, matched on dot boundaries. */
    public static boolean isAmazonHost(String host) {
        if (host == null) return false;
        if (marketplaceDomain(host) != null) return true;
        for (String domain : ASSET_DOMAINS) {
            if (matches(host, domain)) return true;
        }
        return false;
    }

    /** True only for a well-formed http(s) URL whose validated host belongs to Amazon. */
    public static boolean isTrustedAmazonUrl(String url) {
        return isAmazonHost(webHost(url));
    }

    /** The registrable Amazon marketplace domain for a host, or null. */
    public static String marketplaceDomain(String host) {
        if (host == null) return null;
        for (String domain : MARKETPLACES.keySet()) {
            if (matches(host, domain)) return domain;
        }
        return null;
    }

    /** Marketplace country code (us, uk, in, ...) for a host, or null. */
    public static String marketplaceCode(String host) {
        String domain = marketplaceDomain(host);
        return domain == null ? null : MARKETPLACES.get(domain);
    }

    /** The ASIN from a trusted Amazon product URL path, uppercased, or null. */
    public static String productAsin(String url) {
        String host = webHost(url);
        if (!isAmazonHost(host)) return null;

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return null;
        }
        String path = uri.getPath();
        if (path == null) return null;

        Matcher m = ASIN_PATH.matcher(path);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : null;
    }

    /** Canonical product URL without tracking parameters, or null when not a product page. */
    public static String canonicalProductUrl(String url) {
        String asin = productAsin(url);
        if (asin == null) return null;
        String domain = marketplaceDomain(webHost(url));
        if (domain == null) return null;
        return "https://www." + domain + "/dp/" + asin;
    }

    private static final Pattern LANGUAGE_PREFIX = Pattern.compile("^/-/[a-z_-]{2,8}(?=/|$)");

    /** The authority (userinfo, host and port) of an http(s) URL, or null for any other URL. */
    private static String webAuthority(String url) {
        if (url == null) return null;
        String s = url.trim();
        int colon = s.indexOf(':');
        if (colon <= 0) return null;
        String scheme = s.substring(0, colon).toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return null;
        if (!s.startsWith("//", colon + 1)) return null;
        int start = colon + 3;
        int end = start;
        while (end < s.length() && "/?#\\".indexOf(s.charAt(end)) < 0) end++;
        return s.substring(start, end);
    }

    /** The path of an http(s) URL as written ("/" when there is none), without query or fragment. */
    private static String webPath(String url) {
        String s = url.trim();
        int start = s.indexOf("//") + 2;
        int end = start;
        while (end < s.length() && "/?#\\".indexOf(s.charAt(end)) < 0) end++;
        int pathEnd = end;
        while (pathEnd < s.length() && "?#".indexOf(s.charAt(pathEnd)) < 0) pathEnd++;
        String path = s.substring(end, pathEnd);
        return path.isEmpty() ? "/" : path;
    }

    private static boolean matches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
