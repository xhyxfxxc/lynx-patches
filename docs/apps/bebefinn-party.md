<!-- HEADER_START -->
# <img src="../../assets/icons/kr.co.smartstudy.bebefinnparty_android_googlemarket.png" width="36" height="36" align="top" alt=""> Bebefinn Party

![version](https://img.shields.io/badge/version-1.8-2563eb?style=for-the-badge&labelColor=0d1117) ![patches](https://img.shields.io/badge/patches-1-14b8a6?style=for-the-badge&labelColor=0d1117) ![file](https://img.shields.io/badge/file-XAPK-0ea5e9?style=for-the-badge&labelColor=0d1117) ![menu](https://img.shields.io/badge/menu-Home%20screen%20shortcut-6e7681?style=for-the-badge&labelColor=0d1117)

[← All apps](../../README.md#apps)&nbsp;&nbsp;•&nbsp;&nbsp;`kr.co.smartstudy.bebefinnparty_android_googlemarket`&nbsp;&nbsp;•&nbsp;&nbsp;[v3.6.1](https://github.com/xhyxfxxc/lynx-patches/releases/tag/v3.6.1)

**🎯 Supported versions:** `1.8`
<!-- HEADER_END -->

## Features

| Feature | What you get |
|---|---|
| 🚫 **No ads** | Banners, full-screen ads, rewarded ads and app-open ads never load. The game is told, like any ad network would tell it, that there is no ad to show. |

Bebefinn Party is called **Birthday Party** in English on Google Play. The patch only touches ads: billing and the Pinkfong membership (the paid subscription) are left exactly as they were, and nothing paid is unlocked.

**Where Lynx Patches lives:** the game has no settings page that a patch can add a row to. Long press the Bebefinn Party icon on your home screen and choose Lynx Patches.

## How-to

### Patch the game

1. Download the **XAPK** of version 1.8 (the Google Play release: the base APK plus the `arm64-v8a` split).
2. Patch it with Morphe and keep **Hide ads (Bebefinn Party)** ticked (it is on by default).
3. Install the result.

## What to expect

- **Ads are blocked before they are requested.** Every ad load goes through one place in the game's Google ad code. It is answered with "no ad available" straight away, so the request never reaches Google AdMob and the ad networks behind it (Unity Ads and Liftoff) are never asked either.
- **Anything that depends on an ad stays empty.** With no ad ever loading, a "watch an ad for a bonus" button has nothing to play and gives no bonus.
- **A game update can break it.** The patch is written for version 1.8. Another version is offered as experimental and is checked when you patch.

## FAQ

**Does this unlock the Pinkfong membership or paid content?**
No. Billing and the membership are not touched. The patch removes ads only.

**Which file do I download?**
The **XAPK** for version 1.8.

## Patches

<!-- PATCHES_START -->
| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|---|---|---|
| **Hide ads (Bebefinn Party)** | Removes Bebefinn Party's ads (Google AdMob with Unity Ads and Liftoff): banners, full-screen ads and app-open ads never load. |  |
<!-- PATCHES_END -->
