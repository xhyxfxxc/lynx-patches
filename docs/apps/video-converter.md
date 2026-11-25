<!-- HEADER_START -->
# <img src="../../assets/icons/com.inverseai.video_converter.png" width="36" height="36" align="top" alt=""> Video Converter

![version](https://img.shields.io/badge/version-20.11.0-2563eb?style=for-the-badge&labelColor=0d1117) ![patches](https://img.shields.io/badge/patches-2-14b8a6?style=for-the-badge&labelColor=0d1117) ![file](https://img.shields.io/badge/file-XAPK-0ea5e9?style=for-the-badge&labelColor=0d1117) ![menu](https://img.shields.io/badge/menu-Home%20screen%20shortcut-6e7681?style=for-the-badge&labelColor=0d1117)

[← All apps](../../README.md#apps)&nbsp;&nbsp;•&nbsp;&nbsp;`com.inverseai.video_converter`&nbsp;&nbsp;•&nbsp;&nbsp;[v3.9.0](https://github.com/xhyxfxxc/lynx-patches/releases/tag/v3.9.0)

**🎯 Supported versions:** `20.11.0`
<!-- HEADER_END -->

## Features

| Feature | What you get |
|---|---|
| 🚫 **No ads** | No banners, native ads, full-screen ads or app-open ads, from Google AdMob or from InverseAI's own cross-promotion. The strips, gray bars and "Remove ad" buttons they used to leave are gone too. |
| ⏩ **"Watch an ad" unlocks work at once** | Where the app offers "Unlock free / Watch ad" for a premium format, a batch or a merge, the unlock is given straight away and no ad is requested or played. |

Video Converter is also the home of the Noise Reducer screens, the Video Merger and the AI assistant (IVA), so the patch covers all of them. The patches only touch ads: billing, the subscription and every paid feature are left exactly as they were.

**Where Lynx Patches lives:** the app has no settings page that a patch can add a row to. Long press the Video Converter icon on your home screen and choose Lynx Patches.

## How-to

### Patch the app

1. Download the **XAPK** of version 20.11.0 (the Google Play release: the base APK plus the `arm64-v8a` and screen-density splits).
2. Patch it with Morphe and keep **Hide ads (Video Converter)** and **Skip rewarded ads (Video Converter)** ticked (both are on by default).
3. Install the result. It is signed with your own Morphe key, so the Google Play version has to be uninstalled first.

## What to expect

- **Ads are stopped before they are requested.** The app reads its ad switches from a remote configuration; the patch forces the ads-only ones off, which is the path the app itself takes when ads are switched off for a user. Google's ad code is stopped as well, so no request reaches Google AdMob.
- **No empty spaces.** The banner and native slots of every tool screen, the processing screen and the Noise Reducer home list are collapsed, so the screens look like they do for a premium user.
- **"Watch an ad" gives the unlock at once.** The app already skips the ad when your first process is free; the patch uses that same step for every "watch an ad" button, without using up the free-process counter. It works with or without **Hide ads**.
- **The AI assistant's credits are different.** The credits you earn there are granted by InverseAI's server after it has checked the ad, so they cannot be given without one. The "watch an ad for credits" button ends at once with no ad and no credits (the patch answers "not earned" for it). In the Noise Reducer's balance dialog the "Watch ad" button is hidden for the same reason.
- **Purchases do not work in a patched app.** The patched app is signed with a different key than the Google Play version, so Google Play Billing refuses it. This is expected: nothing paid is unlocked, only the one-shot "watch an ad" unlocks above.
- **A game update can break it.** The patches are written for version 20.11.0. Another version is offered as experimental and is checked when you patch: a missing ad switch makes Hide ads fail with a message instead of patching nothing.

## FAQ

**Which file do I download?**
The **XAPK** for version 20.11.0.

**Does this unlock premium?**
No. The subscription, billing and the premium state are not touched. A "watch an ad" button gives the same one-shot unlock it gives after a real ad (one more file, a premium format, a merge), and nothing more.

**Why can I not earn AI credits by watching an ad?**
Those credits live on InverseAI's server, which gives them only for an ad it has verified. The patch does not play ads, so the button ends at once without credits.

**Do purchases work?**
No. A re-signed app cannot use Google Play Billing, in either direction.

**What was tested?**
On an Android 15 emulator without a Google account, against the original app: the home screen, the media picker, the Video Converter, Video To Audio, Audio Converter and Video Merger screens, the processing screen, a premium format ("Watch ad" went on at once with no ad), and an idle screen for three minutes (no ad request at all, where the original made dozens in its first minutes). The emulator could not finish a conversion, in the original app as well (its media library fails to read the file there), so a finished conversion was not tested. Not tested: the AI assistant's credits and the Noise Reducer's balance dialog (they need an InverseAI account), and the Audio Cutter, Audio Merger and Video Cutter screens.

## Patches

<!-- PATCHES_START -->
| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|---|---|---|
| **Hide ads (Video Converter)** | Removes Video Converter's ads: banners, native, full-screen and app-open ads from Google AdMob and InverseAI's own cross-promotion ads, including the Noise Reducer screens. Billing and premium are untouched. |  |
| **Skip rewarded ads (Video Converter)** | Gives the unlock of a "watch an ad" button straight away, without playing an ad. The AI assistant's credits (earned on InverseAI's server) and the Noise Reducer's "Watch ad" button are not unlocked this way. |  |
<!-- PATCHES_END -->
