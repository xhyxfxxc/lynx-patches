/*
 * Copyright (C) 2026 xhyxfxxc
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package app.amazon.extension;

/**
 * Decides which WebView navigations "Open links in browser" sends to the external browser.
 *
 * Only an http(s) link whose host is not Amazon's leaves the app. Everything else is left to Amazon's own handling:
 * its app links (amznapp:, appnav:), intent:, tel:, mailto: and market: links, Amazon's short links, ad system and
 * Prime Video hosts, and any URL without a readable host.
 *
 * A checkout, payment or sign-in page is different. Australian checkout sends the same WebView to PayPal, Afterpay,
 * Zip and the bank's 3-D Secure page and back, and those pages may link on to further hosts of their own. From the
 * first such page until the WebView lands on an ordinary Amazon page again, nothing is diverted. Free of Android
 * APIs so it is covered by plain JVM unit tests.
 */
public final class AmazonLinkPolicy {

    /** A payment flow that never returns to Amazon stops counting after this long. */
    static final long FLOW_TIMEOUT_MILLIS = 30L * 60L * 1000L;

    private boolean inPaymentFlow;
    private long flowSince;

    /**
     * True when [url] must open in the external browser.
     *
     * @param currentUrl the URL of the page the WebView shows now, or null
     * @param nowMillis the current time, for the payment flow timeout
     */
    public synchronized boolean opensInBrowser(String url, String currentUrl, long nowMillis) {
        if (inPaymentFlow && nowMillis - flowSince > FLOW_TIMEOUT_MILLIS) inPaymentFlow = false;
        if (AmazonUrls.isPaymentPage(currentUrl)) {
            inPaymentFlow = true;
            flowSince = nowMillis;
        }

        String host = AmazonUrls.lenientWebHost(url);
        if (host == null) return false; // app link, phone, mail, market, intent, or an unreadable host

        if (AmazonUrls.isAmazonHost(host)) {
            // An Amazon page ends the flow unless it is itself a checkout, payment or sign-in page.
            boolean payment = AmazonUrls.isPaymentPage(url);
            if (payment) flowSince = nowMillis;
            inPaymentFlow = payment;
            return false;
        }
        if (AmazonUrls.isAmazonOwnedHost(host)) return false;
        return !inPaymentFlow;
    }
}
