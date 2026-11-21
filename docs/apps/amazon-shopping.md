<!-- HEADER_START -->
# <img src="../../assets/icons/com.amazon.mShop.android.shopping.png" width="36" height="36" align="top" alt=""> Amazon Shopping

![version](https://img.shields.io/badge/version-32.20.2.100-2563eb?style=for-the-badge&labelColor=0d1117) ![patches](https://img.shields.io/badge/patches-8-14b8a6?style=for-the-badge&labelColor=0d1117) ![file](https://img.shields.io/badge/file-APKM%20%28Android%2012%2B%29-0ea5e9?style=for-the-badge&labelColor=0d1117) ![menu](https://img.shields.io/badge/menu-Home%20screen%20shortcut-6e7681?style=for-the-badge&labelColor=0d1117)

[← All apps](../../README.md#apps)&nbsp;&nbsp;•&nbsp;&nbsp;`com.amazon.mShop.android.shopping`&nbsp;&nbsp;•&nbsp;&nbsp;[v3.4.1](https://github.com/xhyxfxxc/lynx-patches/releases/tag/v3.4.1)

**🎯 Supported versions:** `32.19.0.100` · `32.20.2.100`
<!-- HEADER_END -->

## Features

| Feature | What you get |
|---|---|
| 🚫 **Ad-free pages** | Sponsored products, brand banners and video ads are hidden on search, product, cart and order pages. |
| 📈 **Price history** | A collapsed "Price History" box under the price on product pages. Tap **Load price history** to see the Keepa and CamelCamelCamel charts, with buttons for 1 month up to all time. |
| 🎨 **Dark mode** | Amazon's pages and bottom tab icons follow your phone's dark theme. Manager lets you pick Off, Follow system or Always on. |
| ▶️ **No video autoplay** | Videos in Amazon's in-app pages wait until you tap them. |
| 🛡️ **Quieter search** | Search keypress and focus events are no longer sent along with suggestion requests. |
| 🔗 **Links open in your browser** | Links to websites outside Amazon open in your default browser instead of inside the app. Amazon's own app links, phone and email links, and everything during checkout, payment and sign-in stay in the app. |
| 🧩 **Alexa for Shopping tab** | Off by default. Tick **Hide Alexa for Shopping tab** in Manager to remove the tab from the bottom bar. |

**Where Lynx Patches lives:** long press the Amazon icon on your home screen and choose Lynx Patches. Amazon's own menus are web pages, so there is no row inside the app. In the menu, **General → Show in the app drawer** gives it an icon of its own.

## What to expect

- **Price history is never loaded on its own.** Nothing is requested from Keepa or CamelCamelCamel until you tap **Load price history**. Loading it requests the chart for that one product straight from those sites, so they see the product's ASIN (Amazon's product id) and your IP address, but no Amazon cookies, account details or page content.
- **Australia:** the global Amazon Shopping app is the one Amazon Australia uses. Keepa has no Australian prices, so there the box shows the CamelCamelCamel chart only.
- **Ad hiding is a list of page rules.** It is built into the patch and never downloaded, so a change in Amazon's page layout can let an ad through until the patch is updated.
- **Other Amazon apps** (Prime Video, Alexa and so on) can be installed beside it: the patch renames the permissions that Amazon's apps share, so they do not clash.
- **A second copy beside the Amazon app:** turn on **Change package name** (off by default) to install the patched app next to the Amazon app from the Play Store. **Change package name support (Amazon)** renames Amazon's sign-in and file-sharing providers for you, so Android accepts the install. The copy is a separate app, so you sign in to it again.

## Questions

**Does the Amazon app look different?**
Only where a patch says so: hidden ads, the price history box, dark pages and, if you ticked it, no Alexa for Shopping tab. Everything else is Amazon's own app.

**A step that goes through another website (a bank or payment page, for example) does not open in my browser. Is that on purpose?**
Yes. While you check out, pay or sign in, every page stays inside the app, including PayPal, Afterpay, Zip and your bank's confirmation page. The app goes back to opening outside links in your browser as soon as you are on an ordinary Amazon page again.

**Why is the Alexa for Shopping tab still there?**
Hiding it is optional. Tick **Hide Alexa for Shopping tab** in Manager and patch again. Amazon used to call this assistant Rufus.

## Patches

<!-- PATCHES_START -->
| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|---|---|---|
| **Dark mode** | Force dark mode for Amazon Shopping. | • Dark mode |
| **Disable search suggestions tracking** | Prevents search keypress and focus events from being sent with suggestion requests. |  |
| **Disable video autoplay** | Prevents videos from autoplaying in Amazon's in-app WebView pages. |  |
| **Fix Amazon manifest conflicts** | Updates shared Amazon permissions so other Amazon apps can coexist. |  |
| **Hide Alexa for Shopping tab** | Removes the Alexa for Shopping (formerly Rufus) tab from the bottom navigation bar. |  |
| **Open links in browser** | Opens non-Amazon URLs in the default browser instead of the in-app WebView. |  |
| **Price history charts** | Injects Keepa and CamelCamelCamel price history charts on Amazon product pages. | • Chart period<br>• Show period toggle<br>• Toggle periods<br>• Keepa price lines<br>• CamelCamelCamel chart<br>• Hide zero-price gaps<br>• Chart size<br>• Start collapsed |
| **Remove ads** | Hides sponsored and ad content in Amazon Shopping via CSS injection. |  |
<!-- PATCHES_END -->


## Credits

The Amazon Shopping patches were ported from [morphe-patches](https://github.com/rushiranpise/morphe-patches) by rushiranpise (GPL-3.0). The ad-blocking selectors, the dark-mode CSS and the WebView injection approach come from [amznkiller](https://github.com/hxreborn/amznkiller) by hxreborn (GPL-3.0). They were modified for Lynx Patches on 2026-11-21; see [NOTICE](../../NOTICE).
