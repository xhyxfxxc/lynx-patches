# Hushfacebook (Lynx build)

## What this is

Patches for the Facebook app (package `com.facebook.katana`), made for use with Morphe. They fit one Facebook
build only: 588.0.0.42.55, build 707615136 (the arm64-v8a bundle on APKMirror). The 32-bit build 707615135 was
not tested by Lynx; other builds of 588 and all older versions are not supported. Upstream is Hushfacebook 0.24.0
by SysAdminDoc (https://github.com/SysAdminDoc/HushFacebook), commit e82b05b. The Lynx version is 0.24.0-lynx.1.
It needs Morphe Manager 2.7.0 or newer (Morphe Desktop 2.3.0 or newer on a PC). Terms: a "patch" rewrites the
Facebook app on a PC before it is installed; the "extension" is Java code the patches add that then runs inside
the app; the "bundle" (a `.mpp` file) is the finished pack that Morphe applies.

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
   (extension, settings folder), and the five tests that pinned the old addresses.
2. Marked as a modified version, as the GPL asks: the bundle's name, source, website and author in
   `patches/build.gradle.kts` (this is what Morphe Manager shows), and a Lynx section at the top of `NOTICE`
   (it names upstream 0.24.0; the upstream text below it is unchanged and shown in the app under Licenses).
3. Build fix: `gradle/verification-metadata.xml` had no checksum for some small metadata files the build downloads
   (junit-bom 5.14.4, the Bouncy Castle BOM 1.85 and the parent file of Guava 33.8.0; none is in the bundle), so
   the build stopped on this PC. Each was added only after matching the file with the SHA-256 Maven Central shows.
4. Build fix: `helper/build.gradle.kts` read every dot-separated part of the version as a number, so a suffix such
   as 0.24.0-lynx.1 stopped the whole build. It now reads only the part before the dash.
5. Hush Unpacker (the `helper/` app, only for the Android 9 and 10 build of Facebook) is not published by Lynx:
   its source is left where it is, its APK is not built or shipped (the bundle build only builds it when
   `helper/keystore.properties` exists, which it does not here).
6. Build fix: the dependency checksum list now also accepts the Linux build of the Android resource tool aapt2
   (checksum from Google's own published value), because upstream only builds on Windows and Lynx builds on Linux.

## New in 0.24.0: what the review found

Nothing was removed. Worth knowing before you install:
1. In-app proxy (a new row in Hushfacebook settings) is blank by default and does nothing while blank. If you
   type a proxy there, every Facebook connection goes through it: the proxy can see which servers Facebook talks
   to (not the content, which stays encrypted), one safety check on download addresses is relaxed, and a proxy
   that stops working blocks Facebook, sign-in included, until you clear the row. It is not part of settings
   backup or import. Leave it blank unless you need it.
2. The crash file is on by default, as upstream ships it. After a crash, a freeze or a memory kill, a report with
   personal details removed goes to the phone's Download/Hushfacebook folder (at most 5 files). Since 0.24.0 it
   also covers native crashes and freezes and starts on the login screen. Switch it off in settings if unwanted.
3. Some new switches act for you, all off by default: "Next reel in background" (plays reels with the screen off,
   so Meta records plays you did not watch), "Translate every comment" (taps Facebook's own See translation on
   each comment), "Save all videos" in the download menu (up to 50 videos and 50 photos from one tap). Leave them
   off to stay close to normal use.
4. No new network destination, no new permission and no new exported component came with 0.24.0. Checked on the
   patched app against the original Facebook: no Android permission added or removed, and the only added
   components are the "Open in Facebook" share target and a file provider for photo search, both already in
   0.23.0. One new patch, "Video buffer": applied by default, but it changes nothing until you pick a value.

## What was reviewed and what was not

The 0.23.0 to 0.24.0 update was reviewed before it was committed: `upstream-src/_audit/hushfacebook-v0.24.0-review.md`
(local, not in git; verdict: safe to build). Build files were read completely and every added line was scanned for
13 risk categories (network, loading code, permissions and so on); network, proxy, download, crash and settings
code was read closely, many layout and reels files only scanned. The first audit of the whole pack is
`hushfacebook-report.md` in the same folder. The bundle was applied to the original Facebook 588.0.0.42.55 with
Morphe Desktop 2.3.0 and `--validate`: of 127 patches (122 on by default) 122 applied, 0 failed, no warning. With
the two off-by-default theme patches ("AMOLED black theme", "Material You theme", which use a looser matcher) on
too: 124 applied, 0 failed. This proves the patches fit this build, not that every feature works on a phone.

## Upstream documentation that does not apply to this build

The upstream README, docs and CONTRIBUTING still point at upstream's own source and releases. For this build: add
the Lynx source (below), report at https://github.com/xhyxfxxc/lynx-patches/issues, and there is no Hush Unpacker.
Ignore README.md lines 4, 12-13, 25, 27, 35 (badge, Ko-fi, upstream release and add-source links), 118 (upstream
Discussions and forms), 149 (the release check reads upstream releases), 164 and 175-179 (Hush Unpacker, upstream
checksums), docs/help.md lines 3, 13, 55, 106, and CONTRIBUTING.md (upstream tracker and release steps).

## Known problems that are not fixed

- Upstream issue 551 (open, against 0.24.0 on a Galaxy S26 Ultra with Android 17): on Reels the layout sits too
  low and the bottom bar covers the description and buttons. Issue 552 (open): video stories can freeze, the seek
  bar sits low, and reel captions break when "Keep progress bar" and "Keep the tab bar on reels" are both on.
- Upstream issue 528 (open): a crash at launch on 0.23.0 with Android 17, cause unknown (the pack pauses itself
  after three crashes in a minute). Issues 505 and 548 (on 0.23.0): double seek bar and battery drain, not analysed.
- Sign-in can loop on accounts that have a passkey (upstream issue 273, closed with a workaround): remove the
  passkey in Accounts Center, sign in, then add it again.
- The re-signed Facebook accepts its own signing key in place of Meta's for the Meta apps on the phone, so it is
  only as private as the key Morphe Manager signs with. Keep that key private.

## How to build

In Git Bash, from this folder (needs JDK 21, the Android SDK and a GitHub token that can read packages):

    export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
    export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
    ./gradlew :patches:generatePatchesList :patches:buildAndroid -Pversion=0.24.0-lynx.1 --no-daemon

The finished bundle is `patches/build/release/patches-0.24.0-lynx.1.mpp` (the copy in `patches/build/libs` is
an intermediate file). It is unsigned. Built inside the Lynx repository the bundle says its source state is
"unknown", because the identity check only trusts a repository whose root is this folder. The build also changes
the version line of the tracked `patches-list.json`; restore it with `git checkout -- patches-list.json`.

## How to add it to Morphe Manager

On the phone: Sources, plus, paste

    https://raw.githubusercontent.com/xhyxfxxc/lynx-patches/main/packs/facebook/patches-bundle.json
