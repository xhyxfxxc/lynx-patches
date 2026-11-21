/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import app.loki.extension.LokiLog;

/**
 * The only way the price history card talks to anything outside the page: the object {@code lynxPrice}, added once
 * to each Amazon WebView when it is created.
 *
 * <p>It does two things, both asked for by the card after the user tapped it (or switched on Load automatically):
 * {@code request(asin, code)} fetches the product page of CamelCamelCamel in the background and hands the lowest,
 * average and highest price back to the page, and {@code open(which, asin, code)} opens the product on
 * CamelCamelCamel or Keepa in the browser. Every argument is checked (an ASIN of ten capital letters and digits, a
 * marketplace CamelCamelCamel serves, one of two names) and anything else is ignored. Neither method returns data:
 * the answer goes to the page afterwards, to a fixed global function, and only while the WebView still shows an
 * Amazon page of the same marketplace. Requests are rate limited, answers are kept for 30 minutes per product, and
 * at most one request per product is on its way at a time.
 *
 * <p>The object is visible to every page the WebView loads, so it holds nothing worth stealing: it gives out only
 * what CamelCamelCamel shows to anyone, and it can only be used to look up prices.
 */
public final class PriceBridge {
    /** The name the page uses: window.lynxPrice. */
    static final String NAME = "lynxPrice";

    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int MAX_BODY_BYTES = 1_500_000;
    private static final int MAX_REDIRECTS = 3;

    /** The product pages this phone asked for, newest last; their answers. */
    private static final PriceCache CACHE = new PriceCache(64);
    /** At most 12 look-ups a minute in all, and 6 browser opens a minute. */
    private static final RateLimiter REQUESTS = new RateLimiter(12, 60_000L);
    private static final RateLimiter OPENS = new RateLimiter(6, 60_000L);

    /** Products being fetched right now, with the WebViews waiting for each. */
    private static final Map<String, List<WeakReference<WebView>>> IN_FLIGHT = new HashMap<>();
    private static final Map<WebView, Boolean> ATTACHED = new WeakHashMap<>();

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "lynx-price");
            thread.setDaemon(true);
            return thread;
        }
    });

    private final WeakReference<WebView> view;

    private PriceBridge(WebView view) {
        this.view = new WeakReference<>(view);
    }

    /** Adds the object to a WebView, once. Called when the WebView is created and again, harmlessly, per page. */
    public static void attach(WebView webView) {
        if (webView == null) return;
        try {
            if (!AmazonSettings.priceCard(webView.getContext())) return;
            synchronized (ATTACHED) {
                if (ATTACHED.containsKey(webView)) return;
                ATTACHED.put(webView, Boolean.TRUE);
            }
            webView.addJavascriptInterface(new PriceBridge(webView), NAME);
        } catch (RuntimeException ignored) {
            // No bridge: the card still shows the chart, without the numbers.
        }
    }

    /** Looks the product up and sends the answer to the page. Returns nothing. */
    @JavascriptInterface
    public void request(final String asin, final String code) {
        if (!PriceLinks.validAsin(asin) || !PriceLinks.cccSupports(code)) return;
        final WebView webView = view.get();
        if (webView == null) return;
        if (!REQUESTS.tryAcquire(System.currentTimeMillis())) return;
        // The bridge answers on a background thread; the WebView may only be asked about its page on the main one.
        webView.post(new Runnable() {
            @Override
            public void run() {
                if (!onAmazonPage(webView, code)) return;
                start(webView, asin, code);
            }
        });
    }

    /** Opens the product on CamelCamelCamel ("ccc") or Keepa ("keepa") in the browser. Returns nothing. */
    @JavascriptInterface
    public void open(final String which, final String asin, final String code) {
        final String url;
        if ("ccc".equals(which)) url = PriceLinks.cccProduct(code, asin);
        else if ("keepa".equals(which)) url = PriceLinks.keepaProduct(code, asin);
        else return;
        if (url == null) return;
        final WebView webView = view.get();
        if (webView == null) return;
        if (!OPENS.tryAcquire(System.currentTimeMillis())) return;
        webView.post(new Runnable() {
            @Override
            public void run() {
                if (!onAmazonPage(webView, code)) return;
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    webView.getContext().startActivity(intent);
                } catch (RuntimeException ignored) {
                    // No browser to open it in.
                }
            }
        });
    }

    /** True while the WebView shows a page of the Amazon marketplace [code]. Main thread only. */
    private static boolean onAmazonPage(WebView webView, String code) {
        try {
            String host = AmazonUrls.lenientWebHost(webView.getUrl());
            return host != null && AmazonUrls.isAmazonHost(host) && code.equals(AmazonUrls.marketplaceCode(host));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Main thread: answer from the cache, join a request already on its way, or start one. */
    private static void start(WebView webView, final String asin, final String code) {
        final String key = code + ":" + asin;
        String cached = CACHE.get(key, System.currentTimeMillis());
        if (cached != null) {
            deliver(webView, code, cached);
            return;
        }
        synchronized (IN_FLIGHT) {
            List<WeakReference<WebView>> waiting = IN_FLIGHT.get(key);
            if (waiting != null) {
                waiting.add(new WeakReference<>(webView));
                return;
            }
            waiting = new ArrayList<>();
            waiting.add(new WeakReference<>(webView));
            IN_FLIGHT.put(key, waiting);
        }
        try {
            WORKER.execute(new Runnable() {
                @Override
                public void run() {
                    finish(key, asin, code);
                }
            });
        } catch (RuntimeException e) {
            synchronized (IN_FLIGHT) {
                IN_FLIGHT.remove(key);
            }
        }
    }

    /** Worker thread: fetch, keep the answer, hand it to every WebView waiting for it. */
    private static void finish(String key, String asin, String code) {
        PriceFetch.Result result;
        try {
            result = fetch(asin, code);
        } catch (Throwable error) {
            log("price history: the request failed (" + error.getClass().getSimpleName() + ", " + code + ")");
            result = PriceFetch.classify(asin, code, 0, null);
        }
        CACHE.put(key, result.json, result.keepMillis, System.currentTimeMillis());
        List<WeakReference<WebView>> waiting;
        synchronized (IN_FLIGHT) {
            waiting = IN_FLIGHT.remove(key);
        }
        if (waiting == null) return;
        for (WeakReference<WebView> reference : waiting) {
            WebView webView = reference.get();
            if (webView != null) deliverLater(webView, code, result.json);
        }
    }

    private static void deliverLater(final WebView webView, final String code, final String json) {
        webView.post(new Runnable() {
            @Override
            public void run() {
                deliver(webView, code, json);
            }
        });
    }

    /** Main thread: hand the payload to the page, if it is still an Amazon page of this marketplace. */
    private static void deliver(WebView webView, String code, String json) {
        if (!onAmazonPage(webView, code)) return;
        try {
            webView.evaluateJavascript("window.__lynxPriceData&&window.__lynxPriceData(" + json + ");", null);
        } catch (RuntimeException ignored) {
            // A WebView that is being destroyed: nobody is waiting for the answer.
        }
    }

    /**
     * The name this request goes out under: the platform's own HTTP agent, which on Android is the honest
     * "Dalvik/2.1.0 (Linux; U; Android ...)" string. CamelCamelCamel's Cloudflare answers a browser-like name with a
     * JavaScript challenge that this plain request cannot pass, and lets a plain HTTP client through.
     */
    static String userAgent(String platformAgent, String androidRelease) {
        if (platformAgent != null && !platformAgent.trim().isEmpty()) return platformAgent.trim();
        return "Dalvik/2.1.0 (Linux; U; Android " + androidRelease + ")";
    }

    /** True for the page Cloudflare serves instead of the real one ("Just a moment..."). */
    static boolean looksLikeChallenge(String body) {
        if (body == null) return false;
        String lower = body.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("just a moment") || lower.contains("challenge-platform")
            || lower.contains("cf-browser-verification");
    }

    /** Diagnostic log (a line only while Diagnostic logging is on); used on failure paths only. */
    private static void log(String line) {
        try {
            LokiLog.debug(line);
        } catch (Throwable ignored) {
            // A logging problem must never become the price card's problem.
        }
    }

    private static PriceFetch.Result fetch(String asin, String code) throws Exception {
        String agent = userAgent(System.getProperty("http.agent"), Build.VERSION.RELEASE);
        String address = PriceLinks.cccProduct(code, asin);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            URL url = new URL(address);
            if (!"https".equals(url.getProtocol()) || !PriceLinks.isCccHost(url.getHost())) {
                log("price history: refused a redirect that left CamelCamelCamel (" + code + ")");
                return PriceFetch.classify(asin, code, 0, null);
            }
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(TIMEOUT_MILLIS);
                connection.setReadTimeout(TIMEOUT_MILLIS);
                connection.setRequestProperty("User-Agent", agent);
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        log("price history: redirect without a Location (HTTP " + status + ", " + code + ")");
                        return PriceFetch.classify(asin, code, 0, null);
                    }
                    address = new URL(url, location).toString();
                    continue;
                }
                String body = null;
                if (status == 200) {
                    body = read(connection.getInputStream());
                } else if (status == 403 || status == 503) {
                    // Only to tell a Cloudflare challenge from a plain refusal in the log.
                    body = readQuietly(connection.getErrorStream());
                }
                PriceFetch.Result result = PriceFetch.classify(asin, code, status, status == 200 ? body : null);
                if (status != 200) {
                    if (looksLikeChallenge(body)) {
                        log("price history: CamelCamelCamel sent a bot check page (HTTP " + status + ", " + code + ")");
                    } else {
                        log("price history: CamelCamelCamel answered HTTP " + status + " (" + code + ")");
                    }
                } else if (result.keepMillis == PriceFetch.RETRY_MILLIS) {
                    if (looksLikeChallenge(body)) {
                        log("price history: CamelCamelCamel sent a bot check page (HTTP 200, " + code + ")");
                    } else {
                        log("price history: could not read the price table on the page (" + code + ")");
                    }
                }
                return result;
            } finally {
                connection.disconnect();
            }
        }
        log("price history: gave up after too many redirects (" + code + ")");
        return PriceFetch.classify(asin, code, 0, null);
    }

    private static String readQuietly(InputStream in) {
        if (in == null) return null;
        try {
            return read(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static String read(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > MAX_BODY_BYTES) break;
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
