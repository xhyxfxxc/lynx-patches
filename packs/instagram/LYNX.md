# HushGram (Lynx build)

## What this is

A set of patches for the Instagram app (com.instagram.android), for use with Morphe. It is HushGram 0.0.22
by SysAdminDoc (https://github.com/SysAdminDoc/HushGram, commit 30d2d10), with small changes by Lynx (listed
below). It was written for Instagram 456.0.0.53.78 (arm64-v8a build 386214574) and only that version. Lynx
version: 0.0.22-lynx.1. It needs Morphe Manager 2.6.0 or newer.

Terms: a "patch" runs on your PC when you patch the app and rewrites it. The "extension" is the Java code
added to Instagram that runs inside it. A "bundle" (.mpp) is the built pack that Morphe applies.

## Why it is here

Someone else wrote this pack. Lynx keeps a reviewed copy of the source here and builds the bundle from it
with Lynx CI, not from the author's own download. This is not the original and its author has not endorsed
it. The licence is GPL-3.0 (see LICENSE and NOTICE).

## What Lynx changed

1. Report a problem and the About "Source code and issues" row now go to Lynx, not to the original author.
   Report a problem opens a new issue on https://github.com/xhyxfxxc/lynx-patches/issues/new with the versions,
   the phone and the switches that are on filled into the Lynx bug form. The source row opens
   https://github.com/xhyxfxxc/lynx-patches/tree/main/packs/instagram. The Ko-fi support link is untouched.
   Files: HushgramPreferenceFragment.java, ReportProblemForm.java and three tests.
2. The seek bar preview strip (the small pictures when you drag a reel's bar) now uses the same address check
   as the other save paths, so it is not refused behind a VPN or proxy. Still only Meta's media servers, only
   HTTPS. File: download/SeekSprites.java (one line).
3. The pack says it is a modified version, as the licence asks: the bundle is named "HushGram (Lynx build)",
   its source, website and contact point at Lynx, the author reads "SysAdminDoc, modified by xhyxfxxc", and
   NOTICE starts with a Lynx section above the unchanged original text. Files: patches/build.gradle.kts, NOTICE.
4. Build only: the extension tests wanted .github/ISSUE_TEMPLATE/bug_report.yml inside this folder, which the
   import leaves out. The test input now points at the Lynx repository's own form two folders up (and does not
   fail when it is missing). Nothing in the bundle changes. File: extensions/instagram/build.gradle.kts.
5. This file (LYNX.md). Our test ReportProblemFormTest now checks the field names against the Lynx form.
6. Build only: the dependency checksum list now also accepts the Linux build of the Android resource tool aapt2
   (Google's own checksum), because upstream builds only on Windows. File: gradle/verification-metadata.xml.
0.0.22 needed no new Lynx change: these six were checked after the merge. No upstream default was changed.

## New in 0.0.22: what the review found

197 patches (11 new), 193 in the default selection. Nothing was removed and no existing default changed. The 11
new patches are all in the default selection and every new feature switch starts off: Center names in chats,
Chat bubbles, Expand captions, Hide Followers and Following on profiles, Hide New message button and
suggestions, Hide the line under names, Keep the tab bar on reel links, Let the screen sleep when a reel
stops, Record calls, Reels on Home below the name, Run without Play services. Worth knowing:
1. One new network path, off by default. "Ask for VP9 instead of AV1" can now ask Instagram's own website,
   logged out, for a VP9 copy of a post or reel that Instagram sent as AV1. It sends the public post's number
   and reads no account cookie or sign-in token. It is the one exception to "only Meta's media servers" (the
   server is still Instagram's). The request imitates a web browser, which Instagram may not like: leave it off.
2. "Record calls" (off by default). With the switch on, a Record button shows on the call screen and each
   recording starts with a tap. It saves the voices of the call as an audio file in Recordings/Instagram
   (Music/Instagram below Android 12), where apps with audio access can read it. Nothing is uploaded and the
   other person is not told. Recording a call can need everyone's consent (in New South Wales it generally
   does). Upstream report 831 says tapping this switch closed the app on 0.0.22 (one report, no log).
3. On by default, local only: for "Hide notifications by type", HushGram keeps one sample line (up to 120
   characters) for each of up to 80 kinds of notification, in the app's private storage. It is never exported,
   never in the diagnostic report and never uploaded.
4. Also on without a switch: opening HushGram's settings pauses a playing video; saves run up to three at once
   (twenty may wait) with picture and sound fetched together; "Run without Play services" makes Firebase's
   pending intent immutable; on a re-signed build "Log in with Facebook" goes through the browser. Two new
   sub-switches ("Glass buttons in chats", "Drop quality to keep playing") start on but do nothing until their
   parent switch is on. 63 new setting keys, no existing default changed.
5. No new permission, no new activity, service, receiver or provider, no code loading, no build-time risk.

## What was reviewed and what was not

The update was reviewed by reading the source (no run) on 2026-11-23 by four reviewers plus the lead's own
re-check of the network, storage and new-file code. Verdict: safe to build. The review file is local
(upstream-src/_audit/hushgram-v0.0.22-review.md, not in git). Lynx then built this source and applied the
bundle to the original Instagram 456.0.0.53.78 with Morphe Desktop 2.3.0 and --validate: of 197 patches (193
on by default) all 193 default ones applied, 0 failed, one warning (a Clean up Reels note, also in 0.0.21).
Against the 0.0.21-lynx.1 build exactly the 11 new patches were added and none lost; the patched app's manifest
is identical (permissions, components, exported values) and the bundle names no new web host. This proves the
patches fit this build, not that every feature works on a phone.
Emulator test (2026-11-23, Android 15 emulator, not signed in): the original Instagram and this patched build
both install and start on the sign-in screen, the patch code loads, and there is no crash, no "not responding"
report and no class-loading error. Signed-in test on a phone: NOT run for this release (the emulator apps are
not signed in and the test phone was locked), so feeds, HushGram's own settings and the upstream reports below
are untested by Lynx on 0.0.22.

## Upstream documentation that does not apply to this build

The upstream README, CONTRIBUTING and docs describe the original author's own releases. For this build: add
the Lynx source (below) and report problems at https://github.com/xhyxfxxc/lynx-patches/issues. Ignore
README.md line 27 (v0.0.21, 186 patches, upstream links), lines 36 and 42 (upstream add-source and release
downloads), line 346 (upstream bug forms and Discussions), "Verify a download" from line 371 (checksums of
upstream's release; the Lynx bundle is unsigned) and CONTRIBUTING.md (upstream tracker and release steps).

## Known problems that are not fixed

- Facebook sign-in: 0.0.22 changed it (it goes through the browser, upstream issue 760); not confirmed by the
  reporter, not tested by Lynx. Sign-in problems 240 (a right password is refused) and 440 are still open.
- Open upstream reports on 0.0.22: 831 (Record calls closes the app), 827 (reels time overlaps the seek bar),
  829 ("Loop this video" missing, reels replay). Older and still open: 805 (black bar on reels with Clear mode
  and 3-button navigation), 791 (reels stuck on the first frame after returning to the Reels tab), 777 (reels
  keep the screen awake; the new "Let the screen sleep when a reel stops" switch is off by default), 813 (a
  one-time photo vanishes), 749 (chat jitter with "Glass in chats" on; 757 and 806 are fixed in 0.0.22).
- "Hide reels by caption words" (off by default) saves two settings to the phone's storage each time a new item
  is hidden, so the screen can stutter when many items match. "Hide reels with few views" (off by default) can
  hand back an empty page when every reel on it is outside the chosen numbers, which Instagram may read as the
  end of the list. Both are suspected from reading the code, not reproduced.
- The pack makes the re-signed Instagram accept its own signing key in place of Meta's ("Restore trust on
  re-signed builds", always on). It is only as private as the key Morphe Manager signs with: do not share it.
- Meta can detect patched builds and has restricted accounts (upstream issues 317, 405, 615, closed without a
  root cause). All "ghost" style switches (anonymous story views, hidden typing) start off. Try a spare account.
- Chat history tools (off by default) can freeze the screen on a very long chat (suspected, not proven). Chat
  export (off by default) writes the chat as plain text into the shared Download folder; the new "Select
  messages" in it can also copy or share chosen messages. Copy text (off by default) sends a screenshot of a
  reel or story to Google Lens. All were left in as upstream wrote them.
- Unit test HushgramPauseTest `anExceptionSwallowedOffTheMainThreadPutsTheReportBack[30]` and `[37]` fail on this
  Windows PC on JDK 21 and on JDK 25 alike (checked 2026-11-28), so they are not a JDK 21 issue.

## How to build

In Git Bash from this folder (needs JDK 21, the Android SDK and a GitHub token with read:packages):

    export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
    export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
    cd packs/instagram
    ./gradlew :patches:generatePatchesList :patches:buildAndroid -Pversion=0.0.22-lynx.1 --no-daemon

The bundle is written to patches/build/release/patches-<version>.mpp and is unsigned. The build rewrites the
version line of patches-list.json: do not commit that change. Tests, if wanted:
./gradlew :extensions:instagram:testDebugUnitTest :patches:test --no-daemon.

## How to add it to Morphe Manager

On the phone: Sources, plus, paste

    https://raw.githubusercontent.com/xhyxfxxc/lynx-patches/main/packs/instagram/patches-bundle.json
