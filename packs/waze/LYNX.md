# bearinmind Waze patches (Lynx build)

## What this is

A set of patches for the Waze app (com.waze, versions 5.24.5.0 to 5.27.5.0), for use with Morphe. It is the Waze
part of "bearinmind patches" 2.10.0 by bearinmindcat (https://github.com/bearinmindcat/morphe-patches, commit
1df2542), with a few changes by Lynx. The upstream project patches 87 apps; only its Waze files are in this
folder. Lynx version: 2.10.0-lynx.1.

Terms: a "patch" runs on your PC when you patch the app and rewrites it. The "extension" is the Java code that is
added to Waze and runs inside it. A "bundle" (.mpp) is the built pack that Morphe applies.

## Why it is here

Someone else wrote this pack. Lynx keeps a reviewed copy of the source in this folder and builds it with Lynx CI,
so the bundle comes from source that was read, not from the author's own download. This is not the original and
its author has not endorsed it. The licence is GPL-3.0 (see LICENSE and NOTICE).

## What Lynx changed

1. The bundle description names only the Waze patches (patches/build.gradle.kts). The upstream text listed all 87
   apps. Nothing else was needed to build this Waze-only copy.
2. The patch "Share alerts with Maps" is removed. It was on by default. It sent a broadcast with the position of
   the alerts around the car to every app on the phone, again every 60 seconds, let any app ask for a re-send, and
   let any app start Waze's engine in the background through Waze's open AIDLService. It only fed the author's
   Google Maps patches, which are not part of this pack. Removed: Bridge.java, the patch in
   WazeShareAlertsPatch.kt and its switch in Customization.java. The file WazeShareAlertsPatch.kt stays because
   the unrelated patch "Stay awake in the background" (off by default) lives in it; its descriptions no longer
   say "for Maps".
3. "Flock cameras" and "Speed camera alerts" (both off until you switch them on inside Waze) ask public
   OpenStreetMap (Overpass) servers for cameras. In OsmCameras.java the server maps.mail.ru (a Mail.ru mirror) is
   removed from the list, redirects are no longer followed, and a reply bigger than 8 MB is refused.
4. The pack says it is a modified version, as the licence asks: the bundle is named "bearinmind Waze patches (Lynx
   build)", its source and website point at Lynx, the author reads "bearinmindcat, modified by xhyxfxxc", and
   NOTICE starts with a Lynx section above the unchanged original text. Files: patches/build.gradle.kts, NOTICE.
5. This file (LYNX.md). README.md and CHANGELOG.md are upstream's own and were not edited: they describe all of its
   apps and still list the removed patch.

## What was reviewed and what was not

A static security audit of the Waze part was done on 2026-11-23 (reading the source and the released bundle, no
run, no Waze APK). It found no data theft or remote control apart from the hole removed in change 2. It cannot
prove the released bundle behaves exactly like its source. The other 86 apps were not audited. Lynx applied the 14
default patches to Waze 5.27.5.0 with Morphe Desktop (all apply). Nothing has been tested on a phone yet.

## Known problems that are not fixed

- "Remove permissions" is on by default and takes away the calendar, contacts, phone state and camera permissions
  (also profile and advertising ID). Waze's calendar and contacts features stop working by design (upstream
  issue 191).
- A re-signed Waze does not appear in Android Auto unless "Unknown sources" is enabled there (issue 88).
- Spotify inside Waze may show "Nothing is playing" (issue 213, cause unknown).
- The camera layers, once switched on, send the rough map area the car is in (a square about 25 km wide) and
  your IP address to public Overpass servers (overpass-api.de, lz4.overpass-api.de, z.overpass-api.de and
  overpass.private.coffee). They are off until you switch them on.
- "Patched YouTube Music" accepts any app whose receiver class name starts with a known music app's package name,
  so a hostile app could pose as a music partner of Waze (audit finding F4).
- Debug self-test receivers exist but only work after an adb command on the phone (audit finding F5).

## How to build

In Git Bash from this folder (needs JDK 21, the Android SDK and a GitHub token with read:packages):

    export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
    export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
    cd packs/waze
    ./gradlew :patches:buildAndroid -Pversion=2.10.0-lynx.1 --no-daemon

The bundle is written to patches/build/libs/patches-2.10.0-lynx.1.mpp. It is unsigned.

## How to add it to Morphe Manager

On the phone: Sources, plus, paste

    https://raw.githubusercontent.com/xhyxfxxc/lynx-patches/main/packs/waze/patches-bundle.json
