#!/usr/bin/env python3
"""
Checks that hold for every app in the bundle, so a fix for one app is not silently missing from the others.

Run before any hand-over:

    python tools/audit.py

Exits non-zero when a check fails, and prints one line per finding with the file that holds it.
"""

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATCHES = os.path.join(ROOT, "patches", "src", "main", "kotlin")
EXTENSION = os.path.join(ROOT, "extensions", "extension", "src", "main", "java")
MENU_INFO = os.path.join(EXTENSION, "app", "loki", "extension", "MenuInfo.java")

failures = []


def fail(check, where, detail):
    failures.append((check, where, detail))


def read(path):
    with open(path, encoding="utf-8", errors="replace") as handle:
        return handle.read()


def walk(base, suffix):
    for folder, dirs, names in os.walk(base):
        if base == ROOT and folder == base:
            dirs[:] = [d for d in dirs if d != "packs"]   # imported third-party packs have their own build files
        for name in names:
            if name.endswith(suffix):
                yield os.path.join(folder, name)


def check_menu_flags():
    """Every patch that records itself in the menu must have a row on the Patch status page, and vice versa."""
    set_flags, read_flags, prefixes = {}, {}, set()
    for path in list(walk(PATCHES, ".kt")):
        text = read(path)
        # Written as a manifest meta-data, however it is spelled: addMenuFlag(...), metaData(...), setAttribute.
        # A patch may also build the name from a variable (e.g. `"app.loki.menu.x.$name"`): the part before
        # the variable is then a prefix that covers every key starting with it.
        for key in re.findall(r'"(app\.loki\.menu\.[a-z0-9._]+)"', text):
            set_flags.setdefault(key, []).append(os.path.relpath(path, ROOT))
        for prefix in re.findall(r'"(app\.loki\.menu\.[a-z0-9._]*\.)\$', text):
            prefixes.add(prefix)
    info = read(MENU_INFO)
    for key in re.findall(r'flag\(flags,\s*"([^"]+)"\)', info):
        read_flags.setdefault(key, 0)
        read_flags[key] += 1

    def recorded(key):
        return key in set_flags or any(key.startswith(prefix) for prefix in prefixes)

    for key, where in sorted(set_flags.items()):
        if key not in read_flags:
            fail("menu flag", where[0], f"{key} is recorded by a patch but no row on the Patch status page shows it")
    for key in sorted(read_flags):
        if not recorded(key):
            fail("menu flag", "MenuInfo.java", f"row for {key} can never read as applied - no patch records it")


def check_apps():
    """Each app in apps.json needs a docs page and an icon; the bundle description must name it."""
    manifest = read(os.path.join(ROOT, "apps.json"))
    data = json.loads(manifest)
    description = ""
    for path in walk(ROOT, ".kts"):
        text = read(path)
        if "bundles" in text or "description" in text:
            match = re.search(r'description\s*=\s*"([^"]+)"', text, re.S)
            if match:
                description = match.group(1)
    for package, app in data["apps"].items():
        slug = app.get("slug")
        page = os.path.join(ROOT, "docs", "apps", f"{slug}.md")
        if not os.path.exists(page):
            fail("apps.json", "apps.json", f"{package} has no docs/apps/{slug}.md")
        icons = os.path.join(ROOT, "assets", "icons")
        if not any(os.path.exists(os.path.join(icons, f"{package}{ext}")) for ext in (".svg", ".png")):
            fail("apps.json", "apps.json", f"{package} has no icon in assets/icons")
        # An app is named by its own name or, for a family of games with one set of patches, by the family's name.
        names = [n for n in (app["name"], app.get("family")) if n]
        if description and not any(n.lower() in description.lower() for n in names):
            fail("bundle description", "patches/build.gradle.kts",
                 f"the description Manager shows does not name {app['name']}" +
                 (f" or its family {app['family']}" if app.get("family") else ""))


def check_bundle_apps():
    """Every app the bundle patches must be described in apps.json: name, icon, docs page, README row."""
    manifest = os.path.join(ROOT, "apps.json")
    data = json.loads(read(manifest))
    known = set(data["apps"].keys())
    patched = set()
    for path in walk(PATCHES, ".kt"):
        text = read(path)
        for match in re.findall(r'packageName\s*=\s*"([a-zA-Z0-9_.]+)"', text):
            patched.add(match)
        # A Constants file keeps the package in one place that Compatibility then names.
        for const in re.findall(r'const val PACKAGE\s*=\s*"([a-zA-Z0-9_.]+)"', text):
            if "Compatibility" in text or "COMPATIBILITY" in text:
                patched.add(const)
    for package in sorted(patched - known):
        fail("apps.json", "apps.json", f"{package} is patched by the bundle but has no apps.json entry "
                                    "(no name, icon or docs page, and missing from the README index)")


def check_logs():
    """
    Success paths must not write to the diagnostic log: LokiLog.note() is unconditional and MX alone filled the
    report with one line per page open. Failures keep note(); routine events use debug(context, ...).
    """
    failure_words = ("fail", "error", "could not", "cannot", "can't", "not found", "missing", "refus", "denied",
                     "unsupported", "exception", "no ", "changed", "gave up", "empty")
    routine = ("added", "wired", "tapped", "active", "hook", "ok", "applied", "opened", "started")
    for path in walk(EXTENSION, ".java"):
        text = read(path)
        for match in re.finditer(r'LokiLog\.note\(((?:[^;]|\n)*?)\);', text):
            body = match.group(1)
            literal = " ".join(re.findall(r'"([^"]*)"', body)).lower()
            line = text[:match.start()].count("\n") + 1
            before = text[:match.start()].rsplit("\n", 1)[-1] + body
            if "audit: keep" in before:
                continue
            if any(word in literal for word in failure_words):
                continue
            if any(word in literal for word in routine):
                fail("log noise", os.path.relpath(path, ROOT) + f":{line}",
                     f'note("{literal[:48]}") runs on a success path - use LokiLog.debug(context, ...)')


def check_row_builders():
    """A host row built beside the app's own rows must copy its neighbour's layout params, margins included."""
    for path in walk(EXTENSION, ".java"):
        text = read(path)
        if "Lynx Patches" not in text or "setOnClickListener" not in text:
            continue
        # Only a row placed among the host's own rows: a floating button sets its own FrameLayout params.
        # A row placed among the host's rows is inserted at an index; an overlay is simply added on top.
        if "indexOfChild" not in text and "addView(" not in text.replace("setOnClickListener", ""):
            continue
        if "indexOfChild" not in text:
            continue
        if "LayoutParams" not in text:
            continue
        if "setMargins" not in text and "paramsOf" not in text and "MarginLayoutParams" not in text:
            fail("row layout", os.path.relpath(path, ROOT),
                 "builds a row without copying the neighbour's margins - the row will not line up")


def check_featured():
    """The Featured row is capped, and every app in it can actually render."""
    data = json.loads(read(os.path.join(ROOT, "apps.json")))
    featured = [(a.get("featured"), name, a) for name, a in data["apps"].items() if a.get("featured")]
    if len(featured) > 5:
        fail("featured", "apps.json",
             f"{len(featured)} apps are featured; the row holds 5 - extra apps still appear in the index")
    seen = set()
    for position, package, app in featured:
        if position in seen:
            fail("featured", "apps.json", f"{app['name']} reuses featured position {position}")
        seen.add(position)
        if not app.get("blurb"):
            fail("featured", "apps.json", f"{app['name']} is featured with no blurb")
        if not os.path.exists(os.path.join(ROOT, "docs", "apps", app["slug"] + ".md")):
            fail("featured", "apps.json", f"{app['name']} is featured but has no docs page")


def check_versions():
    """Every app carries the newest two releases, never more: an old entry is carried work nobody tests."""
    for constants in walk(PATCHES, "Constants.kt"):
        text = read(constants)
        target = r'AppTarget\(version = "([^"]+)"\)'
        # A file with several Compatibility blocks holds several apps (the Pinkfong games): the rule applies to each.
        blocks = text.split("Compatibility(")[1:]
        lists = [re.findall(target, block) for block in blocks] if len(blocks) > 1 else [re.findall(target, text)]
        # LinkedIn keeps its versions in a list that is mapped to targets.
        for listed in re.findall(r'_BUILDS\s*=\s*listOf\(([^)]*)\)', text):
            lists[0] += re.findall(r'"([^"]+)"', listed)
        for versions in lists:
            if len(versions) > 2:
                fail("versions", os.path.relpath(constants, ROOT),
                     f"{len(versions)} versions listed ({', '.join(versions)}); the rule is the newest two")


def check_docs_claims():
    """A patch count in prose must match the patches that exist."""
    count = len(set(re.findall(r'name\s*=\s*"([^"]+)"', "".join(read(p) for p in walk(PATCHES, ".kt")))))
    for path in walk(os.path.join(ROOT, "docs"), ".md"):
        text = read(path)
        for number in re.findall(r'(\d+)\s+patches', text):
            if int(number) > count + 12:
                fail("docs claim", os.path.relpath(path, ROOT),
                     f"claims {number} patches; the tree defines {count} names")


def check_brand():
    """The logo Manager shows for this source and the logo the app draws are one file: the Lynx logo."""
    bundle = os.path.join(ROOT, "patches-bundle.png")
    logo = os.path.join(ROOT, "patches", "src", "main", "resources", "loki", "loki_logo.png")
    for path in (bundle, logo):
        if not os.path.exists(path):
            fail("brand", os.path.relpath(path, ROOT), "missing - the Lynx logo must ship")
            return
    with open(bundle, "rb") as a, open(logo, "rb") as b:
        if a.read() != b.read():
            fail("brand", "patches-bundle.png", "differs from loki/loki_logo.png - Manager and the app would show two logos")
    icon = os.path.join(PATCHES, "app", "loki", "patches", "universal", "LokiIcon.kt")
    if 'LOKI_ICON = "@drawable/loki_logo"' not in read(icon):
        fail("brand", os.path.relpath(icon, ROOT), "LOKI_ICON no longer points at the Lynx logo (@drawable/loki_logo)")


def check_private():
    """
    Private apps live on this PC only: nothing in the public tree may name them. The names come from
    tools/private-names.txt (one per line), which is git-ignored, so the check itself names nothing.
    """
    names_file = os.path.join(ROOT, "tools", "private-names.txt")
    if not os.path.exists(names_file):
        return
    names = [line.strip() for line in read(names_file).splitlines() if line.strip()]
    if not names:
        return
    words = re.compile("|".join(re.escape(n) for n in names), re.IGNORECASE)
    bases = [os.path.join(ROOT, d) for d in ("patches", "extensions", "docs", "tools", ".github")]
    files = [os.path.join(ROOT, "README.md"), os.path.join(ROOT, "apps.json")]
    for base in bases:
        for folder, dirs, names in os.walk(base):
            dirs[:] = [d for d in dirs if d not in ("build", ".gradle")]
            files += [os.path.join(folder, n) for n in names if not n.endswith((".png", ".jpg", ".so", ".jar"))]
    for path in files:
        if os.path.abspath(path) in (os.path.abspath(__file__), os.path.abspath(names_file)) or not os.path.exists(path):
            continue
        if words.search(read(path)):
            fail("private", os.path.relpath(path, ROOT), "names a private app")


JAVA_ESCAPES = {'"': '"', "\\": "\\", "'": "'", "n": "\n", "t": "\t", "r": "\r"}
JAVA_STRING = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
JAVA_ESCAPE = re.compile(r"\\(u[0-9a-fA-F]{4}|.)", re.S)


def java_unescape(body):
    """Decode the Java escapes for quotes, backslash, n, t, r and uXXXX. Any other backslash stays for a later check."""
    def one(match):
        code = match.group(1)
        return chr(int(code[1:], 16)) if len(code) == 5 else JAVA_ESCAPES.get(code, match.group(0))
    return JAVA_ESCAPE.sub(one, body)


def java_string_array(text, name):
    """
    The string literals of the Java array initializer `name = { ... };`, or None when it cannot be found or holds
    anything but string literals. // and /* */ comments outside string literals are skipped.
    """
    start = re.search(r"\b" + re.escape(name) + r"\s*(?:\[\s*\]\s*)?=\s*\{", text)
    if not start:
        return None
    items, i = [], start.end()
    while i < len(text):
        if text[i] in " \t\r\n,":
            i += 1
        elif text.startswith("//", i):
            end = text.find("\n", i)
            i = len(text) if end < 0 else end + 1
        elif text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = len(text) if end < 0 else end + 2
        elif text[i] == "}":
            return items
        else:
            literal = JAVA_STRING.match(text, i)
            if not literal:
                return None
            items.append(java_unescape(literal.group(1)))
            i = literal.end()
    return None


RULE_MAX_LENGTH = 300
RULE_FORBIDDEN_CHARS = "{};@\\<`"
RULE_FORBIDDEN_TEXT = ("/*", "*/", "url(", "expression(", "javascript:")
RULE_PAIRS = {"[": "]", "(": ")"}


def shown(text, limit):
    """Text for a message: cut, and nothing but printable ASCII."""
    return re.sub(r"[^\x20-\x7e]", "?", text[:limit])


def pairs_up(selector):
    """Quotes close (a quote opens a string that ends at the next same quote) and every [ and ( closes in order."""
    stack, quote = [], None
    for ch in selector:
        if quote:
            if ch == quote:
                quote = None
        elif ch in "\"'":
            quote = ch
        elif ch in RULE_PAIRS:
            stack.append(RULE_PAIRS[ch])
        elif ch in "])" and (not stack or stack.pop() != ch):
            return False
    return quote is None and not stack


def last_compounds(selector):
    """The last compound selector of every part of a selector list, split at top-level commas and combinators."""
    parts, compound, depth, quote = [], "", 0, None
    for ch in selector:
        if quote:
            quote = None if ch == quote else quote
        elif ch in "\"'":
            quote = ch
        elif ch in RULE_PAIRS or ch in "])":
            depth += 1 if ch in RULE_PAIRS else -1
        elif depth == 0 and ch == ",":
            parts.append(compound)
            compound = ""
            continue
        elif depth == 0 and (ch.isspace() or ch in ">+~"):
            compound = ""
            continue
        compound += ch
    return parts + [compound]


def compound_problems(compound):
    if not any(ch in compound for ch in ".#[:"):
        return [f"part '{shown(compound, 40)}' would hide a whole element type"]
    tag = re.match(r"[A-Za-z][A-Za-z0-9-]*", compound)
    if (tag and tag.group(0).lower() in ("html", "body")) or re.match(r":root(?![\w-])", compound, re.I):
        return [f"part '{shown(compound, 40)}' would hide the whole page"]
    return []


def rule_problems(selector):
    """
    Short problem texts for one ad-hiding selector; an empty list means it is acceptable. At run time the
    selector becomes `<selector>{display:none!important}` in a style sheet put into Amazon's pages, so one bad
    entry could blank a page, break the sheet, or open a new rule.
    """
    if not selector:
        return ["is empty"]
    problems = []
    if selector != selector.strip():
        problems.append("has leading or trailing white space")
    if len(selector) > RULE_MAX_LENGTH:
        problems.append(f"is {len(selector)} characters long (limit {RULE_MAX_LENGTH})")
    if any(not " " <= ch <= "~" for ch in selector):
        problems.append("has a character that is not printable ASCII")
    problems += [f"contains {ch!r}" for ch in RULE_FORBIDDEN_CHARS if ch in selector]
    problems += [f"contains '{text}'" for text in RULE_FORBIDDEN_TEXT if text in selector.lower()]
    if not pairs_up(selector):
        problems.append("has quotes or brackets that do not pair up")
    else:
        for compound in last_compounds(selector):
            problems += compound_problems(compound)
    return problems


AMAZON_HELPER = os.path.join(EXTENSION, "app", "amazon", "extension", "AmazonHelper.java")


def check_amazon_rules():
    """Every entry of AD_SELECTORS in AmazonHelper.java passes rule_problems, and none is listed twice."""
    where = os.path.relpath(AMAZON_HELPER, ROOT)
    selectors = java_string_array(read(AMAZON_HELPER), "AD_SELECTORS") if os.path.exists(AMAZON_HELPER) else None
    if not selectors:
        fail("amazon rules", where, "AD_SELECTORS is missing, empty, or not a plain list of string literals")
        return
    for selector in selectors:
        problems = rule_problems(selector)
        if problems:
            fail("amazon rules", where, f"{shown(selector, 80)} -> {'; '.join(problems)}")
    for selector in sorted({s for s in selectors if selectors.count(s) > 1}):
        fail("amazon rules", where, f"{shown(selector, 80)} -> listed more than once")


def main():
    check_menu_flags()
    check_apps()
    check_bundle_apps()
    check_logs()
    check_row_builders()
    check_featured()
    check_versions()
    check_docs_claims()
    check_brand()
    check_private()
    check_amazon_rules()
    if not failures:
        print("audit: clean")
        return 0
    for check, where, detail in failures:
        print(f"FAIL [{check}] {where}: {detail}")
    print(f"\n{len(failures)} finding(s)")
    return 1


if __name__ == "__main__":
    sys.exit(main())
