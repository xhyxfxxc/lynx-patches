#!/usr/bin/env python3
# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
"""Start-up test of an original app against its patched build on an Android EMULATOR (never a phone).

    python -I tools/emu_smoke.py --package <app id> --original <folder | .apk | .apkm/.apks/.xapk>
        --patched <patched .apk> --out <folder> [--avd lynx_packs] [--port 5584] [--settle 40]
        [--boot-timeout 420] [--lock-wait 900] [--log-tags "morphe|revanced|hush|piko"] [--keep]

Starts the AVD without a window if needed, installs the original app and starts it, replaces it with the patched
app and starts that, then compares the two from the logs. It only installs, starts and observes: no sign-in, no
taps, no typing (the one input is keycode 82 to wake a lock screen). Every adb call carries `-s emulator-<port>`
(one helper builds every command line) and the device must prove it is an emulator before anything is installed.
Needs ANDROID_HOME or ANDROID_SDK_ROOT. Nothing is downloaded. Installs use --abi arm64-v8a (the phone's build)
when the image can run arm64; a system permission dialog on top of the app's own screen is not a failure.
Exit codes: 0 PASS, 2 FAIL (the original starts, the patched app does not), 3 UNUSABLE (the emulator cannot run
this app at all, so nothing can be said about the patch), 1 the tool itself failed.
Writes <out>/emu-smoke.json, <stage>-logcat.txt, -crash.txt, -activities.txt, <stage>.png and emulator.log.
"""
import argparse
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time
import zipfile
from collections import namedtuple

sys.dont_write_bytecode = True      # keep tools/ free of __pycache__ files

EXIT = {"PASS": 0, "FAIL": 2, "UNUSABLE": 3}
CLASS_RE = re.compile(r"\b(VerifyError|NoClassDefFoundError|NoSuchMethodError|NoSuchFieldError|"
                      r"ClassNotFoundException|UnsatisfiedLinkError|IncompatibleClassChangeError)\b")
LOG_RE = re.compile(r"^\d\d-\d\d \d\d:\d\d:\d\d\.\d+\s+(\d+)\s+\d+\s+([VDIWEFA])\s+(.*?)\s*: (.*)$")
PROCESS_RE = re.compile(r"Process: (\S+?), PID: (\d+)")
TOMBSTONE_RE = re.compile(r">>> (\S+) <<<")
START_PROC_RE = re.compile(r"Start proc (\d+):([^/\s]+)/")
ANR_RE = re.compile(r"\bANR in (\S+)")
ACT = r"ActivityRecord\{\S+ u\d+ ([\w.$]+)/(\S+?)(?: t(-?\d+))?[ }]"
TOP_RE = re.compile(r"(?:topResumedActivity=|\bResumedActivity:\s*)" + ACT)
ACT_RE = re.compile(ACT)
DIALOG_PKGS = ("permissioncontroller", "packageinstaller")
SIG_RE = re.compile(r"INSTALL_FAILED_UPDATE_INCOMPATIBLE|INSTALL_FAILED_SHARED_USER_INCOMPATIBLE|"
                    r"signatures? (?:do not|does not|don't) match", re.I)
Line = namedtuple("Line", "pid level tag msg raw")


class ToolError(Exception):
    """A problem of the tool or the environment (exit code 1), not a verdict about the app."""


def log(msg):
    sys.stderr.write("[emu-smoke] %s %s\n" % (time.strftime("%H:%M:%S"), msg))
    sys.stderr.flush()


def text(raw):
    return raw.decode("utf-8", "replace").replace("\r\n", "\n")


# ---- adb: the only place that builds an adb command line ----------------------------------------------------

def serial_for(port):
    if not isinstance(port, int) or port < 5554 or port > 5682 or port % 2:
        raise ToolError("--port must be an even emulator console port between 5554 and 5682")
    return "emulator-%d" % port


def adb_argv(exe, serial, args):
    if not serial.startswith("emulator-"):
        raise ToolError("refusing to run adb for a serial that is not an emulator: %s" % serial)
    return [exe, "-s", serial] + [str(a) for a in args]


class Adb:
    def __init__(self, exe, port):
        self.exe, self.serial = exe, serial_for(port)

    def run(self, *args, timeout=120):
        """Returns (returncode, stdout bytes). stderr is folded in at the end for error messages."""
        try:
            p = subprocess.run(adb_argv(self.exe, self.serial, args), stdin=subprocess.DEVNULL,
                               capture_output=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            return 124, ("timed out after %ds: adb %s" % (timeout, " ".join(map(str, args[:3])))).encode()
        except OSError as e:
            raise ToolError("cannot run adb: %s" % e)
        return p.returncode, p.stdout + (b"\n" + p.stderr if p.stderr else b"")

    def sh(self, command, timeout=120):
        rc, out = self.run("shell", command, timeout=timeout)
        return rc, text(out)

    def prop(self, name):
        return self.sh("getprop " + name, 20)[1].strip()

    def state(self):
        return text(self.run("get-state", timeout=30)[1]).strip().splitlines()[0:1] == ["device"]


# ---- pure helpers (unit-tested) --------------------------------------------------------------------------------

def is_emulator(props):
    return (props.get("ro.kernel.qemu") == "1" or props.get("ro.boot.qemu") == "1"
            or props.get("ro.hardware") in ("ranchu", "goldfish"))


def own_name(name, package):
    return name == package or name.startswith(package + ":")


def parse_log(raw):
    out = []
    for ln in raw.splitlines():
        m = LOG_RE.match(ln)
        if m:
            out.append(Line(int(m.group(1)), m.group(2), m.group(3), m.group(4), ln))
    return out


def component(pkg, cls):
    return "%s/%s" % (pkg, pkg + cls if cls.startswith(".") else cls)


def parse_top_activity(dump, package):
    """Returns (screen, dialog). A system permission dialog on top of one of the app's own screens (same task) is
    not a failure: screen is then the app's own screen under it and dialog names the dialog."""
    m = TOP_RE.search(dump)
    if not m:
        return "", ""
    top = component(m.group(1), m.group(2))
    if m.group(1) != package and m.group(3) and any(k in m.group(1) for k in DIALOG_PKGS):
        for a in ACT_RE.finditer(dump):
            if a.group(1) == package and a.group(3) == m.group(3):
                return component(a.group(1), a.group(2)), top
    return top, ""


def parse_launcher(out):
    for ln in reversed(out.strip().splitlines()):
        if re.match(r"^[\w.$]+/[\w.$]+$", ln.strip()):
            return ln.strip()
    return ""


def find_fatal(lines, package):
    """Java crashes (FATAL EXCEPTION + Process line) and native crashes (>>> pkg <<<) of this package."""
    seen, found = set(), []
    for i, ln in enumerate(lines):
        if ln.tag == "AndroidRuntime" and ln.msg.startswith("FATAL EXCEPTION"):
            for nxt in lines[i + 1:i + 8]:
                m = PROCESS_RE.match(nxt.msg) if nxt.tag == "AndroidRuntime" else None
                if m:
                    if own_name(m.group(1), package) and ("java", m.group(2)) not in seen:
                        seen.add(("java", m.group(2)))
                        found.append(ln.raw + "\n" + nxt.raw)
                    break
        m = TOMBSTONE_RE.search(ln.msg)
        if m and own_name(m.group(1), package):
            p = re.search(r"pid: (\d+)", ln.msg)
            key = ("native", p.group(1) if p else str(ln.pid))
            if key not in seen:
                seen.add(key)
                found.append(ln.raw)
    return found


def app_pids(lines, package, live_pids):
    pids = set(live_pids)
    for ln in lines:
        m = START_PROC_RE.search(ln.msg)
        if m and own_name(m.group(2), package):
            pids.add(int(m.group(1)))
    return pids


def analyze(main_text, crash_text, package, tag_pattern, live_pids):
    main, crash = parse_log(main_text), parse_log(crash_text)
    pids = app_pids(main, package, live_pids)
    tag_re = re.compile(tag_pattern, re.I)
    ev = {"fatal": find_fatal(main + crash, package),
          "anr": [l.raw for l in main if (m := ANR_RE.search(l.msg)) and own_name(m.group(1), package)],
          "class_errors": [l.raw for l in main if l.pid in pids and CLASS_RE.search(l.msg)],
          "tag_lines": [l.raw for l in main if l.pid in pids and tag_re.search(l.tag)]}
    counts = {k: len(v) for k, v in ev.items()}
    return counts, {k: v[:5] for k, v in ev.items() if v and k != "tag_lines"}


def abi_args(abilist):
    """Install the arm64 build, as the phone runs it: an emulator's own x86 libs are often missing or incomplete."""
    return ["--abi", "arm64-v8a"] if "arm64-v8a" in abilist.split(",") else []


def classify_install(rc, out):
    """Returns (kind, detail): kind is ok, signature or other."""
    if rc == 0 and re.search(r"^Success\b", out, re.M):
        return "ok", ""
    lines = [l.strip() for l in out.splitlines() if l.strip()]
    detail = next((l for l in lines if "Failure" in l or "rror" in l), lines[-1] if lines else "no output")
    return ("signature" if SIG_RE.search(out) else "other"), detail[:300]


def check_original(orig):
    """Reasons why the original makes the emulator UNUSABLE for this app (empty list: the original is fine)."""
    bad = []
    if orig["install_kind"] == "signature":
        bad.append("even the original is refused: the app already on the emulator image (a preinstalled system app) "
                   "has another signature (%s)" % orig["install_detail"])
    elif not orig["installed"]:
        bad.append("the original did not install (%s)" % orig["install_detail"])
    else:
        if not orig["running"]:
            bad.append("the original is not running after the settle time")
        elif not orig["own_top"]:
            bad.append("the original shows none of its own screens (top: %s)" % (orig["top"] or "none"))
        if orig["fatal"]:
            bad.append("the original has %d fatal error(s)" % orig["fatal"])
        if orig["anr"]:
            bad.append("the original has %d ANR report(s): the emulator is too slow for this app" % orig["anr"])
    return bad


def decide(orig, patched):
    """Returns (verdict, reasons, warnings) from the two stage dicts (patched is None when skipped)."""
    bad = check_original(orig)
    if bad or patched is None:
        return "UNUSABLE", bad or ["the patched stage did not run"], []
    warn, fail = [], []
    if not patched["installed"]:
        if patched["install_kind"] == "signature":
            return "UNUSABLE", ["the patched app is refused because its signature differs from the preinstalled "
                                "system app on the emulator image (%s)" % patched["install_detail"]], []
        return "FAIL", ["the patched app did not install: %s" % patched["install_detail"]], []
    if not patched["running"]:
        fail.append("the patched app is not running after the settle time")
    elif not patched["own_top"]:
        fail.append("the patched app shows none of its own screens (top: %s)" % (patched["top"] or "none"))
    if patched["fatal"]:
        fail.append("the patched app has %d fatal error(s)" % patched["fatal"])
    if patched["anr"]:
        fail.append("the patched app has %d ANR report(s)" % patched["anr"])
    if patched["class_errors"] > orig["class_errors"]:
        fail.append("the patched app logs %d class-loading errors, the original %d"
                    % (patched["class_errors"], orig["class_errors"]))
    if fail:
        return "FAIL", fail, []
    if patched["top"] != orig["top"]:
        warn.append("the top screen differs: original %s, patched %s" % (orig["top"], patched["top"]))
    if patched["tag_lines"] == 0:
        warn.append("no log line with a patch tag: the patch code may not have loaded")
    return "PASS", ["the patched app installs, runs, shows its own screen and logs nothing worse than the original"], warn


def resolve_original(path, workdir):
    """Returns ("install"|"install-multiple", [apk paths]). A zip is unpacked into workdir by base names only."""
    if os.path.isdir(path):
        apks = sorted(os.path.join(path, n) for n in os.listdir(path) if n.lower().endswith(".apk"))
        if not apks:
            raise ToolError("no .apk files directly inside %s" % path)
        return "install-multiple", apks
    if not os.path.isfile(path):
        raise ToolError("--original does not exist: %s" % path)
    if path.lower().endswith(".apk"):
        return "install", [path]
    if not zipfile.is_zipfile(path):
        raise ToolError("--original is neither a folder, an .apk nor a zip: %s" % path)
    apks = []
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            base = os.path.basename(info.filename.replace("\\", "/"))
            if info.is_dir() or not base.lower().endswith(".apk"):
                continue
            dest = os.path.join(workdir, base)
            if os.path.exists(dest):
                dest = os.path.join(workdir, "%d_%s" % (len(apks), base))
            with z.open(info) as src, open(dest, "wb") as dst:
                shutil.copyfileobj(src, dst)
            apks.append(dest)
    if not apks:
        raise ToolError("no .apk members in %s" % path)
    return "install-multiple", sorted(apks)


def format_report(res):
    lines = ["VERDICT: %s (exit %d)" % (res["verdict"], EXIT[res["verdict"]]),
             "package %s on %s" % (res["package"], res["serial"])]
    for st in (res["original"], res["patched"]):
        if st is None:
            lines.append("patched : skipped (the original was already unusable)")
            continue
        under = " (under %s)" % st["dialog"].rsplit(".", 1)[-1] if st["dialog"] else ""
        lines.append("%-8s: installed=%s running=%s top=%s%s fatal=%d anr=%d class_errors=%d tag_lines=%d seconds=%d"
                     % (st["name"], "yes" if st["installed"] else "NO", "yes" if st["running"] else "no",
                        st["top"] or "none", under, st["fatal"], st["anr"], st["class_errors"], st["tag_lines"],
                        st["seconds"]))
    lines += ["warning: " + w for w in res["warnings"]] + ["reason: " + r for r in res["reasons"]]
    ev = [(st["name"], k, ln) for st in (res["original"], res["patched"]) if st
          for k, v in st["evidence"].items() for ln in v]
    lines += ["  %s %s: %s" % (n, k, ln.replace("\n", " | ")[:200]) for n, k, ln in ev[:12]]
    return "\n".join(lines[:40])


# ---- lock, SDK, emulator ----------------------------------------------------------------------------------------

def pid_alive(pid):
    if pid <= 0:
        return False
    if os.name == "nt":             # never os.kill() on Windows: it terminates the process
        import ctypes
        k = ctypes.WinDLL("kernel32", use_last_error=True)
        k.OpenProcess.restype = ctypes.c_void_p
        k.CloseHandle.argtypes = [ctypes.c_void_p]
        h = k.OpenProcess(0x1000, False, pid)
        if not h:
            return ctypes.get_last_error() == 5
        code = ctypes.c_ulong()
        ok = k.GetExitCodeProcess(ctypes.c_void_p(h), ctypes.byref(code))
        k.CloseHandle(h)
        return bool(ok) and code.value == 259
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def read_pid(path):
    with open(path) as f:
        return f.read().strip()


def acquire_lock(port, wait, base=None):
    path = os.path.join(base or tempfile.gettempdir(), "lynx-emu-smoke-%d.lock" % port)
    deadline = time.time() + wait
    while True:
        try:
            fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.write(fd, str(os.getpid()).encode())
            os.close(fd)
            return path
        except FileExistsError:
            pass
        try:
            raw = read_pid(path)
            holder = int(raw) if raw.isdigit() else 0
            stale = not pid_alive(holder) and (holder or time.time() - os.path.getmtime(path) > 30)
        except (OSError, ValueError):
            stale, holder = False, 0
        if stale:
            try:
                os.remove(path)
            except OSError:
                pass
            continue
        if time.time() >= deadline:
            raise ToolError("another emu_smoke run (pid %s) holds %s; waited %ds" % (holder or "?", path, wait))
        time.sleep(min(5, max(0.2, deadline - time.time())))


def release_lock(path):
    try:
        if read_pid(path) == str(os.getpid()):
            os.remove(path)
    except OSError:
        pass


def find_tools():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise ToolError("neither ANDROID_HOME nor ANDROID_SDK_ROOT is set")
    ext = ".exe" if os.name == "nt" else ""
    adb = os.path.join(sdk, "platform-tools", "adb" + ext)
    emu = os.path.join(sdk, "emulator", "emulator" + ext)
    for p in (adb, emu):
        if not os.path.isfile(p):
            raise ToolError("not found: %s" % p)
    return adb, emu


def avd_exists(emu, avd):
    p = subprocess.run([emu, "-list-avds"], stdin=subprocess.DEVNULL, capture_output=True, timeout=60)
    return avd in text(p.stdout).split()


def start_emulator(emu, avd, port, out):
    cmd = [emu, "-avd", avd, "-port", str(port), "-no-window", "-no-audio", "-no-boot-anim", "-no-snapshot",
           "-gpu", "swiftshader_indirect"]
    kw = {"creationflags": 0x00000008 | 0x00000200} if os.name == "nt" else {"start_new_session": True}
    logf = open(os.path.join(out, "emulator.log"), "wb")
    return subprocess.Popen(cmd, stdin=subprocess.DEVNULL, stdout=logf, stderr=subprocess.STDOUT, close_fds=True, **kw)


def wait_boot(adb, timeout, proc=None):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if proc is not None and proc.poll() is not None:
            raise ToolError("the emulator process exited early (code %s), see emulator.log" % proc.returncode)
        if adb.state() and adb.prop("sys.boot_completed") == "1" and adb.sh("pm path android", 30)[0] == 0:
            return
        time.sleep(3)
    raise ToolError("the emulator did not finish booting within %ds" % timeout)


# ---- one stage: install, start, observe ------------------------------------------------------------------------

def dismiss_lock(adb):
    m = re.search(r"\bshowing=(true|false)", adb.sh("dumpsys window policy", 30)[1])
    if m is None or m.group(1) == "true":
        adb.sh("input keyevent 82", 20)


def start_app(adb, package):
    dismiss_lock(adb)
    adb.run("logcat", "-G", "16M", timeout=30)      # best effort: a bigger log buffer for chatty apps
    adb.run("logcat", "-c", timeout=30)
    comp = parse_launcher(adb.sh("cmd package resolve-activity --brief -c android.intent.category.LAUNCHER " + package, 60)[1])
    if comp:
        rc, out = adb.sh("am start -W -n " + shlex.quote(comp), 180)
        if rc == 0 and "Error" not in out:
            return "am start " + comp
    adb.sh("monkey -p %s -c android.intent.category.LAUNCHER --pct-appswitch 100 1" % package, 120)
    return "monkey"


def run_stage(adb, name, package, install_args, args, out):
    st = {"name": name, "installed": False, "install_kind": "none", "install_detail": "", "start": "", "running": False,
          "pids": [], "top": "", "dialog": "", "own_top": False, "fatal": 0, "anr": 0, "class_errors": 0, "tag_lines": 0,
          "seconds": 0, "evidence": {}}
    t0 = time.time()
    log("%s: uninstalling %s if present" % (name, package))
    adb.run("uninstall", package, timeout=300)
    log("%s: installing (%s)" % (name, install_args[0]))
    rc, raw = adb.run(*install_args, timeout=900)
    st["install_kind"], st["install_detail"] = classify_install(rc, text(raw))
    st["installed"] = st["install_kind"] == "ok"
    if st["installed"]:
        st["start"] = start_app(adb, package)
        log("%s: started via %s, settling %ds" % (name, st["start"], args.settle))
        time.sleep(args.settle)
        rc, pidof = adb.sh("pidof " + package, 30)
        st["pids"] = [int(p) for p in pidof.split() if p.isdigit()]
        st["running"] = bool(st["pids"])
        acts = adb.sh("dumpsys activity activities", 60)[1]
        st["top"], st["dialog"] = parse_top_activity(acts, package)
        with open(os.path.join(out, name + "-activities.txt"), "w", encoding="utf-8") as f:
            f.write(acts)
        st["own_top"] = st["top"].split("/")[0] == package
        main_raw = adb.run("exec-out", "logcat", "-d", "-v", "threadtime", timeout=180)[1]
        crash_raw = adb.run("exec-out", "logcat", "-d", "-b", "crash", timeout=60)[1]
        for suffix, data in (("logcat.txt", main_raw), ("crash.txt", crash_raw)):
            with open(os.path.join(out, "%s-%s" % (name, suffix)), "wb") as f:
                f.write(data)
        png = adb.run("exec-out", "screencap", "-p", timeout=60)[1]
        if png[:4] == b"\x89PNG":
            with open(os.path.join(out, name + ".png"), "wb") as f:
                f.write(png)
        counts, st["evidence"] = analyze(text(main_raw), text(crash_raw), package, args.log_tags, st["pids"])
        st.update(counts)
    st["seconds"] = round(time.time() - t0)
    return st


# ---- main ---------------------------------------------------------------------------------------------------------

def parse_args(argv):
    ap = argparse.ArgumentParser(prog="emu_smoke.py", description="Start-up test of an original and a patched app on an emulator.")
    ap.add_argument("--package", required=True, help="app id, e.g. com.instagram.android")
    ap.add_argument("--original", required=True, help="folder of .apk files, one .apk, or an .apkm/.apks/.xapk zip")
    ap.add_argument("--patched", required=True, help="the patched .apk")
    ap.add_argument("--out", required=True, help="folder for logs, screenshots and emu-smoke.json")
    ap.add_argument("--avd", default="lynx_packs")
    ap.add_argument("--port", type=int, default=5584, help="emulator console port; the serial is emulator-<port>")
    ap.add_argument("--settle", type=int, default=40, help="seconds to wait after the start before looking")
    ap.add_argument("--boot-timeout", type=int, default=420)
    ap.add_argument("--lock-wait", type=int, default=900, help="seconds to wait for another run on the same port")
    ap.add_argument("--log-tags", default="morphe|revanced|hush|piko", help="regex of log tags that show the patch code loaded")
    ap.add_argument("--keep", action="store_true", help="keep the emulator running and the patched app installed")
    return ap.parse_args(argv)


def run(args):
    if not os.path.isfile(args.patched) or not args.patched.lower().endswith(".apk"):
        raise ToolError("--patched must be an existing .apk file: %s" % args.patched)
    try:
        re.compile(args.log_tags)
    except re.error as e:
        raise ToolError("--log-tags is not a valid regex: %s" % e)
    adb_exe, emu_exe = find_tools()
    adb = Adb(adb_exe, args.port)
    os.makedirs(args.out, exist_ok=True)
    lock = acquire_lock(args.port, args.lock_wait)
    work, proc, touched = tempfile.mkdtemp(prefix="lynx-emu-smoke-"), None, False
    try:
        if not adb.state():
            if not avd_exists(emu_exe, args.avd):
                raise ToolError("the AVD %s does not exist (never created or downloaded by this tool)" % args.avd)
            log("starting emulator %s on port %d" % (args.avd, args.port))
            proc = start_emulator(emu_exe, args.avd, args.port, args.out)
        wait_boot(adb, args.boot_timeout, proc)
        props = {n: adb.prop(n) for n in ("ro.kernel.qemu", "ro.boot.qemu", "ro.hardware")}
        if not is_emulator(props):
            raise ToolError("%s is not an emulator (%s): nothing was installed" % (adb.serial, props))
        mode, apks = resolve_original(args.original, work)
        abi = abi_args(adb.prop("ro.product.cpu.abilist"))
        touched = True
        orig = run_stage(adb, "original", args.package, [mode, "-r"] + abi + apks, args, args.out)
        patched = None
        if not check_original(orig):
            patched = run_stage(adb, "patched", args.package, ["install", "-r"] + abi + [args.patched], args, args.out)
        verdict, reasons, warns = decide(orig, patched)
        res = {"verdict": verdict, "reasons": reasons, "warnings": warns, "package": args.package,
               "serial": adb.serial, "avd": args.avd, "abi": abi[1:] or "default", "emulator_started_here": proc is not None,
               "original": orig, "patched": patched, "settle": args.settle, "log_tags": args.log_tags,
               "finished": time.strftime("%Y-%m-%d %H:%M:%S")}
        with open(os.path.join(args.out, "emu-smoke.json"), "w") as f:
            json.dump(res, f, indent=2)
        return res
    finally:
        shutil.rmtree(work, ignore_errors=True)
        if not args.keep:
            try:
                if touched:
                    adb.run("uninstall", args.package, timeout=300)
                if proc is not None:
                    log("stopping the emulator this run started")
                    adb.run("emu", "kill", timeout=60)
            except ToolError:
                pass
        release_lock(lock)


def main(argv=None):
    args = parse_args(argv)
    try:
        res = run(args)
    except ToolError as e:
        sys.stderr.write("emu_smoke: %s\n" % e)
        return 1
    except KeyboardInterrupt:
        sys.stderr.write("emu_smoke: interrupted\n")
        return 1
    print(format_report(res).encode("ascii", "replace").decode())
    return EXIT[res["verdict"]]


if __name__ == "__main__":
    sys.exit(main())
