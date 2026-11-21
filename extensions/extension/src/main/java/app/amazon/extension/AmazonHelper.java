/*
 * Copyright (C) 2026 rushiranpise
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from rushiranpise/morphe-patches (https://github.com/rushiranpise/morphe-patches), modified for
 * Lynx Patches on 2026-11-21: moved to package app.amazon.extension.
 */

package app.amazon.extension;

/**
 * AmazonHelper: runtime injection helper for Amazon Shopping patches.
 *
 * Ad-blocking selectors and dark-mode CSS are inspired by and partially
 * derived from amznkiller by hxreborn:
 *   https://github.com/hxreborn/amznkiller
 *   License: GPL-3.0
 *
 * Everything this helper executes or injects is vendored in this repository and
 * shipped inside the patch bundle. No script or style is downloaded at runtime,
 * so the JavaScript and CSS applied to an authenticated Amazon page are fully
 * determined by reviewed source at build time. The one thing fetched at run
 * time is data: the price numbers of the price history card (see PriceBridge),
 * which reach the page as JSON through textContent only.
 */

import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;

public class AmazonHelper {

    // ---- Ad selectors (vendored, static) ----

    /**
     * Vendored selector list, mirrors amznkiller lists/static.txt (GPL-3.0,
     * hxreborn). Updating these rules is a reviewed commit plus a new patch
     * bundle, never a runtime download, so no remote party can restyle or
     * reshape the authenticated Amazon UI.
     */
    private static final String[] AD_SELECTORS = {
        ".a-cardui[class*=\"_new-detail-faceout-box_\"]:has(.dynamicSponsoredLabelClass)",
        ".a-carousel-card:has(.p13n-sc-sponsored-label)",
        ".amzn-safe-frame-container", ".ape-wrapper",
        ".dp-widget-card-deck:has([data-ad-placement-metadata])",
        ".s-result-item.AdHolder",
        ".s-result-item:has([data-ad-feedback])",
        ".s-result-item:has([data-ad-feedback-label-id])",
        ".s-result-item:has(.puis-sponsored-label-text)",
        ".s-result-item:has(div[cel_widget_id^=\"MAIN-FEATURED_ASINS_LIST-\"])",
        ".s-result-list > .a-section:has(.sbv-ad-content-container)",
        ".p13n-sc-unified-ad-label", ".sbv-video-single-product",
        ".sponsored-products-detail-mobile",
        "#detailILM_feature_div",
        "#faceoutContainer:has(> [class$=\"sponsored-label\"])",
        "#mobile-mshop-ad", "#nav-swmslot", "#sc-rec-bottom", "#sc-rec-right",
        "#similarities_feature_div:has(span.sponsored_label_tap_space)",
        "#sponsoredProducts2_feature_div", "#sponsoredProducts_feature_div",
        "#sponsored-proofPoint-section",
        "#typ-recommendations-stripe-1", "#typ-recommendations-stripe-2",
        "#widget-rightUpsellSlot", ".AdsCard_container__K6vmo",
        "[cel_widget_id*=\"-creative-desktop_loom-desktop-\"]",
        "[class*=\"_adFeedbackWrapper_\"]",
        "[data-ad-id]", "[data-component-type^=\"aspa-asin-ajax\"]",
        "[id^=\"ape_\"]", "[id^=\"mobile-dp-ilm\"]",
        "div.s-inner-result-item > div.sg-col-inner:has(a.puis-sponsored-label-text)",
        "div[cel_widget_id*=\"_ad-placements-\"]", "div[cel_widget_id*=\"Deals3Ads\"]",
        "div[cel_widget_id*=\"desktop-dp-\"]",
        "div[cel_widget_id=\"sp-orderdetails-desktop-carousel_desktop-yo-orderdetails_0\"]",
        "div[cel_widget_id=\"sp-orderdetails-mobile-list_mobile-yo-orderdetails_0\"]",
        "div[cel_widget_id=\"sp-pop-mobile-carousel_mobile-yo-postdelivery_0\"]",
        "div[cel_widget_id=\"sp-rhf-desktop-carousel_desktop-rhf_0\"]",
        "div[cel_widget_id=\"sp-shiptrack-desktop-carousel_desktop-yo-shiptrack_0\"]",
        "div[cel_widget_id=\"sp-shiptrack-mobile-list_mobile-yo-shiptrack_0\"]",
        "div[cel_widget_id=\"sp-typ-mobile-carousel_mobile-typ-carousels_2\"]",
        "div[cel_widget_id=\"sp_phone_detail_thematic\"]", "div[cel_widget_id=\"typ-ads\"]",
        "div[cel_widget_id^=\"LEFT-SAFE_FRAME-\"]",
        "div[cel_widget_id^=\"MAIN-FEATURED_ASINS_LIST-\"]",
        "div[cel_widget_id^=\"MAIN-VIDEO_SINGLE_PRODUCT-\"]",
        "div[cel_widget_id^=\"adplacements:\"]",
        "div[cel_widget_id^=\"multi-brand-\"]", "div[cel_widget_id^=\"sb-\"]",
        "div[cel_widget_id^=\"sb-themed-collection\"]",
        "div[cel_widget_id^=\"sp-desktop-carousel_handsfree-browse\"]",
        "div[cel_widget_id^=\"sp-mobile-thematic-bundle\"]",
        "div[class*=\"SponsoredProducts\"]", "div[class*=\"_dpNoOverflow_\"][data-idt]",
        "div[data-a-carousel-options*='\"isSponsoredProduct\":\"true\"']",
        "div[data-cel-widget*=\"-mobile_loom-mobile-inline-slot\"]",
        "div[data-cel-widget=\"sp-rhf-desktop-carousel_desktop-rhf_1\"]",
        "div[data-cel-widget=\"sp-shiptrack-desktop-carousel_desktop-yo-shiptrack_0\"]",
        "div[data-cel-widget^=\"mobile-ads-\"]",
        "div[data-cel-widget^=\"multi-brand-video-mobile_DPSims_\"]",
        "div[data-cel-widget^=\"multi-brand-video-mobile_DetailPage_\"]",
        "div[data-cel-widget^=\"multi-card-creative-desktop_loom-desktop-top-slot_\"]",
        "div[data-cel-widget^=\"sb-\"]", "div[data-cel-widget^=\"sp-mobile-thematic-bundle\"]",
        "div[data-csa-c-painter=\"single-creative-card\"]:has([data-ad-feedback-label-id])",
        "div[data-csa-c-painter=\"single-video-card\"]:has([data-ad-feedback-label-id])",
        "div[data-csa-c-painter=\"sp-cart-mobile-carousel-cards\"]",
        "div[data-csa-c-slot-id^=\"loom-mobile-brand-footer-slot_hsa-id-\"]",
        "div[data-csa-c-slot-id^=\"loom-mobile-top-slot_hsa-id-\"]",
        "li.gwm-window-tile:has(div[data-csa-c-painter=\"single-creative-card\"] [data-ad-feedback-label-id])",
        "li.gwm-window-tile:has(div[data-csa-c-painter=\"single-video-card\"] [data-ad-feedback-label-id])",
        "div[id^=\"sims-simsContainer_feature_div_\"]:has(.sp_info_link)",
        "div[id^=\"sp_detail\"]",
        "span[cel_widget_id^=\"MAIN-FEATURED_ASINS_LIST-\"]",
        "span[cel_widget_id^=\"MAIN-loom-desktop-brand-footer-slot_hsa-id-CARDS-\"]",
        "span[cel_widget_id^=\"MAIN-loom-desktop-top-slot_hsa-id-CARDS-\"]",
        // Frequently bought together
        "#sims-fbt-content",
        "#sims-fbt",
        "div[cel_widget_id*=\"fbt\"]",
        "div[data-cel-widget*=\"fbt\"]",
        "[cel_widget_id^=\"MAIN-sims-fbt\"]",
    };

    private static volatile String adCss;

    /**
     * Builds the hide-only stylesheet from the vendored selectors. A selector is
     * skipped if it could terminate its own rule and open another, so the
     * generated CSS can only ever hide elements.
     */
    private static String adCss() {
        String css = adCss;
        if (css != null) return css;
        StringBuilder sb = new StringBuilder();
        for (String selector : AD_SELECTORS) {
            String s = selector.trim();
            if (s.isEmpty()
                || s.indexOf('{') >= 0 || s.indexOf('}') >= 0
                || s.indexOf(';') >= 0 || s.indexOf('@') >= 0
                || s.contains("/*") || s.contains("*/")) continue;
            sb.append(s).append("{display:none!important}");
        }
        css = sb.toString();
        adCss = css;
        return css;
    }

    /** Applies the vendored ad-hiding stylesheet. Performs no network access. */
    public static void injectAdBlock(WebView webView) {
        if (webView == null) return;
        webView.evaluateJavascript(
            "(function(css){var id='morphe-amazon-ads';"
            + "var s=document.getElementById(id);"
            + "if(!s){s=document.createElement('style');s.id=id;"
            + "(document.head||document.documentElement).appendChild(s);}"
            + "s.textContent=css;})(" + jsonString(adCss()) + ");", null);
    }

    // ---- Video autoplay ----

    /**
     * Stops videos that start without the user. A page's own settings cannot do this: Cordova resets
     * setMediaPlaybackRequiresUserGesture to false and Chromium lets muted videos play on their own anyway, so
     * the page is told instead. The script removes autoplay attributes, pauses any video that starts without a
     * tap, press or key within the last second (a real tap on a video, which comes right before its play event,
     * still plays it), and watches for videos added later. A video the user started is never paused by a later
     * run, and a video that keeps restarting itself is let go after 25 tries so a player cannot spin the page.
     * Running it again on the same page only rescans. The script is a constant: no runtime value enters it.
     */
    private static final String NO_AUTOPLAY_JS =
        "(function(){"
        + "if(window.__lynxNoAutoplay){window.__lynxNoAutoplay();return;}"
        + "var last=0;"
        + "function mark(){last=Date.now();}"
        + "['pointerdown','touchstart','mousedown','keydown','click'].forEach(function(t){"
        + "document.addEventListener(t,mark,true);});"
        + "function recent(){return Date.now()-last<1000;}"
        + "function stop(v){"
        + "if(v.__lynxUser||(v.__lynxStops|0)>=25)return;"
        + "v.__lynxStops=(v.__lynxStops|0)+1;"
        + "try{v.pause();}catch(e){}}"
        + "function strip(v){"
        + "try{if(v.hasAttribute('autoplay'))v.removeAttribute('autoplay');v.autoplay=false;}catch(e){}"
        + "if(!v.paused&&!v.__lynxUser)stop(v);}"
        + "function scan(root){"
        + "var list=root.querySelectorAll?root.querySelectorAll('video'):[];"
        + "for(var i=0;i<list.length;i++)strip(list[i]);}"
        + "document.addEventListener('play',function(e){"
        + "var v=e.target;if(!v||v.tagName!=='VIDEO')return;"
        + "if(recent()){v.__lynxUser=true;return;}"
        + "stop(v);},true);"
        + "new MutationObserver(function(muts){"
        + "for(var i=0;i<muts.length;i++){var m=muts[i];"
        + "if(m.type==='attributes'){if(m.target.tagName==='VIDEO')strip(m.target);continue;}"
        + "for(var j=0;j<m.addedNodes.length;j++){var n=m.addedNodes[j];"
        + "if(n.nodeType!==1)continue;"
        + "if(n.tagName==='VIDEO')strip(n);else scan(n);}}"
        + "}).observe(document.documentElement,"
        + "{childList:true,subtree:true,attributes:true,attributeFilter:['autoplay']});"
        + "window.__lynxNoAutoplay=function(){scan(document);};"
        + "scan(document);"
        + "})();";

    /** Keeps videos in Amazon's pages from starting on their own. Performs no network access. */
    public static void injectNoAutoplay(WebView webView) {
        if (webView == null) return;
        try {
            webView.evaluateJavascript(NO_AUTOPLAY_JS, null);
        } catch (RuntimeException ignored) {
            // A WebView that is being destroyed: there is no page left to stop.
        }
    }

    // ---- Price history card ----

    /** Containers the price history card is inserted after, most specific first. */
    private static final String[] PLACEMENT_TARGETS = {
        "#buyBoxAccordion", "#corePriceDisplay_desktop_feature_div",
        "#corePrice_feature_div", "#unifiedPrice_feature_div",
        "#mobileapp_buybox_feature_div", "#desktop_buybox", "#buybox",
        "#price_feature_div", "#newAccordionRow", "#productOverview_feature_div",
        "#centerCol", "#mobileapp_accordion_feature_div", "#apex_mobile",
        "#apex_desktop", "[id^=corePrice]",
    };

    /**
     * The price history card: a collapsed "Price history" row that opens to a verdict, the lowest, average and
     * highest price, 30 day, 90 day, 1 year and all-time buttons and the CamelCamelCamel chart, in the style of
     * Amazon's own price history (which is not offered in Australia).
     *
     * Rendering it contacts nobody. The numbers are asked for through window.lynxPrice (PriceBridge, which
     * fetches the CamelCamelCamel product page in the background) and the chart image is created, with no
     * referrer, only after the user taps the row, or at once when Load automatically is on. Everything the page
     * receives goes through textContent, and every runtime value arrives as a JSON-encoded argument, never as
     * concatenated JavaScript source. The script is a constant.
     */
    private static final String PRICE_CARD_JS =
        "function(cfg){\n"
        + "var ID='lynx-price-card',STYLE_ID='lynx-price-style';\n"
        + "var ASIN_RE=new RegExp('/(?:dp|gp/product|gp/aw/d)/([A-Za-z0-9]{10})(?![A-Za-z0-9])');\n"
        + "var RANGES=[['1m','30 days'],['3m','90 days'],['1y','1 year'],['all','All']];\n"
        + "var KINDS={low:1,near:1,good:1,high:1,typical:1};\n"
        + "var CSS=[\n"
        + "'#lynx-price-card{box-sizing:border-box;margin:12px;border:1px solid #d5d9d9;border-radius:12px;background:#fff;color:#0f1111;font:14px/1.4 \"Amazon Ember\",Arial,sans-serif;overflow:hidden}',\n"
        + "'#lynx-price-card *{box-sizing:border-box}',\n"
        + "'#lynx-price-card [hidden]{display:none!important}',\n"
        + "'#lynx-price-card .lpc-head{display:flex;align-items:center;width:100%;margin:0;padding:12px 14px;border:0;background:transparent;color:inherit;font:inherit;text-align:left;cursor:pointer}',\n"
        + "'#lynx-price-card .lpc-title{font-weight:700;font-size:15px}',\n"
        + "'#lynx-price-card .lpc-sub{margin-left:8px;font-size:11px;color:#565959;white-space:nowrap}',\n"
        + "'#lynx-price-card .lpc-sum{flex:1;min-width:0;margin-left:10px;font-size:12px;color:#007185;text-align:right;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}',\n"
        + "'#lynx-price-card .lpc-chev{margin-left:auto;padding-left:10px;font-size:20px;line-height:1;color:#565959;transition:transform .15s}',\n"
        + "'#lynx-price-card .lpc-sum:not(:empty)+.lpc-chev{margin-left:8px}',\n"
        + "'#lynx-price-card.open .lpc-chev{transform:rotate(90deg)}',\n"
        + "'#lynx-price-card .lpc-body{padding:0 14px 14px}',\n"
        + "'#lynx-price-card .lpc-body[hidden]{display:none}',\n"
        + "'#lynx-price-card .lpc-verdict{display:flex;align-items:center;margin:2px 0 6px;font-size:15px;font-weight:700}',\n"
        + "'#lynx-price-card .lpc-verdict:before{content:\"\";flex:none;width:10px;height:10px;margin-right:8px;border-radius:50%;background:#565959}',\n"
        + "'#lynx-price-card .lpc-verdict.k-low,#lynx-price-card .lpc-verdict.k-near,#lynx-price-card .lpc-verdict.k-good{color:#067d62}',\n"
        + "'#lynx-price-card .lpc-verdict.k-low:before,#lynx-price-card .lpc-verdict.k-near:before,#lynx-price-card .lpc-verdict.k-good:before{background:#067d62}',\n"
        + "'#lynx-price-card .lpc-verdict.k-high{color:#b12704}',\n"
        + "'#lynx-price-card .lpc-verdict.k-high:before{background:#b12704}',\n"
        + "'#lynx-price-card .lpc-series{margin:0 0 8px;font-size:12px;color:#565959}',\n"
        + "'#lynx-price-card .lpc-tiles{display:flex;margin:0 -4px 12px}',\n"
        + "'#lynx-price-card .lpc-tile{flex:1;min-width:0;margin:0 4px;padding:8px 10px;border-radius:8px;background:#f0f2f2}',\n"
        + "'#lynx-price-card .lpc-tl{font-size:11px;color:#565959}',\n"
        + "'#lynx-price-card .lpc-tv{font-size:16px;font-weight:700;white-space:nowrap}',\n"
        + "'#lynx-price-card .lpc-td{min-height:15px;font-size:11px;color:#565959}',\n"
        + "'#lynx-price-card .lpc-ranges{display:flex;margin:0 -3px 10px}',\n"
        + "'#lynx-price-card .lpc-range{flex:1;margin:0 3px;padding:6px 2px;border:1px solid #adb1b8;border-radius:16px;background:#fff;color:#0f1111;font:inherit;font-size:12px;cursor:pointer}',\n"
        + "'#lynx-price-card .lpc-range.on{border-color:#007185;background:#e3f1f4;color:#007185;font-weight:700}',\n"
        + "'#lynx-price-card .lpc-chart{border-radius:8px;background:#fff;overflow:hidden}',\n"
        + "'#lynx-price-card .lpc-chart img{display:block;width:100%;height:auto}',\n"
        + "'#lynx-price-card .lpc-key{display:flex;flex-wrap:wrap;padding:6px 12px 8px;background:#fff;color:#0f1111;font-size:13px;line-height:1.4}',\n"
        + "'#lynx-price-card .lpc-key span{display:inline-flex;align-items:center;margin:2px 16px 2px 0;white-space:nowrap}',\n"
        + "'#lynx-price-card .lpc-dot{flex:none;width:10px;height:10px;margin-right:6px;border-radius:50%}',\n"
        + "'#lynx-price-card .lpc-dot.d-amazon{background:#63a85e}',\n"
        + "'#lynx-price-card .lpc-dot.d-new{background:#0033cc}',\n"
        + "'#lynx-price-card .lpc-dot.d-used{background:#cc3300}',\n"
        + "'#lynx-price-card .lpc-msg{padding:14px 8px;border-radius:8px;background:#f0f2f2;color:#565959;text-align:center}',\n"
        + "'#lynx-price-card .lpc-links{display:flex;flex-wrap:wrap;margin:10px 0 0;font-size:13px}',\n"
        + "'#lynx-price-card .lpc-links a{margin-right:16px;color:#007185;text-decoration:none}',\n"
        + "'@media (prefers-color-scheme:dark){',\n"
        + "'#lynx-price-card{border-color:#3b434b;background:#1b2025;color:#e7e9ea}',\n"
        + "'#lynx-price-card .lpc-sub,#lynx-price-card .lpc-chev,#lynx-price-card .lpc-series,#lynx-price-card .lpc-tl,#lynx-price-card .lpc-td{color:#9aa4ad}',\n"
        + "'#lynx-price-card .lpc-sum,#lynx-price-card .lpc-links a{color:#4fc3d9}',\n"
        + "'#lynx-price-card .lpc-tile,#lynx-price-card .lpc-msg{background:#262d34}',\n"
        + "'#lynx-price-card .lpc-msg{color:#9aa4ad}',\n"
        + "'#lynx-price-card .lpc-verdict:before{background:#9aa4ad}',\n"
        + "'#lynx-price-card .lpc-verdict.k-low,#lynx-price-card .lpc-verdict.k-near,#lynx-price-card .lpc-verdict.k-good{color:#3ec9a7}',\n"
        + "'#lynx-price-card .lpc-verdict.k-low:before,#lynx-price-card .lpc-verdict.k-near:before,#lynx-price-card .lpc-verdict.k-good:before{background:#3ec9a7}',\n"
        + "'#lynx-price-card .lpc-verdict.k-high{color:#ff8a75}',\n"
        + "'#lynx-price-card .lpc-verdict.k-high:before{background:#ff8a75}',\n"
        + "'#lynx-price-card .lpc-range{border-color:#59636d;background:#262d34;color:#e7e9ea}',\n"
        + "'#lynx-price-card .lpc-range.on{border-color:#4fc3d9;background:#17323a;color:#4fc3d9}',\n"
        + "'}'\n"
        + "].join('');\n"
        + "function el(tag,cls,text){var e=document.createElement(tag);if(cls)e.className=cls;if(text!=null)e.textContent=text;return e;}\n"
        + "function ensureStyle(){\n"
        + "if(document.getElementById(STYLE_ID))return;\n"
        + "var s=el('style');s.id=STYLE_ID;s.textContent=CSS;\n"
        + "(document.head||document.documentElement).appendChild(s);}\n"
        + "function tile(label){\n"
        + "var t=el('div','lpc-tile');t.appendChild(el('div','lpc-tl',label));\n"
        + "t.v=el('div','lpc-tv','–');t.d=el('div','lpc-td','');\n"
        + "t.appendChild(t.v);t.appendChild(t.d);return t;}\n"
        + "function setTile(t,x){t.v.textContent=x&&x.p?x.p:'–';t.d.textContent=x&&x.d?x.d:'';}\n"
        + "function place(box){\n"
        + "for(var i=0;i<cfg.targets.length;i++){\n"
        + "var t=document.querySelector(cfg.targets[i]);\n"
        + "if(t&&t.parentNode){t.parentNode.insertBefore(box,t.nextSibling);return true;}}\n"
        + "return false;}\n"
        + "function build(asin){\n"
        + "var old=document.getElementById(ID);\n"
        + "if(old&&old.getAttribute('data-asin')===asin)return;\n"
        + "if(old)old.remove();\n"
        + "ensureStyle();\n"
        + "var st={asin:asin,range:cfg.range,loaded:false,status:'idle',data:null,chartFailed:false};\n"
        + "var card=el('div','lpc');card.id=ID;card.setAttribute('data-asin',asin);\n"
        + "var head=el('button','lpc-head');head.type='button';head.setAttribute('aria-expanded','false');\n"
        + "var sum=el('span','lpc-sum','');\n"
        + "head.appendChild(el('span','lpc-title','Price history'));\n"
        + "head.appendChild(el('span','lpc-sub','CamelCamelCamel'));\n"
        + "head.appendChild(sum);\n"
        + "head.appendChild(el('span','lpc-chev','›'));\n"
        + "var body=el('div','lpc-body');body.hidden=true;\n"
        + "var verdict=el('div','lpc-verdict');verdict.hidden=true;\n"
        + "var series=el('div','lpc-series');series.hidden=true;\n"
        + "var tiles=el('div','lpc-tiles');tiles.hidden=true;\n"
        + "var tl=tile('Lowest'),ta=tile('Average'),th=tile('Highest');\n"
        + "tiles.appendChild(tl);tiles.appendChild(ta);tiles.appendChild(th);\n"
        + "var ranges=el('div','lpc-ranges');ranges.setAttribute('role','group');\n"
        + "var buttons=[];\n"
        + "RANGES.forEach(function(r){\n"
        + "var b=el('button','lpc-range',r[1]);b.type='button';\n"
        + "b.onclick=function(){st.range=r[0];mark();if(st.loaded)loadChart();};\n"
        + "buttons.push([r[0],b]);ranges.appendChild(b);});\n"
        + "var chart=el('div','lpc-chart');\n"
        + "var img=el('img');img.alt='Price history chart';img.referrerPolicy='no-referrer';\n"
        + "chart.appendChild(img);\n"
        + "var key=el('div','lpc-key');\n"
        + "[['amazon','Amazon'],['new','3rd party new'],['used','3rd party used']].forEach(function(k){\n"
        + "var s=el('span');s.appendChild(el('i','lpc-dot d-'+k[0]));s.appendChild(document.createTextNode(k[1]));key.appendChild(s);});\n"
        + "chart.appendChild(key);\n"
        + "var msg=el('div','lpc-msg','');msg.hidden=true;\n"
        + "var links=el('div','lpc-links');\n"
        + "function link(label,which,href){\n"
        + "var a=el('a',null,label);a.href=href;a.target='_blank';a.rel='noopener noreferrer';\n"
        + "a.onclick=function(e){\n"
        + "var b=window.lynxPrice;\n"
        + "if(b&&typeof b.open==='function'){e.preventDefault();try{b.open(which,st.asin,cfg.code);}catch(x){}}};\n"
        + "return a;}\n"
        + "links.appendChild(link('View on CamelCamelCamel','ccc',cfg.cccLink+asin));\n"
        + "if(cfg.keepaLink)links.appendChild(link('Also on Keepa','keepa',cfg.keepaLink+asin));\n"
        + "body.appendChild(verdict);body.appendChild(series);body.appendChild(tiles);\n"
        + "body.appendChild(ranges);body.appendChild(chart);body.appendChild(msg);body.appendChild(links);\n"
        + "card.appendChild(head);card.appendChild(body);\n"
        + "function mark(){buttons.forEach(function(p){\n"
        + "var on=p[0]===st.range;p[1].className=on?'lpc-range on':'lpc-range';\n"
        + "p[1].setAttribute('aria-pressed',on?'true':'false');});}\n"
        + "function render(){\n"
        + "var none=st.status==='none';\n"
        + "var ok=st.status==='ok'&&st.data;\n"
        + "var kind=ok&&Object.prototype.hasOwnProperty.call(KINDS,st.data.kind)?st.data.kind:null;\n"
        + "verdict.hidden=!(ok&&kind&&st.data.verdict);\n"
        + "series.hidden=!ok;tiles.hidden=!ok;\n"
        + "if(ok){\n"
        + "verdict.className='lpc-verdict k-'+kind;verdict.textContent=st.data.verdict||'';\n"
        + "series.textContent='Prices for '+(st.data.label||'Amazon');\n"
        + "setTile(tl,st.data.lowest);setTile(ta,st.data.average);setTile(th,st.data.highest);}\n"
        + "sum.textContent=ok&&kind&&st.data.verdict?st.data.verdict:'';\n"
        + "ranges.hidden=none;\n"
        + "chart.hidden=none||st.chartFailed;\n"
        + "msg.hidden=!(none||st.chartFailed);\n"
        + "msg.style.cursor=none?'default':'pointer';\n"
        + "msg.textContent=none?'No price history for this product yet':\"Couldn't load price history. Tap to retry.\";}\n"
        + "function chartSrc(){\n"
        + "var css=card.getBoundingClientRect().width||(window.innerWidth-24);\n"
        + "var w=Math.max(300,Math.min(800,Math.round(css)));\n"
        + "var h=Math.round(w*0.6);\n"
        + "return cfg.chartBase+st.asin+'/amazon-new-used.png?force=1&legend=0&w='+w+'&h='+h+'&tp='+st.range;}\n"
        + "function loadChart(){\n"
        + "st.chartFailed=false;\n"
        + "img.onerror=function(){st.chartFailed=true;render();};\n"
        + "img.onload=function(){st.chartFailed=false;render();};\n"
        + "img.src=chartSrc();render();}\n"
        + "function requestStats(){\n"
        + "var b=window.lynxPrice;\n"
        + "if(!b||typeof b.request!=='function'){st.status='chart';render();return;}\n"
        + "st.status='loading';render();\n"
        + "try{b.request(st.asin,cfg.code);}catch(e){st.status='chart';render();return;}\n"
        + "setTimeout(function(){if(st.status==='loading'){st.status='chart';render();}},15000);}\n"
        + "function load(){st.loaded=true;loadChart();requestStats();}\n"
        + "st.onData=function(p){\n"
        + "if(!p||p.asin!==st.asin||p.code!==cfg.code)return;\n"
        + "if(p.status==='ok'){st.status='ok';st.data=p;}\n"
        + "else if(p.status==='none')st.status='none';\n"
        + "else st.status='chart';\n"
        + "render();};\n"
        + "msg.onclick=function(){if(st.chartFailed){loadChart();if(st.status!=='ok')requestStats();}};\n"
        + "head.onclick=function(){\n"
        + "var open=body.hidden;body.hidden=!open;\n"
        + "head.setAttribute('aria-expanded',open?'true':'false');\n"
        + "card.className=open?'lpc open':'lpc';\n"
        + "if(open&&!st.loaded)load();};\n"
        + "card.__lynx=st;\n"
        + "mark();render();\n"
        + "if(!place(card)){\n"
        + "var fb=document.getElementById('dp')||document.getElementById('ppd');\n"
        + "if(fb)fb.appendChild(card);\n"
        + "else{var obs=new MutationObserver(function(_,o){if(place(card))o.disconnect();});\n"
        + "obs.observe(document.body||document.documentElement,{childList:true,subtree:true});\n"
        + "setTimeout(function(){obs.disconnect();if(!card.parentNode)document.body.appendChild(card);},10000);}}\n"
        + "if(cfg.auto)load();}\n"
        + "window.__lynxPriceData=function(p){\n"
        + "var c=document.getElementById(ID);\n"
        + "if(c&&c.__lynx)c.__lynx.onData(p);};\n"
        + "function currentAsin(){var m=window.location.pathname.match(ASIN_RE);return m?m[1].toUpperCase():null;}\n"
        + "build(currentAsin()||cfg.asin);\n"
        + "if(!window.__lynxPriceNav){window.__lynxPriceNav=true;\n"
        + "var onNav=function(){var a=currentAsin();if(a)build(a);};\n"
        + "var ps=history.pushState,rs=history.replaceState;\n"
        + "history.pushState=function(){ps.apply(this,arguments);setTimeout(onNav,0);};\n"
        + "history.replaceState=function(){rs.apply(this,arguments);setTimeout(onNav,0);};\n"
        + "window.addEventListener('popstate',onNav);}\n"
        + "}\n";

    /**
     * Adds the price history card to a product page. Does nothing for a page that is not an Amazon product page,
     * for a marketplace CamelCamelCamel does not serve, or when the card is switched off in the Amazon page of the
     * Lynx Patches screen.
     */
    public static void injectPriceCard(WebView webView, String url) {
        if (webView == null) return;
        try {
            if (!AmazonSettings.priceCard(webView.getContext())) return;

            // Both trust and product identity come from the parsed URL.
            String asin = AmazonUrls.productAsin(url);
            if (asin == null) return;
            String code = AmazonUrls.marketplaceCode(AmazonUrls.webHost(url));
            if (!PriceLinks.cccSupports(code)) return;

            // Created per WebView at construction; this covers a WebView that was not, for the next page it loads.
            PriceBridge.attach(webView);

            boolean keepa = PriceLinks.keepaSupports(code);
            StringBuilder cfg = new StringBuilder("{");
            cfg.append("\"asin\":").append(jsonString(asin));
            cfg.append(",\"code\":").append(jsonString(code));
            cfg.append(",\"auto\":").append(AmazonSettings.priceAuto(webView.getContext()));
            cfg.append(",\"range\":").append(jsonString(AmazonSettings.priceRange(webView.getContext())));
            cfg.append(",\"targets\":").append(AmazonJson.array(PLACEMENT_TARGETS));
            cfg.append(",\"chartBase\":").append(jsonString(PriceLinks.chartBase(code)));
            cfg.append(",\"cccLink\":").append(jsonString("https://" + PriceLinks.cccHost(code) + "/product/"));
            cfg.append(",\"keepaLink\":").append(keepa ? jsonString("https://keepa.com/#!product/") : "null");
            cfg.append("}");

            webView.evaluateJavascript("(" + PRICE_CARD_JS + ")(" + cfg + ");", null);
        } catch (RuntimeException ignored) {
            // A WebView that is being destroyed: there is no page left to add the card to.
        }
    }

    /** Adds window.lynxPrice to a WebView as it is created. See PriceBridge. */
    public static void attachPriceBridge(WebView webView) {
        PriceBridge.attach(webView);
    }

    // ---- Dark mode (inspired by amznkiller dark_mode.js, GPL-3.0, hxreborn) ----

    /**
     * Page fixes for while the phone is in dark mode (mirrors amznkiller dark_mode.js, GPL-3.0, hxreborn, minus its
     * forced page background). Android's Force Dark does the darkening: it turns the page background and text
     * dark by itself, so no background colour is set here (a fixed dark background under dark text is unreadable).
     * What it leaves are the details Force Dark gets wrong: product photos that Amazon blends into the page with
     * mix-blend-mode, and the yellow buttons, which stay light with dark text.
     */
    private static final String DARK_FIX_CSS =
        "[class*=image-container] img{mix-blend-mode:normal!important}"
        + "img[style*=\"mix-blend-mode\"]{mix-blend-mode:normal!important}"
        + "[class*=asin-metadata]{mix-blend-mode:normal!important}"
        + "[class*=asin-container],[class*=asin-image-wrapper]{background-color:white!important}"
        + "[class*=badgeMessage]{background-color:transparent!important}"
        + ".a-button-primary,.a-button-oneclick{color-scheme:only light!important}";

    /** True while the phone (the view's configuration) is in dark mode, which is when Force Dark is active. */
    private static boolean isSystemDark(android.view.View view) {
        if (view == null) return false;
        int uiMode = view.getResources().getConfiguration().uiMode;
        return (uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Applies the vendored dark-mode page fixes while the phone is in dark mode, and does nothing in light mode.
     * Performs no network access, so it works offline and cannot be altered by any external repository.
     */
    public static void injectDarkMode(WebView webView) {
        if (webView == null || !isSystemDark(webView)) return;
        try {
            webView.evaluateJavascript(
                "(function(css){var id='morphe-amazon-dark';"
                + "var s=document.getElementById(id);"
                + "if(!s){s=document.createElement('style');s.id=id;"
                + "(document.head||document.documentElement).appendChild(s);}"
                + "s.textContent=css;})(" + jsonString(DARK_FIX_CSS) + ");", null);
        } catch (RuntimeException ignored) {
            // A WebView that is being destroyed: there is no page left to fix.
        }
    }

    // ---- External navigation ----

    private static final AmazonLinkPolicy LINK_POLICY = new AmazonLinkPolicy();

    /**
     * Decides whether a WebView navigation must leave the Amazon app, returning
     * true when the destination was handed to the external browser and the
     * navigation should be reported as handled. Returning false lets Amazon
     * handle the link as it always does.
     *
     * Only an http(s) link whose parsed host is not Amazon's is opened in the
     * browser (see AmazonLinkPolicy). Trust comes from the host, so
     * destinations that merely contain the text "amazon." outside the host
     * (evilamazon.com, amazon.com.evil.example, https://amazon.com@evil.example/,
     * https://evil.example/?next=amazon.com) are external. Amazon's own app
     * links (amznapp://, appnav://), intent:, tel:, mailto: and market: links,
     * its short links and any URL without a readable host stay with Amazon,
     * and so does everything while a checkout, payment or sign-in flow is
     * running. Only http(s) URLs are ever handed to another app, so this can
     * never launch an intent:, file: or content: target.
     */
    public static boolean openExternally(WebView webView, String url) {
        if (webView == null || url == null) return false;
        String current = null;
        try {
            current = webView.getUrl();
        } catch (RuntimeException ignored) {
            // A WebView being destroyed: judge the link on its own.
        }
        if (!LINK_POLICY.opensInBrowser(url, current, System.currentTimeMillis())) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            webView.getContext().startActivity(intent);
        } catch (Exception e) {
            // No external handler: still refuse to render a foreign site
            // inside the authenticated Amazon WebView.
        }
        return true;
    }

    // ---- Sanitize share URL ----

    /** Strips Amazon tracking parameters, returning a canonical /dp/ASIN URL. */
    public static String sanitizeAmazonUrl(String url) {
        String canonical = AmazonUrls.canonicalProductUrl(url);
        return canonical != null ? canonical : url;
    }

    // ---- Tab bar icon tint ----

    /**
     * Called with the ImageView returned by BaseTabController.getTabIcon() so
     * the icon stays visible against the dark bar while the phone is in dark mode.
     */
    public static void tintTabIconIfDark(android.widget.ImageView icon) {
        if (icon == null || !isSystemDark(icon)) return;
        icon.setColorFilter(
            android.graphics.Color.WHITE,
            android.graphics.PorterDuff.Mode.SRC_IN
        );
    }

    // ---- Encoding helpers ----

    /** JSON-encodes a value for embedding in generated JavaScript. See AmazonJson. */
    private static String jsonString(String value) {
        return AmazonJson.string(value);
    }
}
