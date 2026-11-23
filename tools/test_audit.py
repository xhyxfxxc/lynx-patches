# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Tests for the Amazon ad-rule check in tools/audit.py. Run from the repo root:

    python -m unittest tools/test_audit.py -v
"""

import contextlib
import io
import os
import sys
import tempfile
import unittest
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True      # keep tools/ free of __pycache__ files
import audit  # noqa: E402


class AuditCase(unittest.TestCase):
    def setUp(self):
        audit.failures.clear()
        self.addCleanup(audit.failures.clear)


class JavaStringArrayTests(AuditCase):
    def test_plain_list_with_a_trailing_comma(self):
        text = 'class A { private static final String[] NAMES = { "a", "b",\n  "c",\n }; }'
        self.assertEqual(audit.java_string_array(text, "NAMES"), ["a", "b", "c"])
        self.assertEqual(audit.java_string_array("String[] NAMES = {};", "NAMES"), [])
        self.assertEqual(audit.java_string_array("String NAMES[] = { \"a\" };", "NAMES"), ["a"])

    def test_escapes_are_decoded(self):
        text = r'String[] X = { "a\"b", "c\\d", "e\'f", "g\nh\ti\rj", "k\u0041l\u00e9", };'
        self.assertEqual(audit.java_string_array(text, "X"), ['a"b', "c\\d", "e'f", "g\nh\ti\rj", "kAl\u00e9"])

    def test_any_other_backslash_stays_so_a_later_check_sees_it(self):
        self.assertEqual(audit.java_string_array(r'String[] X = { "a\bc", "d\u12" };', "X"), ["a\\bc", "d\\u12"])

    def test_comments_outside_strings_are_skipped_and_literals_in_them_are_not_returned(self):
        text = ('String[] X = {\n'
                '  // "line comment", "second"\n'
                '  "a", /* "block", "comment" */ "b",\n'
                '  /* multi\n line "x" */\n'
                '  "http://not-a-comment.example", "/* not a comment */", "c" // "tail"\n'
                '};')
        self.assertEqual(audit.java_string_array(text, "X"),
                         ["a", "b", "http://not-a-comment.example", "/* not a comment */", "c"])

    def test_missing_array_gives_none(self):
        self.assertIsNone(audit.java_string_array("class A {}", "X"))
        self.assertIsNone(audit.java_string_array('String[] MY_X = { "a" };', "X"))       # another name
        self.assertIsNone(audit.java_string_array('String[] OTHER = { "a" };', "X"))
        self.assertIsNone(audit.java_string_array('String[] X = new String[0];', "X"))

    def test_anything_but_plain_string_literals_gives_none(self):
        for text in ('String[] X = { "a" + "b" };', 'String[] X = { "a", OTHER };', 'String[] X = { "a", "b" ',
                     'String[] X = { "a", "unterminated };', 'String[] X = { "a", /* open };'):
            with self.subTest(text):
                self.assertIsNone(audit.java_string_array(text, "X"))


class RuleProblemsTests(AuditCase):
    ACCEPTED = [
        '.a-cardui[class*="_new-detail-faceout-box_"]:has(.dynamicSponsoredLabelClass)',
        '.a-carousel-card:has(.p13n-sc-sponsored-label)',
        '.s-result-item:has(div[cel_widget_id^="MAIN-FEATURED_ASINS_LIST-"])',
        '.s-result-list > .a-section:has(.sbv-ad-content-container)',
        '#faceoutContainer:has(> [class$="sponsored-label"])',
        "div[data-a-carousel-options*='\"isSponsoredProduct\":\"true\"']",
        'div.s-inner-result-item > div.sg-col-inner:has(a.puis-sponsored-label-text)',
        'li.gwm-window-tile:has(div[data-csa-c-painter="single-creative-card"] [data-ad-feedback-label-id])',
        'div[id^="sims-simsContainer_feature_div_"]:has(.sp_info_link)',
        'span[cel_widget_id^="MAIN-loom-desktop-top-slot_hsa-id-CARDS-"]',
        "#sims-fbt", ".ape-wrapper", "[data-ad-id]", '[id^="ape_"]',
        ".a, .b", "*.x", "div > .x", ".a + .b ~ .c", ".a:has(body)", ".a:nth-child(2n+1)",
    ]

    def test_selectors_like_the_ones_in_the_list_are_accepted(self):
        for selector in self.ACCEPTED:
            with self.subTest(selector):
                self.assertEqual(audit.rule_problems(selector), [])

    def test_a_selector_of_300_characters_is_accepted_and_301_is_not(self):
        self.assertEqual(audit.rule_problems("." + "a" * 299), [])
        self.assertIn("301 characters long", " ".join(audit.rule_problems("." + "a" * 300)))

    def test_refused_selectors_say_why(self):
        cases = [
            ("", "is empty"),
            (" .a", "leading or trailing white space"),
            (".a ", "leading or trailing white space"),
            (".a\t.b", "not printable ASCII"),
            (".a\u00e9", "not printable ASCII"),
            (".a/*b", "contains '/*'"),
            (".a*/b", "contains '*/'"),
            (".a[x=url(y)]", "contains 'url('"),
            (".a[x=URL(y)]", "contains 'url('"),
            (".a:has(Expression(1))", "contains 'expression('"),
            ('.a[href^="JavaScript:x"]', "contains 'javascript:'"),
            (".a[x", "do not pair up"),
            ('.a[x="y]', "do not pair up"),
            (".a[x='y]", "do not pair up"),
            (".a)", "do not pair up"),
            (".a(", "do not pair up"),
            (".a[(])", "do not pair up"),
            (".a(x]", "do not pair up"),
            ("div", "whole element type"),
            ("*", "whole element type"),
            ("body", "whole element type"),
            ("html", "whole element type"),
            (".a > div", "whole element type"),
            (".ok, span", "whole element type"),
            (".a,", "whole element type"),
            (", .a", "whole element type"),
            (".a >", "whole element type"),
            ("body.foo", "whole page"),
            ("BODY.foo", "whole page"),
            ("html[lang]", "whole page"),
            ("html#x", "whole page"),
            (":root", "whole page"),
            (":ROOT", "whole page"),
            (":root.foo", "whole page"),
            (".a > body.b", "whole page"),
            (".x{}body", "contains '{'"),
        ]
        cases += [(f".a{ch}b", f"contains {ch!r}") for ch in "{};@\\<`"]
        for selector, expected in cases:
            with self.subTest(selector):
                problems = audit.rule_problems(selector)
                self.assertTrue(problems, "the selector was accepted")
                self.assertIn(expected, " ".join(problems))

    def test_problems_are_printable_ascii(self):
        for selector in (".a\u00e9, \u00e9", ".a\x01"):
            self.assertTrue(all(ch.isascii() and ch.isprintable() for ch in " ".join(audit.rule_problems(selector))))


class AmazonRulesTests(AuditCase):
    GOOD = '".a", "div[x=\\"y\\"]"'

    def run_check(self, java_text):
        """check_amazon_rules against a temp AmazonHelper.java; the failures it reports."""
        with tempfile.TemporaryDirectory(prefix="audit-test-") as folder:
            path = os.path.join(folder, "AmazonHelper.java")
            if java_text is not None:
                with open(path, "w", encoding="utf-8") as handle:
                    handle.write(java_text)
            with mock.patch.object(audit, "AMAZON_HELPER", path), mock.patch.object(audit, "ROOT", folder):
                audit.check_amazon_rules()
        return list(audit.failures)

    def test_the_real_list_passes(self):
        audit.check_amazon_rules()
        self.assertEqual(audit.failures, [])

    def test_the_real_list_is_read_whole(self):
        selectors = audit.java_string_array(audit.read(audit.AMAZON_HELPER), "AD_SELECTORS")
        self.assertGreater(len(selectors), 50)
        self.assertEqual(len(selectors), len(set(selectors)))

    def test_a_good_copy_passes(self):
        self.assertEqual(self.run_check("class A { String[] AD_SELECTORS = { " + self.GOOD + " }; }"), [])

    def test_one_failure_per_bad_entry_and_per_duplicate(self):
        failures = self.run_check('String[] AD_SELECTORS = { ".a", "body", ".b{x}", ".c", ".a", ".c", };')
        self.assertEqual([f[0] for f in failures], ["amazon rules"] * 4)
        self.assertEqual({f[1] for f in failures}, {"AmazonHelper.java"})
        details = [f[2] for f in failures]
        self.assertEqual(details[0], "body -> part 'body' would hide a whole element type")
        self.assertTrue(details[1].startswith(".b{x} -> contains '{'"), details[1])
        self.assertEqual(details[2:], [".a -> listed more than once", ".c -> listed more than once"])

    def test_the_selector_in_a_detail_is_cut_to_80_characters(self):
        long_entry = "." + "a" * 320
        details = [f[2] for f in self.run_check(f'String[] AD_SELECTORS = {{ "{long_entry}" }};')]
        self.assertEqual(len(details), 1)
        self.assertTrue(details[0].startswith(long_entry[:80] + " -> "), details[0])
        self.assertNotIn(long_entry[:81], details[0])

    def test_a_missing_empty_or_odd_array_and_a_missing_file_fail(self):
        for label, text in (("missing", "class A {}"), ("empty", "String[] AD_SELECTORS = { };"),
                            ("comments only", 'String[] AD_SELECTORS = { // ".a"\n };'),
                            ("not literals", 'String[] AD_SELECTORS = { ".a" + ".b" };'), ("no file", None)):
            with self.subTest(label):
                audit.failures.clear()
                failures = self.run_check(text)
                self.assertEqual(len(failures), 1)
                self.assertEqual(failures[0][0], "amazon rules")
                self.assertIn("AD_SELECTORS", failures[0][2])

    def test_main_runs_the_check(self):
        names = [name for name in dir(audit) if name.startswith("check_")]
        with contextlib.ExitStack() as stack:
            mocks = {name: stack.enter_context(mock.patch.object(audit, name)) for name in names}
            stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
            self.assertEqual(audit.main(), 0)
        mocks["check_amazon_rules"].assert_called_once_with()


if __name__ == "__main__":
    unittest.main()
