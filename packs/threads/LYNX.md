# HushThreads (Lynx build)

## What this is

A copy of the patch pack HushThreads 0.0.18 by SysAdminDoc (https://github.com/SysAdminDoc/HushThreads,
commit d50db1f), changed in a few places listed below. It patches the Threads app (package
`com.instagram.barcelona`, version 455.0.0.55.72) when you apply it with Morphe. Lynx version:
0.0.18-lynx.1. A "patch" is a recipe that changes the Threads app when you patch it. A "bundle" is the
built pack (a `.mpp` file) that Morphe Manager downloads. The pack is GPL-3.0; its LICENSE and NOTICE are kept.

## Why it is here

The source was reviewed before it was copied, and Lynx CI builds the bundle from this folder, so what you
install is the code you can read here and not a file somebody else uploaded. It is its own Morphe source,
separate from the main Lynx Patches bundle.

## What Lynx changed

1. Removed the patch "Trust user-added certificates". It rewrote Threads' network security config so that
   certificates a user installs are trusted and Meta's certificate pins are overridden, which lets anyone who
   gets a certificate onto the phone read Threads' traffic. Deleted `TrustUserCertificatesPatch.kt` and its test,
   the settings row and status stub in the extension, its translation rows and its entries in
   `patches-list.json`, `provenance.json`, `README.md` and `scripts/resource-file-allowlist.txt`.
2. Removed `morphe-signing.pub`, the upstream author's public bundle-signing key. A Lynx bundle is not signed by
   the upstream author and must not publish their key. `patches/build.gradle.kts` now ignores any signing key in
   the environment and does not read the key file when it is missing, and `BundleVerifier.kt` then checks that the
   bundle is unsigned. The bundle is published unsigned.
3. "Report a problem" (settings screen, Support) now opens https://github.com/xhyxfxxc/lynx-patches/issues/new
   with the versions, phone and switches in the issue body, instead of the upstream author's issue form. Files:
   `ReportProblemForm.java` and two tests; the `contact` link in the `about` block of
   `patches/build.gradle.kts` points to the Lynx issues page too. The Ko-fi link, the source-code credit link and
   the release check (off by default, it only tells you that upstream released something) are unchanged.
4. Marked as a modified version: the bundle is named "HushThreads (Lynx build)", its source and website point to
   this folder, its author reads "SysAdminDoc, modified by xhyxfxxc" (the `about` block in
   `patches/build.gradle.kts`), `NOTICE` has a Lynx section on top of the unchanged original text, and this file
   was added (plus one line in `.gitignore` so git tracks it). Settings titles inside the app still say HushThreads.
5. Version and patch list: the build version is `0.0.18-lynx.1` and `patches-list.json` was regenerated for it.

README.md is the upstream text and still describes the upstream project, including its signed releases. A Lynx
bundle is not signed.

## What was reviewed and what was not

The whole source of the pack was audited for data leaving the phone, loaded code, permissions and build
behaviour before this copy was made; the audit found no theft or hidden network use. Its report lists the
findings (F1 to F11) and the suspected start-up risks (section 5). Not reviewed: anything inside Threads itself,
and how Meta reacts to a patched app. No patched APK is shared, only the patches.

## Known problems that are not fixed

- The pack makes the re-signed Threads accept its own signing key in place of Meta's, so it is only as private as
  the key Morphe Manager signs with (audit F1). Keep that key to yourself.
- Meta may detect a patched Threads, and the Threads account is shared with Instagram, so a ban can reach both.
- Suspected and unproven start-up risks from the audit (section 5):
  - P1-1: a failure in the first call the pack makes when Threads starts is not caught and could stop Threads at launch.
  - P1-2: two hooks every build needs are strict, so any Threads build other than 455.0.0.55.72 fails the whole patch run.
  - P1-3: "Disable analytics" sends Threads' uploads to a dead local address, which could cause endless retries.
  - P1-4: the hook at app start could be skipped on one code path.
  - P1-5: one patch picks a scratch register in a way that ignores catch handlers.

## How to build

In Git Bash, from `packs/threads` (JDK 21, Android SDK, a GitHub token with `read:packages`):

    export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
    export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
    ./gradlew :patches:generatePatchesList :patches:buildAndroid -Pversion=0.0.18-lynx.1 --no-daemon

The bundle is written to `patches/build/release/patches-0.0.18-lynx.1.mpp`.

## How to add it to Morphe Manager

On the phone: Sources, plus, paste

    https://raw.githubusercontent.com/xhyxfxxc/lynx-patches/main/packs/threads/patches-bundle.json
