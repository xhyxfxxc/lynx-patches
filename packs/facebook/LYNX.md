# Hushfacebook (Lynx build)

## Status

Published on 2026-11-28 as 0.25.0-lynx.1, after the owner's decision on the new share screen of the default-on patch
"Share to Facebook chats" (see "New in 0.25.0" below): it is kept as upstream ships it. The patch is on by default,
and the share screen stays switched off and out of the share menu until "Share to Facebook chats" is turned on in
Hushfacebook settings > Chats and Messenger. The release is `pack-facebook-v0.25.0-lynx.1`; the version before it is
0.24.0-lynx.1.

## What this is

Patches for the Facebook app (package `com.facebook.katana`), made for use with Morphe. They fit one Facebook
build only: 588.0.0.42.55, build 707615136 (the arm64-v8a bundle on APKMirror). The 32-bit build 707615135 was
not tested by Lynx; other builds of 588 and all older versions are not supported. Upstream is Hushfacebook 0.25.0
by SysAdminDoc (https://github.com/SysAdminDoc/HushFacebook), commit 5e928c9. The Lynx version is 0.25.0-lynx.1
(published on 2026-11-28). It needs Morphe Manager 2.7.0 or newer (Morphe Desktop 2.3.0 or newer on a PC).
Terms: a "patch" rewrites the Facebook app on a PC before it is installed; the "extension" is Java code the patches
add that then runs inside the app; the "bundle" (a `.mpp` file) is the finished pack that Morphe applies.

## Why it is here

A reviewed copy of the upstream source (see "What was reviewed" below). Lynx CI builds the bundle from this folder
and publishes it as its own Morphe source, so it is built from exactly this code, not from upstream's own file.

## What Lynx changed

1. "Report a problem" and "Source code and issues" point at Lynx. Report a problem opens a new issue at
   https://github.com/xhyxfxxc/lynx-patches/issues/new (versions, phone model and the switches that are on are
   filled in as the text, because Lynx's bug form has no fields for this pack), and the settings "source" link
   opens https://github.com/xhyxfxxc/lynx-patches/tree/main/packs/facebook. Before, both went to the upstream
   author's repository. The Ko-fi link and the release check are unchanged (the check is off by default and still
   reads the upstream author's releases). Files: `HushfacebookPreferenceFragment.java` and `ReportProblemForm.java`
   (extension, settings folder), and the five tests that pinned the old addresses. The 0.25.0 merge conflicted once,
   in `SettingsL10nTest.java` (the list of texts that stay as they are in every language): Lynx's address was kept
   and upstream's new "#1E3A5F" entry was taken. That is not a new Lynx change.
2. Marked as a modified version, as the GPL asks: the bundle's name, source, website and author in
   `patches/build.gradle.kts` (this is what Morphe Manager shows), and a Lynx section at the top of `NOTICE`
   (it names upstream 0.25.0; the upstream text below it is unchanged and shown in the app under Licenses).
3. Build fix: `gradle/verification-metadata.xml` had no checksum for some small metadata files the build downloads
   (junit-bom 5.14.4, the Bouncy Castle BOM 1.85 and the parent file of Guava 33.8.0; none is in the bundle), so
   the build stopped on this PC. Each was added only after matching the file with the SHA-256 Maven Central shows.
4. Build fix: `helper/build.gradle.kts` read every dot-separated part of the version as a number, so a suffix such
   as 0.25.0-lynx.1 stopped the whole build. It now reads only the part before the dash.
5. Hush Unpacker (the `helper/` app, only for the Android 9 and 10 build of Facebook) is not published by Lynx:
   its source is left where it is, its APK is not built or shipped (the bundle build only builds it when
   `helper/keystore.properties` exists, which it does not here).
6. Build fix: the dependency checksum list now also accepts the Linux build of the Android resource tool aapt2
   (checksum from Google's own published value), because upstream only builds on Windows and Lynx builds on Linux.
7. Help (2026-11-26, owner request; docs only, not in the bundle): `docs/help.md`, entry "Facebook links from other
   apps don't open in Facebook", has a new part on moving only Facebook's addresses with adb (`pm set-app-links`:
   denied on the Meta app that holds them, approved on Facebook), with the m.me exception and how to undo it.
   Checked on the owner's Galaxy S26 Ultra (Android 17, Facebook 588). On an upgrade, check it against the new
   build's manifest (`packs/PROCESS.md`, "Link handling of the Meta packs"). Still in place after the 0.25.0 merge.
8. Tests (2026-11-28, owner rule: remove redundant tests, add needed ones; tests are not in the bundle):
   `ShareToChatsDeclarationTest.kt` (new) pins that the share screen ships exported but switched off, with only the
   photo, video and text share filters; `ReportProblemFormTest.java` keeps only its two address pins, because the
   rest repeated `SettingsNavigationTest`.

## New in 0.25.0: what the review found

Nothing of upstream's was removed except "Unlock app icons" (point 4). Worth knowing before you install:
1. "Share to Facebook chats" is a new patch, on by default, and it was the one open question. It adds an exported
   share screen (`app.morphe.extension.facebook.chats.ShareToChats`) to the patched app. The screen is declared
   disabled (`enabled="false"`) and does nothing until the switch of the same name in Hushfacebook settings is
   turned on; the switch is off by default (a settings import that you choose could turn it on). When it is on, any
   app on the phone can hand it photos, videos or text through Android's share sheet. It refuses file addresses and
   Facebook's own files, and it does not pass the incoming intent on. Two weak points, both only while the switch is
   on: it reads the shared list on the main thread with no limit on the number of items (a hostile app could make
   Facebook hang for a while), and it does not require that the sender granted read access (it could copy a file
   from a provider that Facebook itself may read). No P0 or P1 was found. Owner's decision (2026-11-28): publish as
   upstream ships it, with no Lynx change.
2. "Chat background" is a new patch, on by default, local only. It saves the name of the last opened chat (and its
   thread key) in Facebook's private settings every time a chat opens, even when no color is set. The name stays on
   the phone and is left out of a settings export. It may cost a little jank, because the save is written at once.
3. The battery fix (upstream issue 548) is in "Video buffer" (on by default; if its anchor is missing the patch
   fails soft). The Play Store double-pick fix (issue 561) is extension-only. Both kept.
4. "Unlock app icons" was removed by upstream on purpose (it got around a paid-app-icon check on the phone, a
   takedown risk). A Plus icon picked under 0.24.0 should fall back to the default icon.
5. Four new languages: Arabic, Bulgarian, Italian, Korean (the new tables hold text entries only).
6. 13 new switches, all off by default (18 settings added, 2 removed, no existing default changed). The new "Mark
   all notifications as read" row only acts when you tap it. 128 patches, 123 on by default (was 127 and 122).
7. Still true from 0.24.0: the In-app proxy row is blank by default and does nothing while blank (if you type a
   proxy, it can see which servers Facebook talks to, and a dead proxy blocks Facebook until you clear the row);
   the crash file is on by default (Download/Hushfacebook, at most 5 files, personal details removed); the switches
   that act for you ("Next reel in background", "Translate every comment", "Save all videos") are off by default.
8. No new network destination and no new Android permission. The only new exported component is the share screen of
   point 1 (checked on the patched app, see "What was tested").

## What was reviewed and what was not

The 0.24.0 to 0.25.0 update was reviewed before it was committed: `upstream-src/_audit/hushfacebook-v0.25.0-review.md`
(local, not in git; verdict: safe to build, not clean for an unattended publish because of point 1 above and the one
merge conflict). 248 files changed (+45,292 / -3,687 lines; about 33,000 of those lines are the four new translation
tables). Three read-only reviewers (extension, patches, tests and docs) and the main session read it; of the build
files only `gradle.properties` changed (the version), so no download was needed. A grep for network, socket,
WebView, class loading, crypto and broadcast code over every added extension line found only the two content-reader
calls of the share screen. Share screen, Chat background, Mark all read, Video buffer and the build files were read
closely; many layout and reels files and the translation tables were only scanned. The first audit of the whole
pack is `hushfacebook-report.md` in the same folder. The review found no confirmed P0 or P1.

## What was tested (0.25.0-lynx.1, 2026-11-27)

Run on the Lynx PC against the original Facebook 588.0.0.42.55 (build 707615136). Logs and files are in
`C:\Users\thoma\Morpheapp\out\facebook\lynx-0.25.0\` (local, not in git).

- Build: succeeded with Gradle dependency verification on (no checksum was added). Bundle
  `patches-0.25.0-lynx.1.mpp`, 9,903,814 bytes, SHA-256
  3922d4ed32725c3987a9d6149d4cc7a5b918dcffe960655756a8d5ff022e111f. The build rewrote the version line of the
  tracked `patches-list.json`; it was restored and is not committed.
- Unit tests: patch tests 1,758, 0 failed (539 skipped: the fixture tests, which need a folder of Facebook files
  that is not set here). Extension tests 6,184, 45 failed. All 45 also failed in the 0.24.0 build on this PC
  (46 there); none is new. They are in the font file, own emoji, ffmpeg join (Mp4Join), push state, crash report
  undo and a few save tests. They were analysed on 2026-11-28: see the next line and the first entry under Known
  problems.
- Unit tests on JDK 25 (2026-11-28, temurin-25.0.5.1, the JDK that upstream's README asks for on Windows; the run
  above used JDK 21, temurin-21.0.13): extension tests 6,184, 42 failed (45 on JDK 21). The four Mp4JoinTest tests
  pass on JDK 25 only. FontFilePreferenceTest.aFullWorkerQueueLeavesTheRowsUsable fails on JDK 25 but is unstable
  (it failed on JDK 21 with 0.24.0 and passed on JDK 21 with 0.25.0). The other 41 fail on both JDKs. Logs:
  `out\facebook\lynx-0.25.0\test-extensions.log` (JDK 21), `test-extensions-jdk25.log` and `test-tmpdir-jdk25.log`.
- Fixture tests (2026-11-28, `:patches:fixtureTest`, with only the arm64-v8a file of Facebook 588.0.0.42.55 in
  `C:\Users\thoma\Morpheapp\fixtures\facebook`; the armeabi-v7a and Android 9 files are not on this PC): 711 run,
  652 passed, 58 failed, 1 skipped (the Android 9 refusal test). All 58 failures are checks that every declared
  build was seen, so they are about the missing files, not about the arm64 build: 45 "an ABI went unchecked", 11
  "one fixture for each declared build" (`MaterialYouTokenFixtureTest`), 1 "one retained fixture for each declared
  build" (`AppCompatibilitiesMatchFixturesTest`) and 1 "armeabi-v7a fixture missing" (`PushDiagnosticsFixtureTest`,
  which stops before it reads any build). No fingerprint or patch check failed on the arm64 build. Upstream's
  manifest test of the share screen (`ShareToChatsManifestTest`, 2 tests) passed. Log:
  `out\facebook\lynx-0.25.0\test-fixtures.log`.
- Morphe Desktop 2.3.0 with `--validate`: default set 123 applied, 0 failed, no warning (128 patches, 5 off by
  default). With the two off-by-default theme patches ("AMOLED black theme", "Material You theme") on too: 125
  applied, 0 failed. This proves the patches fit this build, not that every feature works on a phone.
- Manifest of the patched app, compared with the 0.24.0-lynx.1 patched app: exactly one difference, the added
  exported activity `app.morphe.extension.facebook.chats.ShareToChats`, `enabled="false"`, with a SEND filter
  (image/*, video/*, text/plain) and a SEND_MULTIPLE filter (image/*, video/*), category DEFAULT only, no
  permission attribute. No Android permission was added or removed. Compared with the original Facebook, the
  difference is that activity, the "Open in Facebook" share target (already in 0.23.0) and the two Meta signature
  permissions renamed to `app.hushfacebook.*` (unchanged since 0.24.0).
- Hosts in the bundle (`tools/bundle_hosts.py`, against the published 0.24.0-lynx.1 bundle): 0 added, 0 removed.
- Emulator start-up test (`tools/emu_smoke.py`, Android emulator, no sign-in): PASS. The original and the patched
  app both come up on the logged-out welcome screen ("Get started", "I already have a profile") under Android's
  notification permission dialog; no crash, no ANR, no class errors; the patched app logs 7 lines from the
  Hushfacebook code at start.

Not tested: anything signed in (feed, Reels, stories), the new switches (none was turned on), the share screen in
use or even switched on, Chat background with a color set, any phone (no Galaxy S25 test of 0.25.0), whether the
battery fix works, and an upgrade over an installed 0.24.0 build.

## Upstream documentation that does not apply to this build

The upstream README, docs and CONTRIBUTING still point at upstream's own source and releases. For this build: add
the Lynx source (below), report at https://github.com/xhyxfxxc/lynx-patches/issues, and there is no Hush Unpacker.
Ignore README.md lines 4, 12-13, 25, 27, 35 (badge, Ko-fi, upstream release and add-source links), 118 (upstream
Discussions and forms), 149 (the release check reads upstream releases), 164 and 175-179 (Hush Unpacker, upstream
checksums), docs/help.md lines 3, 13, 55, 135, and CONTRIBUTING.md (upstream tracker and release steps). Line
numbers checked again on 2026-11-27: unchanged.

## Known problems that are not fixed

- Unit tests that fail on the Windows PC Lynx builds on (checked 2026-11-28,
  `:extensions:facebook:testDebugUnitTest`, 6,184 tests). 41 fail on JDK 21 and JDK 25 alike: FontFilePreferenceTest
  12, OwnFontFileTest 8, PushStateStoreTest 6, JavaCrashReportTest 6, OwnEmojiTest 3, SaveFailureCauseTest 2,
  MediaSaveTest 1, HeaderTextTest 1, DashJoinTest 1, ClearLogBufferPreferenceTest 1. One more,
  FontFilePreferenceTest.aFullWorkerQueueLeavesTheRowsUsable, fails on JDK 25 only and is unstable (it failed on
  JDK 21 with 0.24.0 and passed on JDK 21 with 0.25.0). The four Mp4JoinTest tests fail on JDK 21 and pass on
  JDK 25. All are file operations: the font tests get
  `FontFile$Refused: FileSystemException` from the atomic move in `FontFile.moveIn` right after a typeface loaded
  the file, because Windows refuses to rename a file that is still open or mapped, while Android allows it. Other
  tests fail because a second write through `android.util.AtomicFile` does not land, or because work files cannot
  be deleted. They are not bugs on Android phones. Moving Java's temp folder out of AppData\Local (TMP, TEMP,
  `-Djava.io.tmpdir`) changed nothing; antivirus settings were not tried and are not to be changed. A build
  session compares its failures with this list: a failure that is not on it is new and is reported.
- Open upstream issues on 0.25.0, none a confirmed P0 or P1: 587 (fullscreen after a reel, cosmetic), 575 and 556
  (seek bar stays at 0:00, not reproduced), 593 (no sound, seen on 0.23.0 and 0.24.0), 570 (Home stops loading,
  since 0.22), 568 (a Google sign-in prompt on calls after turning on Share to Facebook chats, unconfirmed). Fixed
  upstream but not yet released: 589 (settings export loses number rows), 585 and 586 (cosmetic on a Galaxy S26
  Ultra).
- Reported against 0.24.0 and earlier, not re-checked for 0.25.0: issue 551 (on Reels the layout sits too low on a
  Galaxy S26 Ultra with Android 17; Lynx did not see it on a Galaxy S25 Ultra), issue 552 (video stories can
  freeze, the seek bar sits low, reel captions break when "Keep progress bar" and "Keep the tab bar on reels" are
  both on), issue 528 (a crash at launch with Android 17, cause unknown; the pack pauses itself after three crashes
  in a minute), issue 505 (double seek bar). Battery drain (issue 548) should be fixed by the 0.25.0 "Video buffer"
  change; Lynx did not measure it.
- Sign-in can loop on accounts that have a passkey (upstream issue 273, closed with a workaround): remove the
  passkey in Accounts Center, sign in, then add it again.
- The re-signed Facebook accepts its own signing key in place of Meta's for the Meta apps on the phone, so it is
  only as private as the key Morphe Manager signs with. Keep that key private.

## How to build

In Git Bash, from this folder (needs JDK 21, the Android SDK and a GitHub token that can read packages):

    export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
    export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
    ./gradlew :patches:generatePatchesList :patches:buildAndroid -Pversion=0.25.0-lynx.1 --no-daemon

The finished bundle is `patches/build/release/patches-0.25.0-lynx.1.mpp` (the copy in `patches/build/libs` is
an intermediate file). It is unsigned. Built inside the Lynx repository the bundle says its source state is
"unknown", because the identity check only trusts a repository whose root is this folder. The build also changes
the version line of the tracked `patches-list.json`; restore it with `git checkout -- patches-list.json`.

## How to add it to Morphe Manager

On the phone: Sources, plus, paste

    https://raw.githubusercontent.com/xhyxfxxc/lynx-patches/main/packs/facebook/patches-bundle.json
