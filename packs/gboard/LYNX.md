# Gboard patches (Lynx build)

Status: prepared, not published (2026-11-29). Gboard cannot be start-up tested on the PC's emulator; the owner decides.

## What this is

Morphe patches for Gboard (Google's keyboard, `com.google.android.inputmethod.latin`), version
18.5.4.993608717-release-arm64-v8a. Upstream: "Gboard-patches" 4.5.0 by jasonwu1994
(https://github.com/jasonwu1994/Gboard-patches), commit aa6cb2b. This build is 4.5.0-lynx.1.
A "patch" is a change applied on the PC to a copy of the app; you install the result yourself.
Gboard sees every key you press, so what these patches do matters.

## Why it is here

A reviewed copy of the source, built from this folder by Lynx CI instead of using the author's ready-made
file. The original code is GPL-3.0; the changes below are ours.

## What Lynx changed

1. "Web Clipboard" is off by default (`GboardPatchRegistry.kt`). It runs a web server inside the keyboard that
   other devices on the network can reach. The review found: it listens on every network interface, the 4 digit
   pairing code has no limit on wrong guesses and a status page tells a guesser when the code is right, and its
   HTTPS key is the same on every install. The code is still there: switch the patch on yourself if you want it.
2. "FTP Server" is off by default (`GboardPatchRegistry.kt`). When switched on its defaults are anonymous login,
   write access to all shared storage, over plain (unencrypted) FTP, and its patch adds the "all files access"
   permission. The code is still there: switch the patch on yourself if you want it.
   With 1 and 2 off, the patched keyboard has no such service and none of the extra permissions.
3. "Add Gboard Signature Bypass" is narrower (`GboardSignatureBypassBytecodePatch.kt`, tests
   `GboardSignatureBypassTransformationTest.kt` and `GboardSignatureBypassPatchContractTest.kt`). Gboard checks
   that an app is signed by Google; a re-signed Gboard fails that check at start-up, so a bypass is needed.
   Upstream made the check say "yes" for every app. But Gboard also has an open "debug bridge" that lets another
   app in when it is NAMED like a Google tool and passes the same check, so any look-alike app got through.
   Now the check says "yes" only when the app asked about is Gboard itself (its own package name, which also
   works with "Package Rename"). For every other app the original check runs unchanged.
4. Identity: bundle name "Gboard patches (Lynx build)", source, website and contact point to the Lynx Patches
   repo, author "Jason Wu, modified by xhyxfxxc" (`patches/build.gradle.kts`); `NOTICE` has a Lynx section on
   top, the original notices are unchanged. The author links in the app's credits still point to the author.
5. `patches-list.json` was regenerated (version, the two defaults, the changed descriptions).
6. Tests: the two upstream tests that pin the defaults of Web Clipboard and FTP Server
   (`GboardWebClipboardPatchRegistrySourceTest.kt`, `GboardLanFtpServerPatchContractTest.kt`) now expect "off" and
   the "Lynx:" sentence. One new test, `LynxWebClipboardCaptureScopeTest.kt`, pins that the Web Clipboard capture
   code (the hook that copies clipboard items to the Web Clipboard service) comes only with the Web Clipboard
   patch, so with that patch off none of it is installed.

The descriptions of patches 1 to 3 carry a "Lynx:" sentence so you can see this in Morphe Manager.

## What is new in 4.5.0 (upstream)

- The patches' settings page comes in 13 more languages.
- 9 new toolbar editing buttons; all are off by default.
- An optional "Select All" in the clipboard edit mode; off by default.
- Backup and restore of Gboard's own personal data as a ZIP file; you choose where it is saved and the app never
  sends it anywhere.
- Web Clipboard also syncs images and screenshots (only matters if you switch Web Clipboard on).
- A fix for the Frosted Glass look.

## What was reviewed and what was not

The first review (2026-11-23, version 4.4.0) of the patches, the code that runs inside the keyboard and the
build files found no hidden data sending and no call-home. The upgrade to 4.5.0 got a three-part static review
on 2026-11-29 (the patches, the code that runs inside the keyboard, the tests and root files) of everything that
changed: again no hidden data sending, no call-home, no new web address, no new permission and no new component.
It is a reading of the code, not a test on a phone. Not read line by line: the minified web app of Web Clipboard
(a prebuilt page, only used when that patch is on) and the React library code inside it. Checked with tools
only: the 13 new translation files (no links, no HTML or script, placeholders match). Nothing was changed beyond
the list above.

## Known problems that are not fixed

- "Package Rename" is on by default. The patched keyboard installs under another package name next to the
  stock Gboard, and some banking apps treat it as a third-party keyboard (upstream issue 22).
- The JavaScript row feature (swipeable custom top row) ships a prebuilt JavaScript engine
  (`libgboardpatches_quickjs.so`) whose source is not in the repository. It is only loaded when you create a
  script, and scripts can make network requests (and can read the text you have selected).
- "AI Writing Tools" turns on Gboard's own Google-side features, so selected text goes to Google when used.
- "Web Clipboard" and "FTP Server" stay risky when switched on: the first lets anyone on the network who guesses
  a 4 digit code read your clipboard (passwords, codes), and since 4.5.0 also the copied images and screenshots,
  without skipping the ones Android marks as sensitive; the weak pairing code problem is not fixed upstream. The
  second opens your shared storage to anyone on it.
- The settings backup file holds settings in plain text, including the FTP password, the Web Clipboard
  pairing code and your JavaScript.
- The "Backup & Restore" patch (on by default) now forces three Gboard backup switches on all the time
  (`GboardDataBackupFlagPolicy.java`). So Chinese learned words and saved expression images also become part of
  Gboard's own Android backup, which goes to Google when the phone's Google backup is on. Stock Gboard does this
  on Pixel phones. Lynx did not change it.
- A Gboard data import has no limit on the unpacked size of each file in the ZIP, and a ZIP can set any Gboard
  setting. The export ZIP is not encrypted. Only import ZIPs you made yourself.
- Open upstream issues: 158 (the clipboard suggestion chip is missing on some phones) and 170 (stuck in
  incognito mode; upstream could not reproduce it).

## Tests

Run on the PC on 2026-11-29 with the bundle built from this folder (`--rerun-tasks --no-build-cache`).
Applying to the original app with Morphe Desktop 2.2.0 (`patch --validate`): the default set applies 44
patches, none failed, the same 44 as the 4.4.0-lynx.1 build (upstream's 46 minus the two now off); with Web
Clipboard and FTP Server switched on, all 46 apply. The patched app's manifest (permissions, components, intent
filters) is identical to the 4.4.0-lynx.1 build's, and the bundle contains no web address the 4.4.0-lynx.1
bundle did not. Unit tests (JDK 21, `./gradlew test`): patches 303, extension 1355, calculator 13; none failed,
1 skipped (a symbolic link test that Windows does not allow). The new `LynxWebClipboardCaptureScopeTest.kt` was
shown to fail when the capture patch is added to another patch's dependencies and when Web Clipboard is made
on by default, and to pass again after both were undone. Not tested: running this Gboard on a phone or in the
emulator.

## How to build

```
export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
cd packs/gboard
./gradlew :patches:buildAndroid -Pversion=4.5.0-lynx.1 --no-daemon
```

The bundle is `patches/build/libs/patches-4.5.0-lynx.1.mpp`.

## How to add it to Morphe Manager

On the phone: Sources, plus, paste

```
https://raw.githubusercontent.com/xhyxfxxc/lynx-patches/main/packs/gboard/patches-bundle.json
```
