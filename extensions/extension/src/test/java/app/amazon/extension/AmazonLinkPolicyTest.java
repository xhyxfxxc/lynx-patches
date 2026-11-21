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

/** Which links "Open links in browser" diverts, and which it leaves to Amazon. */
public class AmazonLinkPolicyTest {

    private static final String HOME = "https://www.amazon.com.au/";
    private static final String CHECKOUT =
        "https://www.amazon.com.au/gp/buy/spc/handlers/display.html?hasWorkingJavascript=1";

    private static boolean opens(String url, String current) {
        return new AmazonLinkPolicy().opensInBrowser(url, current, 0L);
    }

    @Test
    public void externalWebsitesOpenInTheBrowser() {
        assertTrue(opens("https://example.com/", HOME));
        assertTrue(opens("http://example.com/page?a=1", HOME));
        assertTrue(opens("https://news.example.org/story", null));
        assertTrue(opens("HTTPS://EXAMPLE.COM/", HOME));
    }

    @Test
    public void lookalikeHostsOpenInTheBrowser() {
        assertTrue(opens("https://evilamazon.com/", HOME));
        assertTrue(opens("https://amazon.com.evil.example/", HOME));
        assertTrue(opens("https://amazon.com@evil.example/", HOME));
        assertTrue(opens("https://www.amazon.com:pass@evil.example/dp/B0CX23V2ZK", HOME));
        assertTrue(opens("https://evil.example\\@amazon.com/", HOME));
        assertTrue(opens("https://evil.example/?next=amazon.com", HOME));
    }

    @Test
    public void amazonHostsStayInTheApp() {
        assertFalse(opens("https://www.amazon.com.au/dp/B0CX23V2ZK", HOME));
        assertFalse(opens("https://www.amazon.com/s?k=a|b&x={y}", HOME));
        assertFalse(opens("https://m.media-amazon.com/images/I/x.jpg", HOME));
        assertFalse(opens("https://evil@www.amazon.com/", HOME));
    }

    @Test
    public void amazonOwnedHostsStayInTheApp() {
        assertFalse(opens("https://s.amazon-adsystem.com/x", HOME));
        assertFalse(opens("https://www.primevideo.com/detail/abc", HOME));
        assertFalse(opens("https://a.co/d/abc123", HOME));
        assertFalse(opens("https://amzn.to/3abc", HOME));
        assertFalse(opens("https://amzn.asia/d/abc", HOME));
        assertFalse(opens("https://amzn.eu/d/abc", HOME));
        assertFalse(opens("https://amzn.com/abc", HOME));
        assertTrue(opens("https://nota.co/", HOME));
        assertTrue(opens("https://evil-amzn.to/", HOME));
    }

    @Test
    public void appLinksPhoneAndMailLinksAreLeftToAmazon() {
        assertFalse(opens("amznapp://page?app-action=dismiss-all", HOME));
        assertFalse(opens("appnav://wallet", HOME));
        assertFalse(opens("intent://www.amazon.com/#Intent;scheme=https;end", HOME));
        assertFalse(opens("tel:+61255501234", HOME));
        assertFalse(opens("mailto:help@example.com", HOME));
        assertFalse(opens("market://details?id=com.example", HOME));
        assertFalse(opens("javascript:void(0)", HOME));
        assertFalse(opens("data:text/html;base64,PHNjcmlwdD4x", HOME));
        assertFalse(opens("file:///sdcard/x.html", HOME));
        assertFalse(opens("sms:+61255501234", HOME));
    }

    @Test
    public void unreadableUrlsAreLeftToAmazon() {
        assertFalse(opens(null, HOME));
        assertFalse(opens("", HOME));
        assertFalse(opens("   ", HOME));
        assertFalse(opens("www.example.com", HOME));
        assertFalse(opens("//example.com/x", HOME));
        assertFalse(opens("https://", HOME));
        assertFalse(opens("https:///path", HOME));
        assertFalse(opens("https://exa mple.com/", HOME));
        assertFalse(opens("https:/example.com/", HOME));
        assertFalse(opens("ht tp://example.com", HOME));
    }

    @Test
    public void nothingLeavesTheAppFromACheckoutOrPaymentPage() {
        assertFalse(opens("https://www.paypal.com/checkoutnow?token=EC-1", CHECKOUT));
        assertFalse(opens("https://portal.afterpay.com/au/checkout", CHECKOUT));
        assertFalse(opens("https://checkout.zip.co/x", "https://www.amazon.com.au/checkout/p/p-123/pay"));
        assertFalse(opens("https://acs.bank.example/3ds", "https://www.amazon.com.au/cpe/yourpayments/wallet"));
        assertFalse(opens("https://accounts.example.com/oauth", "https://www.amazon.com.au/ap/signin?openid.x=1"));
        assertFalse(opens("https://www.paypal.com/", "https://www.amazon.com.au/gp/aw/sign-in.html"));
        assertFalse(opens("https://www.paypal.com/", "https://www.amazon.com.au/gp/cart/view.html"));
        assertFalse(opens("https://www.paypal.com/", "https://www.amazon.com.au/payments/x"));
        assertFalse(opens("https://www.paypal.com/", "https://www.amazon.com.au/-/en/gp/buy/spc/x"));
        assertFalse(opens("https://www.paypal.com/", "https://www.amazon.com.au/apay/home"));
    }

    @Test
    public void thePaymentFlowContinuesThroughExternalPagesUntilAmazonIsBack() {
        AmazonLinkPolicy policy = new AmazonLinkPolicy();
        String thankYou = "https://www.amazon.com.au/gp/buy/thankyou";
        // Checkout redirects to the bank's page, which links on to another host: still the same flow.
        assertFalse(policy.opensInBrowser("https://bank.example/3ds", CHECKOUT, 1000L));
        assertFalse(policy.opensInBrowser("https://secure.card.example/verify", "https://bank.example/3ds", 2000L));
        // Back on a checkout page: still in the flow.
        assertFalse(policy.opensInBrowser(thankYou, "https://secure.card.example/done", 3000L));
        assertFalse(policy.opensInBrowser("https://other.example/", thankYou, 4000L));
        // An ordinary Amazon page ends it.
        String product = "https://www.amazon.com.au/dp/B0CX23V2ZK";
        assertFalse(policy.opensInBrowser(product, thankYou, 5000L));
        assertTrue(policy.opensInBrowser("https://other.example/", product, 6000L));
    }

    @Test
    public void aStalePaymentFlowTimesOut() {
        AmazonLinkPolicy policy = new AmazonLinkPolicy();
        assertFalse(policy.opensInBrowser("https://bank.example/3ds", CHECKOUT, 0L));
        assertFalse(policy.opensInBrowser("https://other.example/", "https://bank.example/3ds", 1000L));
        assertTrue(policy.opensInBrowser("https://other.example/", "https://bank.example/3ds",
            AmazonLinkPolicy.FLOW_TIMEOUT_MILLIS + 1001L));
    }

    @Test
    public void ordinaryAmazonPagesAreNotPaymentPages() {
        assertFalse(AmazonUrls.isPaymentPage("https://www.amazon.com.au/dp/B0CX23V2ZK"));
        assertFalse(AmazonUrls.isPaymentPage("https://www.amazon.com.au/gp/buyagain"));
        assertFalse(AmazonUrls.isPaymentPage("https://www.amazon.com.au/s?k=checkout"));
        assertFalse(AmazonUrls.isPaymentPage("https://www.amazon.com.au/gp/your-account/ship-track?x=1"));
        assertFalse(AmazonUrls.isPaymentPage("https://www.amazon.com.au/apple-watch/b?node=1"));
        assertFalse(AmazonUrls.isPaymentPage("https://evil.example/checkout/p/1"));
        assertFalse(AmazonUrls.isPaymentPage("https://evil.example/?u=https://www.amazon.com.au/gp/buy"));
        assertFalse(AmazonUrls.isPaymentPage(null));
        assertFalse(AmazonUrls.isPaymentPage("amznapp://checkout"));
    }

    @Test
    public void recognisesPaymentPages() {
        assertTrue(AmazonUrls.isPaymentPage("https://www.amazon.com.au/gp/buy/spc/handlers/display.html"));
        assertTrue(AmazonUrls.isPaymentPage("https://www.amazon.com.au/checkout/p/p-1/pay"));
        assertTrue(AmazonUrls.isPaymentPage("https://www.amazon.com.au/cpe/yourpayments/wallet"));
        assertTrue(AmazonUrls.isPaymentPage("https://www.amazon.com.au/ap/signin"));
        assertTrue(AmazonUrls.isPaymentPage("https://www.amazon.com.au/ax/claim"));
        assertTrue(AmazonUrls.isPaymentPage("https://www.amazon.com.au/gp/aw/sign-in.html"));
        assertTrue(AmazonUrls.isPaymentPage("https://www.amazon.com.au/gp/cart/view.html"));
        assertTrue(AmazonUrls.isPaymentPage("https://www.amazon.com.au/cart"));
        assertTrue(AmazonUrls.isPaymentPage("https://www.amazon.com.au/payments-portal/r1/x"));
        assertTrue(AmazonUrls.isPaymentPage("https://www.amazon.com.au/-/en/checkout/p/x"));
    }

    @Test
    public void lenientHostReadsLikeABrowser() {
        assertEquals("example.com", AmazonUrls.lenientWebHost("https://example.com/a b?c=|{}"));
        assertEquals("example.com", AmazonUrls.lenientWebHost("https://user:pw@example.com:8443/x"));
        assertEquals("example.com", AmazonUrls.lenientWebHost("  HTTP://Example.COM./x  "));
        assertEquals("[::1]", AmazonUrls.lenientWebHost("http://[::1]:8080/"));
        assertEquals("evil.example", AmazonUrls.lenientWebHost("https://amazon.com@evil.example/"));
        assertEquals("example.com", AmazonUrls.lenientWebHost("https://example.com?x=1"));
        assertEquals("example.com", AmazonUrls.lenientWebHost("https://example.com#frag"));
        assertNull(AmazonUrls.lenientWebHost("amznapp://page"));
        assertNull(AmazonUrls.lenientWebHost("tel:+1"));
        assertNull(AmazonUrls.lenientWebHost("https://"));
        assertNull(AmazonUrls.lenientWebHost("https://[::1/"));
        assertNull(AmazonUrls.lenientWebHost("https://exa mple.com/"));
    }
}
