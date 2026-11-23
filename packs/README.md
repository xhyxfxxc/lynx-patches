# Packs: third-party patch packs inside Lynx

A pack is somebody else's Morphe patch pack (for one app, such as Facebook or Waze) that lives in this repo as a
source snapshot under `packs/<name>/`, is kept up to date from its upstream, carries our own fixes on top, and is
built and published by Lynx CI as its own Morphe source.

The order of work for adopting and upgrading a pack is in [PROCESS.md](PROCESS.md); the briefs for the review and
port sessions are in [briefs/](briefs/).

Why a pack is its own source and not part of the Lynx Patches bundle:

- Every pack was written against its own patcher toolkit version and has its own Gradle build, so the packs
  cannot share one build. Each one is built by its own `gradlew` in its own folder.
- Packs use the same internal names (`app.morphe...`, same class names), which clash when two are merged.
- A pack can be updated on its own. A small fix to one app does not ship a new bundle to everyone.

## Layout

```
packs/
  packs.json             the list: one entry per pack (schema below)
  README.md              this file
  PROCESS.md             the order of work: adopt, review, fix, publish, upgrade
  briefs/                briefs for review and port sessions
  <name>/                the pack, as upstream wrote it plus our fix commits
    gradlew, settings.gradle.kts, ...   upstream's own build
    LYNX.md              what we changed, in plain words (our file; it becomes the release notes)
    patches-bundle.json  written by CI only, never by hand: it is what Morphe Manager reads
```

Pull results are written to `build/pack-sync/` (git-ignored), never committed.

## packs.json

A JSON object `{"packs": {...}}` with a top-level `_about` text. One entry per pack, keyed by the pack name
(lowercase letters, digits, dashes). The tool rewrites the file in a fixed format (2 space indent), so keep it that way.

```
"facebook": {
  "title":    "Hushfacebook (Lynx build)",      display name of the bundle, informational
  "app":      "com.facebook.katana",            package of the target app, informational
  "upstream": "https://github.com/SysAdminDoc/HushFacebook",   any git URL (the tests use local paths)
  "ref":      "v0.23.0",                        upstream tag or branch that was imported
  "commit":   "<40 hex sha>",                   what ref resolved to; written by the tool, never by hand
  "include":  [],                               optional glob list; empty or missing means everything
  "exclude":  ["patches-bundle.json", ".github/**"],     glob list
  "version":  "0.23.0-lynx.1",                  the version our CI builds and releases (raise it for every release)
  "build":    {"java": "21", "tasks": ":patches:buildAndroid", "output": "patches/build/libs/patches-*.mpp", "args": ""}
}
```

Only `upstream` and `ref` are needed to import. Unknown keys are an error (a typo such as `excludes` must not
silently switch the filter off). Keys that start with `_` are free notes.

### Filter rules

Paths are relative to the upstream repo root, with `/`. A file is kept when (`include` is empty OR it matches at
least one include pattern) AND it matches no exclude pattern.

- `**` as a whole path segment matches any number of segments, none included. As the last segment it means
  everything under that directory.
- `*` matches inside one segment and never crosses `/`.
- A pattern ending in `/` means "everything under that directory": `docs/` is the same as `docs/**`.
- Patterns are anchored at the upstream root. A pattern without any `/` matches only the root file of that name:
  `patches-bundle.json` is the root file, never `sub/patches-bundle.json`. To drop a whole folder write `docs/`
  (a plain `docs` would only match a root file called `docs`).
- Nothing else is special (no `?`, no `[...]`).

Hard safety rule, whatever the filters say: if the filtered tree would hold a root-level `patches-bundle.json`,
import and sync stop with an error. At `packs/<name>/patches-bundle.json` that file would make Morphe Manager serve
the upstream author's own binary from our URL. Only our CI writes that file.

## Commands

All run from anywhere inside the repo. The tool uses git plumbing only: it never checks an upstream tree out into
a working directory of its own and never runs anything from it, and blobs stay byte-identical to upstream
whatever `core.autocrlf` says. Every commit it or you make for packs must be `chore(pack-<name>): ...`
(or `chore(packs): ...` for tooling): `fix:` and `feat:` would publish a new Lynx Patches release.

```bash
python tools/pack_sync.py status
```
One line per pack: upstream, ref, short commit, version, whether `packs/<name>/` exists at HEAD, and how many
files differ from the pristine upstream snapshot (or "not imported").

```bash
python tools/pack_sync.py import facebook --dry-run     # prints the tree, files kept and dropped, changes nothing
python tools/pack_sync.py import facebook
```
Needs an entry in packs.json (commit it first as `chore(pack-facebook): add pack entry`), a clean working tree and
no existing `packs/facebook/`. It fetches `ref` at depth 1, applies the filter, puts the files under
`packs/facebook/`, records the full sha in packs.json and commits `chore(pack-facebook): import ...`.
Then read the code (review gate below) and add `packs/facebook/LYNX.md`.

```bash
python tools/pack_sync.py sync facebook v0.24.0 --dry-run   # upstream change only, touches nothing
python tools/pack_sync.py sync facebook v0.24.0
```
A real 3-way merge: the upstream change from the recorded commit to the new ref is applied on top of our current
files, so our fixes stay. Nothing is committed. The changes are staged, packs.json already points at the new ref,
the pure upstream diff is written to `build/pack-sync/<name>-<old>..<new>.diff`, and the exact commit command is
printed. Exit code 0 = clean, 2 = conflicts (markers are left in the files, resolve and `git add` them),
1 = error.

```bash
python tools/pack_sync.py diff facebook --name-only     # or --stat, or no flag for the full patch
```
Shows OUR changes: the pristine upstream snapshot of the recorded commit against `HEAD:packs/facebook`. Our own
files (`LYNX.md`, `patches-bundle.json`) show up as additions. This is the list to publish as "what Lynx changed".

## Review gate

A sync is never committed before its upstream diff has been read. Open `build/pack-sync/<name>-<old>..<new>.diff`
(and do the same for the whole tree after a first import) and look for:

- anything that sends data off the phone: new URLs, HTTP or socket code, analytics, telemetry, crash reporters;
- anything that loads code: class loaders, `System.load`, downloading a file and running it, dex injection;
- new permissions or exported components added to the patched app;
- changes to the build: `*.gradle*`, `gradle/`, `gradlew`, wrapper jar or properties, `buildSrc/`, `.github/`. A
  build runs code on this PC and on CI with a token in the environment. The sync summary lists such files.

For an upgrade, the lead repeats this search over the added lines of the extension after the reviewers report (add
`| grep -v 'table\.put('` or similar when translation tables flood it):

```bash
git diff --cached -U0 -- packs/<name>/extensions | grep -E "^\+" | grep -v -E "^\+\+\+" \
  | grep -v -E "^\+\s*(//|\*|/\*)" \
  | grep -n -E "HttpURLConnection|HttpsURLConnection|URLConnection|openConnection|openStream|okhttp|OkHttp|Retrofit|Volley|HttpClient|\bSocket\(|ServerSocket|SocketFactory|DatagramSocket|InetAddress|InetSocketAddress|WebSocket|DownloadManager|Cronet|\.connect\(|https?://|wss?://|DexClassLoader|PathClassLoader|InMemoryDexClassLoader|defineClass|Runtime\.getRuntime|ProcessBuilder|System\.load|evaluateJavascript|addJavascriptInterface|loadUrl\(|WebView|setProperty\(|getCookie|CookieManager|sendBroadcast|ContentResolver|getExternalStorage|Base64|Cipher"
```

Never run an upstream build script (`gradlew`, a Gradle plugin, a task) that has not been reviewed. CI also
checks the Gradle wrapper jar against Gradle's official checksums before it builds.

Our own changes to a pack are separate commits `chore(pack-<name>): ...` and are listed in `packs/<name>/LYNX.md`.
Never edit upstream files in a commit that also changes something else.

## Build a pack locally

Use the JDK the entry asks for in `build.java`. From Git Bash:

```bash
export JAVA_HOME="C:/Users/thoma/.jdks/temurin-21.0.13" ANDROID_HOME="C:/Users/thoma/Android/Sdk"
export GITHUB_ACTOR=xhyxfxxc GITHUB_TOKEN="$(gh auth token)"
cd packs/<name>
./gradlew <build.tasks> -Pversion=<version> --no-daemon     # output: see build.output
```

## Release a pack

Raise `version` in packs.json (a release needs a version that has no tag yet), commit and push, then start the
workflow from `.github/workflows/pack.yml`:

```bash
gh workflow run "Pack" -R xhyxfxxc/lynx-patches -f pack=<name>
gh workflow run "Pack" -R xhyxfxxc/lynx-patches -f pack=<name> -f publish=false   # build and verify only
```

The workflow builds in `packs/<name>`, checks the bundle holds `classes.dex` and files under `extensions/`,
uploads it as a workflow artifact (also with `publish=false`, so you can try it on a phone first), and with
`publish=true` creates the release `pack-<name>-v<version>` (never marked latest), writes
`packs/<name>/patches-bundle.json`, pushes it to main and attests the build.

## Add a pack as a source in Morphe Manager

One tap: on the phone, tap the pack's "Add to Morphe" link in the table in [Packs](#packs). Morphe Manager opens
and asks to add the source. The link has this form:

```
https://morphe.software/add-source?github=xhyxfxxc/lynx-patches/tree/main/packs/<name>&name=<label>
```

By hand: on the phone, Sources, plus, paste

```
https://raw.githubusercontent.com/xhyxfxxc/lynx-patches/main/packs/<name>/patches-bundle.json
```

Use one patch source per app: remove or switch off the original author's source for that app in Manager before or
after adding the Lynx one. Two sources for the same app apply the same patches twice, and the result may not
install.

## License duties

- Every pack is GPL-3.0. Keep the pack's own `LICENSE` and `NOTICE` files and every copyright header untouched.
- Name the bundle "<Upstream name> (Lynx build)" (the `title` in packs.json, and the name inside the pack's own
  build) so nobody mistakes it for the original. Say who wrote the pack in `LYNX.md`.
- List every change we made in `packs/<name>/LYNX.md`.
- Share patches only, never patched APKs.

## Packs

| name | app (tested with) | upstream | upstream version | Lynx version | add |
| --- | --- | --- | --- | --- | --- |
| facebook | Facebook `com.facebook.katana`, 588.0.0.42.55 (build 707615136) | [SysAdminDoc/HushFacebook](https://github.com/SysAdminDoc/HushFacebook) | v0.24.0 | 0.24.0-lynx.1 | [Add to Morphe](https://morphe.software/add-source?github=xhyxfxxc/lynx-patches/tree/main/packs/facebook&name=Lynx%20Facebook) |
| instagram | Instagram `com.instagram.android`, 456.0.0.53.78 | [SysAdminDoc/HushGram](https://github.com/SysAdminDoc/HushGram) | v0.0.21 | 0.0.21-lynx.1 | [Add to Morphe](https://morphe.software/add-source?github=xhyxfxxc/lynx-patches/tree/main/packs/instagram&name=Lynx%20Instagram) |
| threads | Threads `com.instagram.barcelona`, 455.0.0.55.72 | [SysAdminDoc/HushThreads](https://github.com/SysAdminDoc/HushThreads) | v0.0.18 | 0.0.18-lynx.1 | [Add to Morphe](https://morphe.software/add-source?github=xhyxfxxc/lynx-patches/tree/main/packs/threads&name=Lynx%20Threads) |
| gboard | Gboard `com.google.android.inputmethod.latin`, 18.5.4.993608717 | [jasonwu1994/Gboard-patches](https://github.com/jasonwu1994/Gboard-patches) | v4.4.0 | 4.4.0-lynx.1 | [Add to Morphe](https://morphe.software/add-source?github=xhyxfxxc/lynx-patches/tree/main/packs/gboard&name=Lynx%20Gboard) |
| waze | Waze `com.waze`, 5.27.5.0 | [bearinmindcat/morphe-patches](https://github.com/bearinmindcat/morphe-patches) | v2.10.0 | 2.10.0-lynx.1 | [Add to Morphe](https://morphe.software/add-source?github=xhyxfxxc/lynx-patches/tree/main/packs/waze&name=Lynx%20Waze) |
| x | X `com.twitter.android`, 12.19.1-release.0 | [crimera/piko](https://github.com/crimera/piko) | v3.11.1 | 3.11.1-lynx.1 | [Add to Morphe](https://morphe.software/add-source?github=xhyxfxxc/lynx-patches/tree/main/packs/x&name=Lynx%20X) |

Each pack's own `LYNX.md` lists what Lynx changed in it. When a pack's `version` in `packs.json` changes, update
its row here in the same commit.
