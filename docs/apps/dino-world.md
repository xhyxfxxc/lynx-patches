<!-- HEADER_START -->
# <img src="../../assets/icons/kr.co.smartstudy.dinoworld_android_googlemarket.png" width="36" height="36" align="top" alt=""> Dino World

![version](https://img.shields.io/badge/version-36.05-2563eb?style=for-the-badge&labelColor=0d1117) ![patches](https://img.shields.io/badge/patches-1-14b8a6?style=for-the-badge&labelColor=0d1117) ![file](https://img.shields.io/badge/file-XAPK-0ea5e9?style=for-the-badge&labelColor=0d1117) ![menu](https://img.shields.io/badge/menu-Home%20screen%20shortcut-6e7681?style=for-the-badge&labelColor=0d1117)

[← All apps](../../README.md#apps)&nbsp;&nbsp;•&nbsp;&nbsp;`kr.co.smartstudy.dinoworld_android_googlemarket`&nbsp;&nbsp;•&nbsp;&nbsp;[v3.8.0](https://github.com/xhyxfxxc/lynx-patches/releases/tag/v3.8.0)

**🎯 Supported versions:** `36.05`
<!-- HEADER_END -->

## Features

| Feature | What you get |
|---|---|
| 🚫 **No ads** | Banners, full-screen ads and app-open ads never load. The game is told, like any ad network would tell it, that there is no ad to show. |

The patches only touch ads: billing and the Pinkfong membership (the paid subscription) are left exactly as they were, and nothing paid is unlocked.

**Where Lynx Patches lives:** the game has no settings page that a patch can add a row to. Long press the Dino World icon on your home screen and choose Lynx Patches.

## How-to

### Patch the game

1. Download the **XAPK** of version 36.05 (the Google Play release: the base APK plus its splits).
2. Patch it with Morphe and keep **Hide ads (Pinkfong)** ticked (on by default).
3. Install the result.

## What to expect

- **Ads are blocked before they are requested.** Each kind of full-screen ad is loaded by one call in Google's ad code, and banners by the game's ad view. Every one of those is answered with "no ad available" straight away (or simply not started), so no request reaches Google AdMob and the ad networks behind it are never asked either.
- **There is no reward video in this game.** If a later version adds a "watch a video for a bonus" button, it finds no ad and gives no bonus: this patch only removes ads.
- **The More apps button stays.** It is Pinkfong's own list of its games, not an ad network, so the patch leaves it as it is.
- **A game update can break it.** The patches are written for version 36.05. Another version is offered as experimental and is checked when you patch.

## FAQ

**Does this unlock the Pinkfong membership or paid content?**
No. Billing and the membership are not touched. The patches remove ads only.

**Which file do I download?**
The **XAPK** for version 36.05.

## Patches

<!-- PATCHES_START -->
| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|---|---|---|
| **Hide ads (Pinkfong)** | Removes the ads of Pinkfong and Baby Shark kids games (Google AdMob with Unity Ads and Liftoff): banners, full-screen ads and app-open ads never load. |  |
<!-- PATCHES_END -->
