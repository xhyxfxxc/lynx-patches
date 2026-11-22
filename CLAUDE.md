# Lynx Patches: working rules

Lynx Patches is a public fork of Loki Patches by Lokesh Sparrow
(https://github.com/lokesh-sparrow/loki-patches), forked on 2026-11-21 at upstream v3.4.0
(commit 255e485). Patches for use with Morphe. Repo: https://github.com/xhyxfxxc/lynx-patches.

## License duties (GPL-3.0-or-later plus NOTICE section 7 terms; owner decision 2026-11-21)

- Keep the line "Loki Patches by Lokesh Sparrow" in the README, in NOTICE and in the in-app
  About/Credits screen. Never remove or reword the upstream NOTICE sections; Lynx adds its own
  section above them.
- The product name is "Lynx Patches". Never use "Loki" or "Morphe" as (part of) the product
  name. "Based on Loki Patches" and "for use with Morphe" are fine as descriptions.
- Never present upstream code as our own. Keep every existing `Copyright (C) 2026 Lokesh Sparrow`
  header. New files may carry `Copyright (C) 2026 xhyxfxxc`.
- Share patches only, never patched APKs.

## Keep upstream merges cheap

- Only user-visible branding was renamed. Internal identifiers stay as upstream named them:
  packages `app.loki.*`, classes like `LokiLog`, resource names `loki_logo` / `loki_glyph`,
  manifest keys `app.loki.menu.*`, Logcat tag "Loki", URL scheme `lokipatches://`, backup
  format id `loki-patches-settings`, shortcut id `loki-patches`. Do not rename them.
- The bundle name "Lynx Patches" in `patches/build.gradle.kts` (`about { name }`) must match the
  manifest Name checks in `SharedPatches.kt` (bundle version lookup) and `MenuPatch.kt`.
- Sync: `git fetch upstream` then `git merge upstream/main` (merge commit, never rebase or
  force-push). Upstream tags are not fetched (`remote.upstream.tagOpt --no-tags`) because our
  version numbers diverge from theirs.
- On conflicts: take upstream's logic, keep our brand strings, URLs, credits and logo. For the
  generated files (CHANGELOG.md, patches-list.json, patches-bundle.json, the README blocks
  between the START/END markers) keep ours; the next release regenerates them.
- After every merge, rebrand anything new:
  `grep -rn -e "Loki Patches" -e "lokesh-sparrow/loki-patches" --exclude-dir=.git --exclude=NOTICE --exclude=CHANGELOG.md .`
  must only show the intended credit lines. When a source string changes, change the same key
  in every file under `extensions/extension/src/main/translations/` (the English text is the key).

## Upstream check (on demand only; owner decision 2026-11-21)

Lynx is for the owner's own use. Never sync upstream automatically. When the owner asks to
"check upstream" (or a LinkedIn update breaks a patch):
1. `git fetch upstream`, then `git log --oneline main..upstream/main` and
   `git diff --stat main...upstream/main`.
2. Summarize for the owner in plain words, LinkedIn changes first: fixed fingerprints, newly
   verified LinkedIn builds (`VERIFIED_LINKEDIN_BUILDS` in
   `patches/src/main/kotlin/app/linkedin/patches/shared/Constants.kt`), new patches, other apps.
3. Merge or cherry-pick (`git cherry-pick -x`) only what the owner picks, rebrand per the rules
   above, build, run `tools/audit.py`, then commit and push.

Amazon Shopping has a second source: its patches were ported by hand on 2026-11-21 from
rushiranpise/morphe-patches v1.23.1 (GPL-3.0; credited in NOTICE and the in-app Credits), into
`app/amazon/patches/` and `app/amazon/extension/`. It is not a git remote (different layout). On
"check upstream" for Amazon, diff that repo's `patches/src/main/kotlin/app/template/patches/amazon/`
and `extensions/.../AmazonHelper.java` / `AmazonUrls.java` since v1.23.1 and port by hand what the
owner picks. Our Amazon code has since diverged (clone support, link policy, dark mode, autoplay,
manifest permissions, price history card), so never copy their files over ours.

## Packs: other authors' patch packs, published as separate sources (owner decision 2026-11-23)

- `packs/<name>/` is a source snapshot of someone else's Morphe patch pack plus our changes. Each pack keeps
  its own Gradle build and is released by `.github/workflows/pack.yml` as its own Morphe source
  (`packs/<name>/patches-bundle.json`, written only by that workflow). Registry: `packs/packs.json`.
  How-to and filter rules: `packs/README.md`. They are not part of the Lynx bundle and not built by the root build.
- Packs: facebook (Hushfacebook), instagram (HushGram), threads (HushThreads), gboard (Gboard-patches),
  waze (bearinmind patches, Waze files only), x (Piko, X half only). All GPL-3.0.
- Commits that touch packs are `chore(pack-<name>): ...` or `chore(packs): ...`. Never `fix:` or `feat:` there:
  those would publish a new Lynx bundle.
- Review gate: `python tools/pack_sync.py sync <name> <tag>` merges a new upstream version but never commits.
  Read the upstream diff it writes to `build/pack-sync/` for anything that sends data off the phone, loads code,
  adds permissions or exported components, or changes the build, and only then commit. Never run a pack's build
  on code that has not been reviewed. The first audits (2026-11-23) are in
  `C:\Users\thoma\Morpheapp\upstream-src\_audit\` (local, not in git).
- Everything we changed in a pack is listed in `packs/<name>/LYNX.md`; keep it current.
  `python tools/pack_sync.py diff <name>` shows the exact difference from upstream.
- Release a pack: raise `version` in `packs/packs.json` (`<upstream version>-lynx.<n>`), apply the built bundle
  with Morphe Desktop `--validate` to the original APK in `apks\` (every default patch must apply), test on the
  phone, ask the owner before a pack's first release, then
  `gh workflow run Pack -R xhyxfxxc/lynx-patches -f pack=<name>`.
- License duties: keep each pack's LICENSE and upstream NOTICE text, name it "<Upstream name> (Lynx build)",
  never publish upstream's own `patches-bundle.json` or signing key from our folders.
- CI lessons (2026-11-23): dispatch one Pack run at a time and wait for it (`gh run watch`), because the workflow's
  concurrency group cancels older queued runs. A Maven Central `429 Too Many Requests` or any other download error
  in the build is transient: run it again. The three Hush packs (facebook, instagram, threads) are built upstream
  only on Windows: when a dependency change fails Gradle dependency verification on Linux CI, add the Linux
  artifact's checksum as published by its vendor to that pack's `gradle/verification-metadata.xml`; never switch
  verification off.
- After a pack release: check that
  `https://raw.githubusercontent.com/xhyxfxxc/lynx-patches/main/packs/<name>/patches-bundle.json` answers 200,
  download the asset (`gh release download pack-<name>-v<version> -R xhyxfxxc/lynx-patches`) and confirm its
  `classes.dex` and `extensions/*` are byte-identical to the bundle that was validated and tested (only `META-INF`
  build stamps and CRLF/LF in text files may differ), then update the Packs table in `packs/README.md`. The Lynx
  Patches release must stay the one marked Latest.
- One-tap add link (checked in Morphe Manager 2.7.0):
  `https://morphe.software/add-source?github=xhyxfxxc/lynx-patches/tree/main/packs/<name>&name=<label>`. Manager
  shows the bundle's own name ("<Upstream name> (Lynx build)") once the source has loaded.

## Branches, commits, releases

- Everything lands on `main` (upstream's design: a push to `main` is a release). Commit and
  `git push origin main` at every milestone.
- `chore:` no release, `fix:` patch release, `feat:` minor release. Releases are made only by
  `.github/workflows/release.yml`; never create or upload one by hand. Never hand-edit the
  generated files listed above.
- Ask the owner before pushing the first `feat:`/`fix:` that would publish a release.

## Build

Needs JDK 21 and the Android SDK (platform 36), and a GitHub token with `read:packages`.
In Git Bash from the repo root:

```bash
export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
./gradlew :patches:buildAndroid   # output: patches/build/libs/patches-*.mpp
python tools/audit.py             # the same audit CI runs before a release
```

Apply with Morphe Desktop, check fingerprints with `--validate`. Original APKs live in
`C:\Users\thoma\Morpheapp\apks\` (outside the repo); never commit APKs.
