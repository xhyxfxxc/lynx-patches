# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Tests for tools/emu_smoke.py. Run from the repo root; no emulator and no adb are needed:

    python -m unittest tools/test_emu_smoke.py -v

Everything is checked on small text samples or with a stand-in for subprocess.run.
"""

import ast
import os
import subprocess
import sys
import tempfile
import unittest
import zipfile
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.dont_write_bytecode = True      # keep tools/ free of __pycache__ files
import emu_smoke as es  # noqa: E402

PKG = "com.example.app"


def L(pid, tag, msg, level="I"):
    return "11-23 03:00:01.123  %5d  %5d %s %-8s: %s" % (pid, pid, level, tag, msg)


def crash(pid, process, tag="AndroidRuntime"):
    return "\n".join([
        L(pid, tag, "FATAL EXCEPTION: main", "E"),
        L(pid, tag, "Process: %s, PID: %d" % (process, pid), "E"),
        L(pid, tag, "java.lang.RuntimeException: boom", "E"),
    ])


def stage(**kw):
    st = {"name": "x", "installed": True, "install_kind": "ok", "install_detail": "", "running": True,
          "top": PKG + "/.Main", "dialog": "", "own_top": True, "fatal": 0, "anr": 0, "class_errors": 0, "tag_lines": 5, "seconds": 1,
          "evidence": {}}
    st.update(kw)
    return st


class ParsingTests(unittest.TestCase):
    def test_top_activity_prefers_top_resumed_and_expands_relative_names(self):
        dump = ("  topResumedActivity=ActivityRecord{eac8b0e u0 %s/.activity.MainTabActivity t9}\n"
                "  ResumedActivity: ActivityRecord{1111111 u0 other.pkg/other.Act t3}\n" % PKG)
        self.assertEqual(es.parse_top_activity(dump, PKG), (PKG + "/" + PKG + ".activity.MainTabActivity", ""))

    def test_top_activity_falls_back_to_resumed_activity_and_keeps_full_names(self):
        dump = "  ResumedActivity: ActivityRecord{abc u10 %s/com.example.ui.Home$Inner t12}\n" % PKG
        self.assertEqual(es.parse_top_activity(dump, PKG), (PKG + "/com.example.ui.Home$Inner", ""))
        self.assertEqual(es.parse_top_activity("nothing here", PKG), ("", ""))

    def test_another_apps_screen_on_top_stays_the_top(self):
        dump = "  topResumedActivity=ActivityRecord{abc u0 com.android.launcher/.Home t1}\n"
        self.assertEqual(es.parse_top_activity(dump, PKG), ("com.android.launcher/com.android.launcher.Home", ""))

    def test_permission_dialog_over_the_apps_own_screen_gives_the_own_screen(self):
        dialog = "com.google.android.permissioncontroller/com.android.permissioncontroller.permission.ui.GrantPermissionsActivity"
        dump = ("  topResumedActivity=ActivityRecord{aaa u0 %s t55}\n"
                "    * Hist  #1: ActivityRecord{aaa u0 %s t55}\n"
                "    * Hist  #0: ActivityRecord{bbb u0 %s/.LoginActivity t55}\n" % (dialog, dialog, PKG))
        self.assertEqual(es.parse_top_activity(dump, PKG), (PKG + "/" + PKG + ".LoginActivity", dialog))

    def test_permission_dialog_without_an_own_screen_in_its_task_is_not_ours(self):
        dialog = "com.google.android.permissioncontroller/com.android.permissioncontroller.permission.ui.GrantPermissionsActivity"
        dump = ("  topResumedActivity=ActivityRecord{aaa u0 %s t55}\n"
                "    * Hist  #0: ActivityRecord{bbb u0 %s/.LoginActivity t77}\n" % (dialog, PKG))
        self.assertEqual(es.parse_top_activity(dump, PKG), (dialog, ""))

    def test_launcher_is_the_last_component_line(self):
        out = "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\n%s/.Main\n" % PKG
        self.assertEqual(es.parse_launcher(out), PKG + "/.Main")
        self.assertEqual(es.parse_launcher("No activity found"), "")

    def test_is_emulator(self):
        self.assertTrue(es.is_emulator({"ro.boot.qemu": "1"}))
        self.assertTrue(es.is_emulator({"ro.kernel.qemu": "1"}))
        self.assertTrue(es.is_emulator({"ro.hardware": "ranchu"}))
        self.assertTrue(es.is_emulator({"ro.hardware": "goldfish"}))
        self.assertFalse(es.is_emulator({"ro.boot.qemu": "", "ro.hardware": "qcom"}))


class FatalTests(unittest.TestCase):
    def fatal(self, *blocks, package=PKG, crash_buffer=""):
        lines = es.parse_log("\n".join(blocks)) + es.parse_log(crash_buffer)
        return es.find_fatal(lines, package)

    def test_java_crash_of_the_package_counts(self):
        self.assertEqual(len(self.fatal(L(10, "Other", "hi"), crash(4242, PKG))), 1)

    def test_java_crash_of_a_sub_process_counts(self):
        self.assertEqual(len(self.fatal(crash(4243, PKG + ":remote"))), 1)

    def test_other_packages_do_not_count(self):
        self.assertEqual(self.fatal(crash(5, "com.other.app"), crash(6, PKG + "extra"), crash(7, "system_server")), [])

    def test_native_tombstone_counts_once_per_pid(self):
        tomb = L(77, "DEBUG", "pid: 4242, tid: 4250, name: RenderThread  >>> %s <<<" % PKG, "F")
        other = L(78, "DEBUG", "pid: 9, tid: 9, name: x  >>> com.other.app <<<", "F")
        self.assertEqual(len(self.fatal(tomb, other, tomb)), 1)

    def test_same_crash_in_main_and_crash_buffers_counts_once(self):
        c = crash(4242, PKG)
        self.assertEqual(len(self.fatal(c, crash_buffer=c)), 1)


class AnalyzeTests(unittest.TestCase):
    def test_anr_lines_of_the_package_and_its_sub_processes(self):
        main = "\n".join([
            L(604, "ActivityManager", "ANR in %s (%s/.Main)" % (PKG, PKG), "E"),
            L(604, "ActivityManager", "ANR in %s:remote" % PKG, "E"),
            L(604, "ActivityManager", "ANR in com.other.app (com.other.app/.A)", "E"),
        ])
        counts, _ = es.analyze(main, "", PKG, "morphe", [])
        self.assertEqual(counts["anr"], 2)

    def test_class_errors_only_count_for_the_apps_own_pids(self):
        main = "\n".join([
            L(604, "ActivityManager", "Start proc 4000:%s/u0a1 for next-top-activity {x}" % PKG),
            L(4000, "System", "java.lang.NoSuchMethodError: no method Lx/Y;->z()V", "W"),
            L(4001, "System", "java.lang.NoSuchMethodError: from another app", "W"),
            L(4000, "art", "java.lang.VerifyError: Verifier rejected class", "W"),
            L(4000, "art", "an unrelated warning", "W"),
        ])
        counts, ev = es.analyze(main, "", PKG, "morphe", [4000])
        self.assertEqual(counts["class_errors"], 2)
        self.assertEqual(len(ev["class_errors"]), 2)

    def test_a_process_that_died_is_known_from_its_start_proc_line(self):
        main = "\n".join([
            L(604, "ActivityManager", "Start proc 5555:%s:remote/u0a1 for service {x}" % PKG),
            L(604, "ActivityManager", "Start proc 6666:com.other.app/u0a2 for service {x}"),
            L(5555, "System", "java.lang.UnsatisfiedLinkError: dlopen failed", "E"),
            L(6666, "System", "java.lang.UnsatisfiedLinkError: not ours", "E"),
        ])
        counts, _ = es.analyze(main, "", PKG, "morphe", [])      # pidof finds nothing: it is already dead
        self.assertEqual(counts["class_errors"], 1)

    def test_tag_lines_match_case_insensitively_for_the_apps_pids_only(self):
        main = "\n".join([L(4000, "Morphe", "loaded"), L(4000, "HushGram", "x"), L(4000, "Other", "y"), L(1, "morphe", "z")])
        counts, _ = es.analyze(main, "", PKG, "morphe|revanced|hush|piko", [4000])
        self.assertEqual(counts["tag_lines"], 2)


class InstallAndVerdictTests(unittest.TestCase):
    def test_signature_conflict_is_classified(self):
        out = ("Performing Streamed Install\nadb: failed to install x.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: "
               "Existing package com.google.android.inputmethod.latin signatures do not match newer version; ignoring!]")
        self.assertEqual(es.classify_install(1, out)[0], "signature")
        self.assertEqual(es.classify_install(1, "Failure [INSTALL_FAILED_SHARED_USER_INCOMPATIBLE: x]")[0], "signature")
        self.assertEqual(es.classify_install(1, "Failure [INSTALL_FAILED_X: Package foo signatures don't match]")[0], "signature")

    def test_parse_error_and_success(self):
        kind, detail = es.classify_install(1, "Failure [INSTALL_PARSE_FAILED_NOT_APK: Failed to parse]")
        self.assertEqual(kind, "other")
        self.assertIn("INSTALL_PARSE_FAILED_NOT_APK", detail)
        self.assertEqual(es.classify_install(0, "Performing Streamed Install\nSuccess\n"), ("ok", ""))
        self.assertEqual(es.classify_install(124, "timed out after 900s")[0], "other")

    def test_signature_conflict_makes_the_verdict_unusable_and_a_parse_error_a_fail(self):
        sig = stage(installed=False, install_kind="signature", install_detail="signatures do not match")
        self.assertEqual(es.decide(stage(), sig)[0], "UNUSABLE")
        parse = stage(installed=False, install_kind="other", install_detail="INSTALL_PARSE_FAILED_NOT_APK")
        self.assertEqual(es.decide(stage(), parse)[0], "FAIL")

    def test_pass_with_and_without_warnings(self):
        verdict, reasons, warns = es.decide(stage(), stage())
        self.assertEqual((verdict, warns), ("PASS", []))
        self.assertTrue(reasons)
        verdict, _, warns = es.decide(stage(), stage(top=PKG + "/.Other", tag_lines=0))
        self.assertEqual(verdict, "PASS")
        self.assertEqual(len(warns), 2)

    def test_fail_cases(self):
        base = {"class_errors": 2}
        for bad in ({"running": False}, {"own_top": False, "top": "com.android.launcher/.Home"}, {"fatal": 1},
                    {"anr": 1}, {"class_errors": 3}):
            with self.subTest(bad=bad):
                self.assertEqual(es.decide(stage(**base), stage(**dict(base, **bad)))[0], "FAIL")
        self.assertEqual(es.decide(stage(**base), stage(**base))[0], "PASS")
        self.assertEqual(es.decide(stage(class_errors=5), stage(class_errors=2))[0], "PASS")

    def test_unusable_when_the_original_cannot_run(self):
        for bad in ({"installed": False, "install_kind": "other"}, {"running": False}, {"own_top": False, "top": "x/.y"},
                    {"fatal": 1}, {"anr": 1}):
            with self.subTest(bad=bad):
                self.assertEqual(es.decide(stage(**bad), stage())[0], "UNUSABLE")
        self.assertEqual(es.decide(stage(running=False), None)[0], "UNUSABLE")

    def test_signature_conflict_of_the_original_is_unusable_with_a_plain_reason(self):
        sig = stage(installed=False, install_kind="signature", install_detail="signatures do not match")
        verdict, reasons, _ = es.decide(sig, None)
        self.assertEqual(verdict, "UNUSABLE")
        self.assertIn("preinstalled system app", reasons[0])

    def test_abi_flag_only_when_the_emulator_can_run_arm64(self):
        self.assertEqual(es.abi_args("x86_64,arm64-v8a"), ["--abi", "arm64-v8a"])
        self.assertEqual(es.abi_args("x86_64,x86"), [])
        self.assertEqual(es.abi_args(""), [])

    def test_exit_codes(self):
        self.assertEqual(es.EXIT, {"PASS": 0, "FAIL": 2, "UNUSABLE": 3})


class ResolveOriginalTests(unittest.TestCase):
    def test_zip_members_are_unpacked_by_base_name_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            bundle = os.path.join(tmp, "app.apkm")
            with zipfile.ZipFile(bundle, "w") as z:
                z.writestr("base.apk", b"base")
                z.writestr("splits/config.en.apk", b"en")
                z.writestr("a\\b\\config.de.apk", b"de")
                z.writestr("../evil.apk", b"evil")
                z.writestr("../../up/deeper.APK", b"deeper")
                z.writestr("icon.png", b"png")
                z.writestr("info.json", b"{}")
            work = os.path.join(tmp, "work")
            os.mkdir(work)
            mode, apks = es.resolve_original(bundle, work)
            self.assertEqual(mode, "install-multiple")
            self.assertEqual(sorted(os.path.basename(a) for a in apks),
                             ["base.apk", "config.de.apk", "config.en.apk", "deeper.APK", "evil.apk"])
            for a in apks:
                self.assertEqual(os.path.dirname(a), work)
            self.assertEqual(sorted(os.listdir(work)), sorted(os.path.basename(a) for a in apks))
            self.assertEqual(sorted(os.listdir(tmp)), ["app.apkm", "work"])      # nothing escaped the work folder

    def test_folder_and_single_apk(self):
        with tempfile.TemporaryDirectory() as tmp:
            for n in ("base.apk", "split_a.apk", "info.json"):
                open(os.path.join(tmp, n), "wb").close()
            os.mkdir(os.path.join(tmp, "nested"))
            open(os.path.join(tmp, "nested", "deep.apk"), "wb").close()
            mode, apks = es.resolve_original(tmp, tmp)
            self.assertEqual((mode, [os.path.basename(a) for a in apks]), ("install-multiple", ["base.apk", "split_a.apk"]))
            self.assertEqual(es.resolve_original(os.path.join(tmp, "base.apk"), tmp),
                             ("install", [os.path.join(tmp, "base.apk")]))
            with self.assertRaises(es.ToolError):
                es.resolve_original(os.path.join(tmp, "info.json"), tmp)
            with self.assertRaises(es.ToolError):
                es.resolve_original(os.path.join(tmp, "missing"), tmp)


class AdbHelperTests(unittest.TestCase):
    def test_every_command_gets_the_emulator_serial(self):
        calls = []

        def fake_run(cmd, **kw):
            calls.append(cmd)
            return subprocess.CompletedProcess(cmd, 0, b"out", b"")

        adb = es.Adb("ADB", 5584)
        with mock.patch.object(es.subprocess, "run", fake_run):
            adb.run("uninstall", PKG)
            adb.sh("getprop ro.hardware")
            adb.prop("ro.hardware")
            adb.state()
            adb.run("emu", "kill")
        self.assertEqual(len(calls), 5)
        for cmd in calls:
            self.assertEqual(cmd[:3], ["ADB", "-s", "emulator-5584"])

    def test_port_decides_the_serial_and_bad_ports_are_refused(self):
        self.assertEqual(es.Adb("ADB", 5554).serial, "emulator-5554")
        for bad in (0, 5555, 5000, 99999, "5584"):
            with self.assertRaises(es.ToolError):
                es.serial_for(bad)

    def test_a_serial_that_is_not_an_emulator_is_refused(self):
        with self.assertRaises(es.ToolError):
            es.adb_argv("ADB", "R5CY13DJNPN", ["shell", "ls"])

    def test_only_the_adb_helper_runs_adb(self):
        """Every subprocess call in the module sits in Adb.run, avd_exists (emulator -list-avds) or start_emulator."""
        with open(os.path.join(HERE, "emu_smoke.py"), encoding="utf-8") as f:
            source = f.read()
        tree = ast.parse(source)
        allowed = {"run": "Adb", "avd_exists": None, "start_emulator": None}
        for fn in ast.walk(tree):
            if not isinstance(fn, ast.FunctionDef):
                continue
            for node in ast.walk(fn):
                if (isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)
                        and isinstance(node.func.value, ast.Name) and node.func.value.id == "subprocess"):
                    self.assertIn(fn.name, allowed, "subprocess call in %s" % fn.name)
        for banned in ("kill-server", "tcpip", "reconnect", '"usb"', "os.system", "shell=True"):
            self.assertNotIn(banned, source)


class LockTests(unittest.TestCase):
    def test_stale_lock_is_taken_over_and_removed_on_release(self):
        with tempfile.TemporaryDirectory() as tmp:
            dead = subprocess.Popen([sys.executable, "-c", "pass"])
            dead.wait()
            path = os.path.join(tmp, "lynx-emu-smoke-5584.lock")
            with open(path, "w") as f:
                f.write(str(dead.pid))
            got = es.acquire_lock(5584, 0, base=tmp)
            self.assertEqual(got, path)
            self.assertEqual(es.read_pid(path), str(os.getpid()))
            es.release_lock(got)
            self.assertFalse(os.path.exists(path))

    def test_live_holder_makes_the_wait_time_out(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "lynx-emu-smoke-5584.lock")
            with open(path, "w") as f:
                f.write(str(os.getpid()))          # this test process is alive
            with self.assertRaises(es.ToolError) as cm:
                es.acquire_lock(5584, 0, base=tmp)
            self.assertIn(str(os.getpid()), str(cm.exception))
            self.assertTrue(os.path.exists(path))
            es.release_lock(path)

    def test_release_leaves_a_lock_of_another_process_alone(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "x.lock")
            with open(path, "w") as f:
                f.write("1")
            es.release_lock(path)
            self.assertTrue(os.path.exists(path))

    def test_pid_alive(self):
        self.assertTrue(es.pid_alive(os.getpid()))
        self.assertFalse(es.pid_alive(0))


class ReportTests(unittest.TestCase):
    def test_report_starts_with_the_verdict_and_stays_short(self):
        orig = stage(name="original", dialog="com.google.android.permissioncontroller/x.GrantPermissionsActivity",
                     evidence={"class_errors": ["x" * 500] * 5})
        patched = stage(name="patched", evidence={"fatal": ["a\nb"] * 5, "anr": ["c"] * 5, "class_errors": ["d"] * 5})
        res = {"verdict": "FAIL", "package": PKG, "serial": "emulator-5584", "original": orig, "patched": patched,
               "warnings": ["w"], "reasons": ["r"]}
        lines = es.format_report(res).splitlines()
        self.assertTrue(lines[0].startswith("VERDICT: FAIL (exit 2)"))
        self.assertIn("(under GrantPermissionsActivity)", lines[2])
        self.assertLessEqual(len([l for l in lines if l.startswith("  ")]), 20)
        self.assertTrue(all(len(l) < 260 for l in lines))


if __name__ == "__main__":
    unittest.main()
