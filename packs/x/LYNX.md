# Piko for X (Lynx build)

## What this is
Patches for the X app (Twitter, package `com.twitter.android`), version 12.19.1-release.0 only. They are
Piko 3.11.1 by crimera (https://github.com/crimera/piko, commit 2975196), reviewed and changed by Lynx.
Lynx version: 3.11.1-lynx.1. Piko also patches Instagram: that half was not reviewed and is not in this
pack, so this bundle patches X only.

Terms: a patch is code that runs on the PC and rewrites the app; the extension is Java code added to the
app that runs inside it; the bundle is the built .mpp file; a fingerprint is how a patch finds its place.

## Why it is here
A reviewed copy of Piko's source (GPL-3.0), built by Lynx CI from this folder, so the bundle is what was
read and not the upstream author's binary. The changes below are on top of it.

## What Lynx changed
1. Builds without the Instagram half. `DisableAutoScrollPatch.kt` imported a small helper from the Instagram
   code; it now has its own copy in `twitter/utils/Utils.kt`. `DisablePlayStoreUpdates.kt` no longer lists
   Instagram. `CheckString.kt` (the optional string check) no longer looks for Instagram strings.
2. Removed the translator provider "Google translator V2" (`GTranslateV2.java`). It sent the text of posts
   to ftapi.pythonanywhere.com, a private person's free hosting account. A saved setting that pointed at it
   now uses the default provider (Google). Audit F2.
3. Removed the patch "Log server response" (`ResponseLogger.java`, its patch, settings and strings). Switched
   on, it wrote every server answer, direct messages and login answers included, to a text file in the public
   Download folder. Audit F4.
4. Removed the patch "Show changelogs" (`Changelogs.java`, its patch, settings and strings, and the HTTP
   helper classes only it used). On by default, it downloaded CHANGELOG.md from the upstream author's GitHub
   branch and showed it with clickable links. Audit F3.
5. "Native reader mode" is not selected by default (`default = false`, as "Export all activities" is). Its
   code is unchanged. It loads posts from the third-party site twitter-thread.com into a web view. Audit F1.
6. Fixed a start-up crash: one malformed saved feature flag (for example after restoring a wrong file) made
   X crash at every start. `FeatureSwitchPatch.java` and `FeatureFlagsFragment.java` skip such entries;
   `RestorePrefFragment.java` keeps only well-formed entries. Audit section 5.
7. Made three fingerprints exact so that strict validation passes (Morphe Manager does not validate and
   silently takes the first match): `TweetEntityPatch.kt` (tweet text class, matched two classes; seven
   patches depend on it), `HideFABPatch.kt` (also matched the pack's own extension class) and
   `DefaultReplySortingPatch.kt` (matched three methods, now one).
8. Added upstream's later fix "Spoof client version" (Piko commit 8d265d8, by the Piko authors): two new
   files, unchanged. It fixes the "Update Required" screen in X Chat (direct messages) by reporting a newer
   app version (12.44.0-prod.01) on X Chat requests only. It shows under the category "Old patches".
9. Marked as a modified version: bundle name, source, website and author in `patches/build.gradle.kts`, a
   Lynx section at the top of `NOTICE` (the Piko text below it is unchanged), and this file.
   `patches-list.json` was regenerated (`generatePatchesList`): 74 X patches.

## What was reviewed and what was not

The X half was audited, partly by reading and partly by search (`_audit/piko-report.md` in the Morpheapp
work folder): no code found reads cookies, tokens or messages and sends them anywhere. The Instagram half
was not audited and is not here.
Instagram-only helper code inside `extensions/shared` is still in the bundle but nothing in X calls it.
The Morphe library classes inside the bundle were only scanned.

## Known problems that are not fixed
- Some phones are refused at sign-in with "use the official app" (Google Play Integrity; upstream issues
  2075, 2092, 2029). It cannot be fixed in the pack.
- Only the old X app 12.19.1 is supported. Upstream's pre-release 3.12.0-dev.5 adds the rewritten X app
  (12.41 and 12.43) in about 69,000 new lines that have not been reviewed and are not included.
- The translator sends the text of a post to Google when you tap Translate.
- "Import/Export login token" can copy the account's login token to the clipboard or a file when you use it.
- Several patches switch on paid (Premium) client features locally, which X could notice.
- Crash logs are written to the public Download folder.
- The "source label" option waits on the main thread (off by default).
- "Spoof client version" tells X Chat's servers a different app version than the real one.

## How to build

```
export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
cd packs/x
./gradlew :patches:buildAndroid -Pversion=3.11.1-lynx.1 --no-daemon
```

Output: `patches/build/libs/patches-3.11.1-lynx.1.mpp`. Optional check: `:patches:checkStringResources`.

## How to add it to Morphe Manager

On the phone: Sources, plus, paste
`https://raw.githubusercontent.com/xhyxfxxc/lynx-patches/main/packs/x/patches-bundle.json`
