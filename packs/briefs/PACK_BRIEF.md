# Pack port brief: rules and steps common to every pack

You port ONE third-party Morphe patch pack that has already been imported, unchanged, into
`C:\Users\thoma\Morpheapp\lynx-patches\packs\<name>\`. Your prompt names the pack and lists its
"Lynx delta" (the changes to make). This file holds what is common to all packs.

## Background

- The repo is the owner's public "Lynx Patches" repo (GitHub xhyxfxxc/lynx-patches, branch main).
  Read `CLAUDE.md` and `packs/README.md` in it first. Each pack under `packs/<name>/` is a source
  snapshot of an upstream project (GPL-3.0) and is built by its OWN Gradle build in that folder.
  Lynx CI later publishes it as its own Morphe source.
- A security audit of the pack exists at `C:\Users\thoma\Morpheapp\upstream-src\_audit\<auditname>-report.md`
  (your prompt gives the file). Read the sections your prompt points to. It has `path:line` evidence
  relative to the upstream repo root, which is now `packs/<name>/`.
- Terms: PATCHES are Kotlin under `patches/` that run at patch time and rewrite the target app.
  The EXTENSION is Java under `extensions/` that is injected into the app and runs inside it.
  A bundle (`.mpp`) is the built pack. Morphe Desktop applies a bundle to an original APK on the PC.
- The owner is new to Android patching. Anything you write for them (LYNX.md) must be plain and
  define its terms.

## Hard rules

If your prompt says you are the only session in the repo, rules 1 and 2 relax as the prompt
states (for example it may let you edit packs/packs.json and one table row in packs/README.md,
and use git pull --rebase when a push is rejected).

1. Other sessions work on OTHER packs in this same checkout at the same time. You may only create,
   change or delete files under `packs/<name>/`. Do not edit `packs/packs.json` (report the values
   instead), nothing else in the repo, nothing outside it except your output folder
   `C:\Users\thoma\Morpheapp\out\<name>\lynx\` and a sub-folder named `pack-<name>\` inside the
   scratch folder your prompt gives you.
2. Git, because the working tree is shared: never run `git add -A`, `git add .`, `git commit -a`,
   `git stash`, `git reset`, `git checkout -- <path outside your pack>`, `git clean`, `git pull`,
   `git rebase`, `git merge`, branch or worktree commands. Stage with `git add packs/<name>` and
   commit with a pathspec: `git commit -m "<message>" -- packs/<name>`. If git reports that
   `index.lock` exists, wait 5 seconds and retry (up to 12 times); never delete the lock file.
3. Every commit message is `chore(pack-<name>): <plain sentence saying what changed and why>`.
   Never `fix:` or `feat:` (those publish a release of the main Lynx bundle). One logical change per
   commit. End each commit message with the line
   `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
4. Push at every milestone with `git push origin main` (your own commits and any other session's
   commits go together; that is fine). If a push is rejected, do not pull or rebase: wait one minute,
   try once more, and if it is still rejected say so in your final report.
5. Never read, build or run anything in `C:\Users\thoma\Morpheapp\upstream-src\<repo clones>`.
   The copy under `packs/<name>/` was audited and its Gradle build may be run.
6. Do not touch the phone (no adb). Do not install anything system-wide. Do not create GitHub
   releases, tags or workflow runs.
7. Never print or log the GitHub token. Use it only through the environment variable.
8. Writing style in everything you write (code comments, LYNX.md, commit messages): never use em
   dashes (the long dash). Use commas, colons, parentheses or periods. ASCII only in new docs.
9. Keep the diff against upstream SMALL. Future upstream versions are merged in with a 3-way merge,
   and every line we touch can conflict. Prefer: changing one default, removing one registration,
   deleting whole files of a removed feature. Avoid: reformatting, renaming, moving code, drive-by
   fixes. Do not rename upstream's internal identifiers, packages or branding strings in code.
10. Keep every upstream copyright header, LICENSE and the upstream NOTICE text. Files you add may
    carry `Copyright (C) 2026 xhyxfxxc` plus `SPDX-License-Identifier: GPL-3.0-or-later`.
11. If something in the delta turns out to be wrong or impossible (the code is not what the brief
    says), stop that item, keep the rest, and report exactly what you found. Do not improvise a
    different security decision.

## Steps

### 1. Apply the Lynx delta
The numbered items in your prompt, one commit each. When an item removes a feature, remove the
patch, the extension classes only it uses, its strings, its tests and its entry in any registry or
generated list the build checks, so the build and its own verification tasks stay green. When an
item changes a default, change only that default (and any test that pins it).

### 2. Mark the pack as a modified version (license duty, GPL-3.0 section 7)
- In the Gradle `about { ... }` block of the patches module: `name` becomes the title given in your
  prompt (for example "HushGram (Lynx build)"), `source` and `website` become
  `https://github.com/xhyxfxxc/lynx-patches/tree/main/packs/<name>`, `author` becomes
  `"<upstream author>, modified by xhyxfxxc"`. Leave `license` as is. If other code compares the
  bundle name (grep for the old name in `*.kt`, `*.kts`, `*.java`), keep those checks working.
- Add this section at the very top of `packs/<name>/NOTICE`, above the unchanged upstream text
  (fill in the angle brackets; underline with `=` to the title's length):

      <Title> NOTICE
      ==============

      This is a modified version of <Upstream name> <upstream version> by <upstream author>
      (<upstream URL>), built and published by xhyxfxxc as part of Lynx Patches
      (https://github.com/xhyxfxxc/lynx-patches/tree/main/packs/<name>). It is not the original
      and its author has not endorsed it. What was changed is listed in LYNX.md in that folder.
      The notices of the original follow unchanged.

  If the build generates code or tests from NOTICE (the Hush packs do), make them pass.
- "Report a problem" style links that open the UPSTREAM issue tracker pre-filled with device
  details must point to `https://github.com/xhyxfxxc/lynx-patches/issues/new` instead (a modified
  build must not send its bug reports to the original author). Donation links to the upstream
  author and credit lines stay exactly as they are. An update check that only tells the user that
  the upstream project released something stays as it is (leave its default alone).
- Do not rename the app-facing branding inside the app (settings titles and so on).

### 3. Write `packs/<name>/LYNX.md`
Short and plain. Sections: What this is (one paragraph: which app, which upstream, which upstream
version and commit, the Lynx version); Why it is here (reviewed copy, built from this source by Lynx
CI); What Lynx changed (a numbered list, one entry per delta item: what, why, in plain words, with
the file names); What was reviewed and what was not (two or three lines pointing at the audit);
Known problems that are not fixed (from your prompt); How to build (the exact command you used);
How to add it to Morphe Manager (the raw URL from packs/README.md). Under 80 lines.

### 4. Build the bundle locally
In Git Bash:

    export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
    export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
    cd /c/Users/thoma/Morpheapp/lynx-patches/packs/<name>
    ./gradlew <tasks from your prompt> -Pversion=<version from your prompt> --no-daemon

- Use `--no-daemon` and do not raise Gradle's memory above what the pack sets (several builds run
  on this PC at once). If `./gradlew` fails to start because of line endings, run it with
  `sh gradlew`, or use `gradlew.bat` through `cmd //c`.
- Build problems that come only from the pack now living in a subfolder of another repo (a task
  that expects the repo root, a file we excluded on import such as `patches-bundle.json` or
  `.github/`, a release-only check, a signing key we do not have, the version string
  `<x>-lynx.<n>`) are fixed with the smallest possible change inside `packs/<name>/`, each as its
  own commit, each listed in LYNX.md. Never weaken a check that guards what goes INTO the bundle
  without saying so in your report. The bundle is published unsigned: do not generate or add
  signing keys; if a published public key file makes the build demand a signature, remove that
  file and say so.
- If the version string with a `-lynx.<n>` suffix is rejected by the build, try to make the build
  accept it with a minimal change; if that is not reasonable, use `<upstream version>.<n>` and
  report it.
- Record the exact tasks, the output path and the sha256 of the bundle.

### 5. Validate on the original app
    cd /c/Users/thoma/Morpheapp
    "$JAVA_HOME/bin/java" -Xmx6g -jar tools/morphe-desktop-<version>-all.jar patch \
      -p <your bundle> --validate \
      --keystore tools/morphe-data/morphe.keystore --keystore-entry-alias Morphe --keystore-entry-password Morphe \
      -o out/<name>/lynx/<name>-lynx.apk -r out/<name>/lynx/result.json \
      <the original APK bundle from your prompt>  > out/<name>/lynx/validate.log 2>&1
- `tools/morphe-desktop-<version>-all.jar` is the jar your prompt names (a bundle built against a
  newer patcher needs a newer jar).
- Every patch that is on by default must report `Applied:`. Compare the applied list with the
  upstream baseline in `out/<name>/upstream-baseline/validate.log` (made with the upstream author's
  released bundle on the same APK): the only differences allowed are the ones your delta causes.
  Report the two lists' differences exactly.
- Never pass `-f` (force). If a patch fails, find out why and report; do not hide it.

### 6. Static checks on the patched APK
The checks listed in your prompt, done on `out/<name>/lynx/<name>-lynx.apk` with the SDK tools
(`/c/Users/thoma/Android/Sdk/build-tools/36.0.0/dexdump.exe`, `aapt2.exe dump xmltree --file
AndroidManifest.xml`, `unzip`). Quote the evidence (counts, matching lines) in your report. Also
always report: the permissions and the exported components that the patched manifest has and the
ORIGINAL manifest (inside the original bundle's `base.apk`, already unpacked next to it in
`unpacked/base.apk`) does not have.

### 7. Tests
If the pack has unit tests that cover code you changed and they run without a phone or private
fixtures, run just those (time box: 20 minutes) and report the result. Do not chase unrelated
failing tests; list them.

## Final report (under 70 lines)
Commits (sha and subject), the build command, bundle path, size and sha256, validation result
(count applied, differences from baseline), static check evidence, new permissions and exported
components versus the original, tests run, every assumption or deviation, everything not done, and
the values for this pack's `build` entry in packs/packs.json (java, tasks, output glob, args) and
the version string that worked.
