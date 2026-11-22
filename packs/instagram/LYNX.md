# HushGram (Lynx build)

## What this is

A set of patches for the Instagram app (com.instagram.android), for use with Morphe. It is HushGram 0.0.21
by SysAdminDoc (https://github.com/SysAdminDoc/HushGram, commit 78f7deb), with small changes by Lynx (listed below).
It was written for Instagram 456.0.0.53.78 and only that version. Lynx version: 0.0.21-lynx.1.

Terms: a "patch" runs on your PC when you patch the app and rewrites it. The "extension" is the Java code
that is added to Instagram and runs inside it. A "bundle" (.mpp) is the built pack that Morphe applies.

## Why it is here

Someone else wrote this pack. Lynx keeps a reviewed copy of the source in this folder and builds it with
Lynx CI, so the bundle comes from source that was read, not from the author's own download. This is not the
original and its author has not endorsed it. The licence is GPL-3.0 (see LICENSE and NOTICE).

## What Lynx changed

1. Report a problem and the About "Source code and issues" row now go to Lynx, not to the original author.
   Report a problem opens a new issue on https://github.com/xhyxfxxc/lynx-patches/issues/new with the
   versions, the phone and the switches that are on filled into the fields of the Lynx bug form. The source
   row opens https://github.com/xhyxfxxc/lynx-patches/tree/main/packs/instagram. The Ko-fi support link is
   untouched. Files: HushgramPreferenceFragment.java, ReportProblemForm.java and three tests.
2. The seek bar preview strip (the small pictures when you drag a reel's bar) now uses the same address check
   as the other save paths, so it is not refused behind a VPN or proxy. Still only Meta's media servers, only
   HTTPS. File: download/SeekSprites.java (one line).
3. The pack says it is a modified version, as the licence asks: the bundle is named "HushGram (Lynx build)",
   its source, website and contact point at Lynx, the author reads "SysAdminDoc, modified by xhyxfxxc", and
   NOTICE starts with a Lynx section above the unchanged original text. Files: patches/build.gradle.kts, NOTICE.
4. Build only: the extension tests wanted a file .github/ISSUE_TEMPLATE/bug_report.yml inside this folder, which
   the import leaves out. The test input now points at the Lynx repository's own form two folders up (and does
   not fail when it is missing). Nothing that goes into the bundle changes. File: extensions/instagram/build.gradle.kts.
5. This file (LYNX.md). Our test ReportProblemFormTest now checks the field names against the Lynx form.
6. Build only: the dependency checksum list now also accepts the Linux build of the Android resource tool aapt2
   (checksum from Google's own published value), because upstream only builds on Windows and Lynx builds on Linux.

## What was reviewed and what was not

A static security audit of HushGram 0.0.21 was done on 2026-11-23 (reading the source and the released bundle,
no run). It found no data theft or remote control, and listed risks that are in this file below. It did not
decompile every instruction, so it cannot prove the released bundle behaves exactly like its source. Nothing here
has been tested on a phone yet.

## Known problems that are not fixed

- Facebook sign-in hangs on a re-signed Instagram (upstream issue 760). Signing in with user name and password works.
- The pack makes the re-signed Instagram accept its own signing key in place of Meta's ("Restore trust on
  re-signed builds", always on). It is only as private as the key Morphe Manager signs with. Do not share
  that key or reuse a published one.
- Meta can detect patched builds and has restricted accounts (upstream issues 317, 405, 615, closed without a root
  cause). All "ghost" style switches (anonymous story views, hidden typing, etc.) start off. Try a spare account first.
- Other open upstream reports: 805 (black bar on reels with Clear mode and the seek bar on 3-button
  navigation); 757, 749, 806 (chat screen jitter, only with "Glass in chats" on); 791 (reels stuck on the first
  frame after returning to the Reels tab); 751 (story edges flash with "Ambient light for stories"); 777 (reels
  keep the screen awake); 813 (a one-time photo vanishes); 240 (a right password is refused as wrong).
- Chat history tools (off by default) can freeze the screen on a very long chat (suspected, not proven).
- Chat export (off by default) writes the chat as plain text into the shared Download folder. Copy text (off
  by default) sends a screenshot of a reel or story to Google Lens. Both were left in as upstream wrote them.

## How to build

In Git Bash from this folder (needs JDK 21, the Android SDK and a GitHub token with read:packages):

    export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
    export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
    cd packs/instagram
    ./gradlew :patches:generatePatchesList :patches:buildAndroid -Pversion=0.0.21-lynx.1 --no-daemon

The bundle is written to patches/build/release/patches-<version>.mpp. It is unsigned. Notes: the library needs
Android platform 35 (Gradle installs it by itself; several builds at once can leave it half installed). The
build rewrites the version line of patches-list.json: do not commit that change. Tests, if wanted:
./gradlew :extensions:instagram:testDebugUnitTest :patches:test --no-daemon (long).

## How to add it to Morphe Manager

On the phone: Sources, plus, paste

    https://raw.githubusercontent.com/xhyxfxxc/lynx-patches/main/packs/instagram/patches-bundle.json
