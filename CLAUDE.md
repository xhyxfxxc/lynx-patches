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
