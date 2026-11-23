# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Tests for tools/bundle_hosts.py. Run from the repo root, no network needed:

    python -m unittest tools/test_bundle_hosts.py -v

Every test builds small synthetic zips (fake classes.dex, nested extensions/*.mpe) in a temp dir.
"""

import io
import os
import subprocess
import sys
import tempfile
import unittest
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True      # keep tools/ free of __pycache__ files
import bundle_hosts  # noqa: E402

SCRIPT = os.path.join(HERE, "bundle_hosts.py")


def dex(*strings):
    """A fake dex: a header, then each string as a length byte, its bytes and a NUL, like a string table."""
    body = b"".join(bytes([len(s) & 0x7F]) + s + b"\x00" for s in strings)
    return b"dex\n035\x00" + body


def zip_bytes(entries):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, data in entries.items():
            archive.writestr(name, data)
    return buffer.getvalue()


class HostTestCase(unittest.TestCase):
    def setUp(self):
        kw = {"ignore_cleanup_errors": True} if sys.version_info >= (3, 10) else {}
        self._tmp = tempfile.TemporaryDirectory(prefix="bundle-hosts-test-", **kw)
        self.addCleanup(self._tmp.cleanup)
        self.base = self._tmp.name

    def bundle(self, name, entries):
        path = os.path.join(self.base, name)
        with open(path, "wb") as handle:
            handle.write(zip_bytes(entries))
        return path

    def found(self, entries):
        """{host: (class, count, first path)} for a synthetic bundle."""
        found = bundle_hosts.scan_bundle(self.bundle("scan.mpp", entries))
        return {host: ("url" if item["url"] else "bare", item["count"], item["first"])
                for host, item in found.hosts.items()}

    def run_tool(self, *paths):
        proc = subprocess.run([sys.executable, "-I", SCRIPT, *paths], capture_output=True)
        text = [data.decode("utf-8", "replace").replace("\r\n", "\n") for data in (proc.stdout, proc.stderr)]
        return proc.returncode, text[0], text[1]


class ScanTests(HostTestCase):
    def test_url_hosts_in_a_fake_dex(self):
        found = self.found({"classes.dex": dex(
            b"https://graph.example.com/v1/me?x=1", b"http://plain.example.io", b"wss://push.example.org/socket",
            b"ftp://files.example.net/a.zip", b"https://graph.example.com/again")})
        self.assertEqual(found, {
            "graph.example.com": ("url", 2, "classes.dex"),
            "plain.example.io": ("url", 1, "classes.dex"),
            "push.example.org": ("url", 1, "classes.dex"),
            "files.example.net": ("url", 1, "classes.dex"),
        })

    def test_a_nested_zip_under_extensions_is_opened(self):
        inner = zip_bytes({"classes.dex": dex(b"https://nested.example.com/x"), "readme.txt": b"nothing here"})
        found = self.found({"classes.dex": dex(b"https://top.example.com/"), "extensions/ext.mpe": inner})
        self.assertEqual(found["top.example.com"], ("url", 1, "classes.dex"))
        self.assertEqual(found["nested.example.com"], ("url", 1, "extensions/ext.mpe!/classes.dex"))

    def test_nested_zips_are_opened_three_levels_deep_and_no_further(self):
        deepest = zip_bytes({"x.dex": dex(b"https://level4.example.com/")})
        level3 = zip_bytes({"x.dex": dex(b"https://level3.example.com/"), "next.zip": deepest})
        level2 = zip_bytes({"c.zip": level3})
        level1 = zip_bytes({"b.zip": level2})
        path = self.bundle("deep.mpp", {"extensions/a.mpe": level1})
        found = bundle_hosts.scan_bundle(path)
        self.assertIn("level3.example.com", found.hosts)
        self.assertEqual(found.hosts["level3.example.com"]["first"], "extensions/a.mpe!/b.zip!/c.zip!/x.dex")
        self.assertNotIn("level4.example.com", found.hosts)
        self.assertTrue(any("deeper than 3" in note for note in found.notes), found.notes)

    def test_a_fake_zip_is_scanned_as_plain_bytes(self):
        found = self.found({"odd.bin": b"PK\x03\x04 not really a zip https://fake.example.com/ end"})
        self.assertEqual(found, {"fake.example.com": ("url", 1, "odd.bin")})

    def test_port_user_info_and_upper_case(self):
        found = self.found({"a.class": dex(
            b"HTTPS://API.Example.COM:8443/x", b"http://user:secret@Login.Example.com/",
            b"see http://trail.example.com.", b"http://localhost:3000/", b"http://10.0.0.5:8080/x", b"http://[::1]:80/",
            b"http://nodot/", b"http://%s/%s", b"http://", b"https://.", b"http://a..b.example.com/")})
        self.assertEqual(sorted(found), ["10.0.0.5", "[::1]", "api.example.com", "localhost", "login.example.com",
                                         "trail.example.com"])
        self.assertTrue(all(kind == "url" for kind, _, _ in found.values()))

    def test_bare_hosts(self):
        found = self.found({"a.dex": dex(
            b"cdn.example.net", b"Contact support.example.org now", b"CDN.Example.NET", b"shop.example.store",
            b"m.example.com.br")})
        self.assertEqual(found, {
            "cdn.example.net": ("bare", 2, "a.dex"),
            "support.example.org": ("bare", 1, "a.dex"),
            "shop.example.store": ("bare", 1, "a.dex"),
            "m.example.com.br": ("bare", 1, "a.dex"),
        })

    def test_identifiers_and_paths_are_not_hosts(self):
        found = self.found({"a.dex": dex(
            b"com.instagram.android", b"Lcom/foo/Bar;", b"androidx.core.app.Foo", b"com.google.android.gms",
            b"x.example.com/path", b"/data/example.com", b"my_example.com", b"-example.com", b"example.comx",
            b"example.com.evil", b"example.com.", b"com", b"Lorg/apache/http/Foo;", b"java.lang.String")})
        self.assertEqual(found, {})

    def test_a_host_seen_as_url_is_a_url_whatever_else_was_seen(self):
        found = self.found({"a.dex": dex(b"both.example.com", b"https://both.example.com/x")})
        self.assertEqual(found, {"both.example.com": ("url", 2, "a.dex")})

    def test_the_first_path_follows_the_zip_order(self):
        found = self.found({"z.dex": dex(b"https://once.example.com/"), "a.dex": dex(b"https://once.example.com/")})
        self.assertEqual(found["once.example.com"], ("url", 2, "z.dex"))


class CommandTests(HostTestCase):
    def test_single_bundle_table(self):
        path = self.bundle("one.mpp", {
            "classes.dex": dex(b"https://b.example.com/", b"https://a.example.com/", b"https://b.example.com/y",
                               b"zeta.example.net"),
            "extensions/e.mpe": zip_bytes({"x.dex": dex(b"alpha.example.org")})})
        code, out, err = self.run_tool(path)
        self.assertEqual((code, err), (0, ""))
        row = "{:<5}  {:<17}  {:>5}  {}".format           # class, host (widest is 17), count, first entry path
        self.assertEqual(out.splitlines(), [
            row("class", "host", "count", "first entry path"),
            row("url", "a.example.com", "1", "classes.dex"),
            row("url", "b.example.com", "2", "classes.dex"),
            row("bare", "alpha.example.org", "1", "extensions/e.mpe!/x.dex"),
            row("bare", "zeta.example.net", "1", "classes.dex"),
            "hosts: 2 url, 2 bare",
        ])

    def test_two_bundles_show_added_and_removed_hosts(self):
        old = self.bundle("old.mpp", {"classes.dex": dex(
            b"https://same.example.com/", b"https://gone.example.com/", b"gonebare.example.net", b"moved.example.com")})
        new = self.bundle("new.mpp", {
            "classes.dex": dex(b"https://same.example.com/", b"newbare.example.org", b"https://moved.example.com/"),
            "extensions/ext.mpe": zip_bytes({"classes.dex": dex(b"https://new.example.com/y")})})
        code, out, err = self.run_tool(old, new)
        self.assertEqual((code, err), (0, ""))
        self.assertEqual(out.splitlines(), [
            "+ url new.example.com  extensions/ext.mpe!/classes.dex",
            "+ bare newbare.example.org  classes.dex",
            "- url gone.example.com",
            "- bare gonebare.example.net",
            "added: 2, removed: 2",
        ])

    def test_identical_bundles_have_no_difference(self):
        entries = {"classes.dex": dex(b"https://same.example.com/")}
        code, out, err = self.run_tool(self.bundle("a.mpp", entries), self.bundle("b.mpp", entries))
        self.assertEqual((code, out, err), (0, "added: 0, removed: 0\n", ""))

    def test_a_file_that_is_not_a_zip_is_one_error_line(self):
        text = os.path.join(self.base, "plain.txt")
        with open(text, "w") as handle:
            handle.write("hello\n")
        good = self.bundle("good.mpp", {"classes.dex": dex(b"https://a.example.com/")})
        for args in ([text], [good, text], [text, good]):
            with self.subTest(args=[os.path.basename(a) for a in args]):
                code, out, err = self.run_tool(*args)
                self.assertEqual(code, 1)
                self.assertEqual(out, "")
                self.assertEqual(err.strip(), f"error: {text} is not a zip file")

    def test_a_missing_file_is_one_error_line(self):
        missing = os.path.join(self.base, "nope.mpp")
        code, out, err = self.run_tool(missing)
        self.assertEqual(code, 1)
        self.assertEqual(out, "")
        self.assertEqual(len(err.strip().splitlines()), 1, err)
        self.assertTrue(err.startswith("error: cannot read"), err)

    def test_entry_names_cannot_inject_terminal_codes(self):
        path = self.bundle("evil.mpp", {"a\x1b[31m\nred.dex": dex(b"https://evil.example.com/")})
        code, out, err = self.run_tool(path)
        self.assertEqual(code, 0, err)
        self.assertNotIn("\x1b", out)
        self.assertEqual(len(out.splitlines()), 3, out)          # header, one host, totals

    def test_wrong_usage_exits_with_two(self):
        code, _, err = self.run_tool()
        self.assertEqual(code, 2)
        self.assertIn("usage", err)


if __name__ == "__main__":
    unittest.main()
