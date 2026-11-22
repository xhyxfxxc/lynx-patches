# Hushfacebook (Lynx build)

## What this is

Patches for the Facebook app (package `com.facebook.katana`, built for version 587.0.0.39.55), made for use
with Morphe. Upstream is Hushfacebook 0.23.0 by SysAdminDoc (https://github.com/SysAdminDoc/HushFacebook),
commit 7c0e085. The Lynx version is 0.23.0-lynx.1. Terms: a "patch" rewrites the Facebook app on a PC before it
is installed; the "extension" is Java code that the patches add to the app and that then runs inside it; the
"bundle" (a `.mpp` file) is the finished pack that Morphe applies.

## Why it is here

This is a reviewed copy of the upstream source (see "What was reviewed" below). Lynx CI builds the bundle from
this folder and publishes it as its own Morphe source, so a bundle from here is built from exactly this code and
not from the upstream author's released file.

## What Lynx changed

1. "Report a problem" and "Source code and issues" point at Lynx. Report a problem now opens a new issue at
   https://github.com/xhyxfxxc/lynx-patches/issues/new (with the versions, phone model and the switches that are
   on filled in as the text), and the settings "source" link opens
   https://github.com/xhyxfxxc/lynx-patches/tree/main/packs/facebook. Before, both went to the upstream
   author's repository, which is not where bugs of a modified build belong. Lynx's own bug form has no fields
   for this pack, so the report text goes in the issue body and no issue form is named. The Ko-fi link and the
   release check are unchanged (the release check is off by default and still reads the upstream author's
   releases). Files: `extensions/facebook/.../settings/HushfacebookPreferenceFragment.java`,
   `ReportProblemForm.java`, and the five tests that pinned the old addresses.
2. Marked as a modified version, as the GPL asks: the bundle's name, source, website and author in
   `patches/build.gradle.kts` (this is what Morphe Manager shows), and a Lynx section at the top of `NOTICE`
   (the upstream text below it is unchanged and still shown in the app under Licenses).
3. Build fix: `gradle/verification-metadata.xml` had no checksum for three small metadata files of test
   libraries (junit-bom 5.14.4 and the Bouncy Castle BOM 1.85; none is part of the bundle), so the build
   stopped on this PC. The checksums were added only after matching each file with the SHA-256 that Maven
   Central publishes for it.
4. Build fix: `helper/build.gradle.kts` split the version at the dots and read every part as a number, so a
   version with a suffix such as 0.23.0-lynx.1 stopped the whole build. It now reads only the part before the
   dash.
5. Hush Unpacker (the `helper/` app, only needed to patch the Android 9 and 10 build of Facebook) is not
   published by Lynx. Its source is left where it is. Its APK is not built or shipped, and the bundle build does
   not need it (it only builds it when `helper/keystore.properties` exists, which it does not here).
6. Build fix: the dependency checksum list now also accepts the Linux build of the Android resource tool aapt2
   (checksum from Google's own published value), because upstream only builds on Windows and Lynx builds on Linux.

## What was reviewed and what was not

The pack was audited as source before import: `upstream-src/_audit/hushfacebook-report.md` (verdict: safe with
changes, no network path that sends your data to the author, no new permissions of its own). The audit read the
network, download, signature, settings and build code closely and only searched the other ~660 source files. The
bundle was built here and applied to the original Facebook 587.0.0.39.55 with `--validate`; that proves the
patches still fit, not that every feature works on a phone.

## Known problems that are not fixed

- Sign-in can loop on accounts that have a passkey (upstream issue 273). Workaround: remove the passkey in
  Accounts Center, sign in, then add it again.
- One report of a crash at launch on 587.0.0.39.55 (upstream issue 528, cause unknown). The pack pauses itself
  after three crashes in a minute.
- A fresh start can show an old or empty Home feed (upstream issue 493, fixed upstream after 0.23.0, not here).
- Battery drain reports (upstream issues 548 and 505), not analysed.
- The pack makes the re-signed Facebook accept its own signing key in place of Meta's for the Meta apps on the
  phone, so it is only as private as the key Morphe Manager signs with. Keep that key private.

## How to build

In Git Bash, from this folder (needs JDK 21, the Android SDK and a GitHub token that can read packages):

    export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
    export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
    ./gradlew :patches:generatePatchesList :patches:buildAndroid -Pversion=0.23.0-lynx.1 --no-daemon

The finished bundle is `patches/build/release/patches-0.23.0-lynx.1.mpp` (the copy in `patches/build/libs` is
an intermediate file). It is unsigned. Built inside the Lynx repository the bundle says its source state is
"unknown", because the identity check only trusts a repository whose root is this folder.

## How to add it to Morphe Manager

On the phone: Sources, plus, paste

    https://raw.githubusercontent.com/xhyxfxxc/lynx-patches/main/packs/facebook/patches-bundle.json
