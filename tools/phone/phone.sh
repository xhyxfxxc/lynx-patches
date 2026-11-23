# Copyright (C) 2026 xhyxfxxc
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Helpers for read-mostly testing on the TEST phone (source this file in Git Bash). No taps happen unless tap/swipe
# (or a guarded variant) is called.
#
#     export LYNX_ADB_SERIAL=<serial of the test phone>
#     source tools/phone/phone.sh
#
# Never point this at the daily phone. See packs/PROCESS.md section C and tools/phone/README.md.
ADB="${ANDROID_HOME:-C:/Users/thoma/Android/Sdk}/platform-tools/adb.exe"
S="${LYNX_ADB_SERIAL:?set LYNX_ADB_SERIAL to the adb serial of the test phone}"
export MSYS_NO_PATHCONV=1
# Folder of this script (the Python helpers sit next to it). On Git Bash convert it to a Windows path for Python.
SCRATCH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if command -v cygpath >/dev/null 2>&1; then SCRATCH="$(cygpath -m "$SCRATCH")"; fi

sh() { "$ADB" -s $S shell "$@"; }

# ui [maxlines]: text / content-desc / bounds of every labelled node on screen
ui() {
  sh "rm -f /sdcard/ui.xml; uiautomator dump /sdcard/ui.xml 2>&1 | grep -v 'dumped to' ; cat /sdcard/ui.xml 2>/dev/null" | python -I "$SCRATCH/uidump.py" "${1:-70}"
}

# shot <windows-or-posix path without extension>: full screenshot plus a small copy for viewing
# (shrink.py runs without -I on purpose: it needs Pillow, which lives in the user's site-packages)
shot() {
  "$ADB" -s $S exec-out screencap -p > "$1.png"
  python "$SCRATCH/shrink.py" "$1.png" "$1-small.jpg"
}

tap() { sh "input tap $1 $2"; }
swipe() { sh "input swipe $1 $2 $3 $4 ${5:-300}"; }
back() { sh "input keyevent KEYCODE_BACK"; }
home() { sh "input keyevent KEYCODE_HOME"; }
focus() { sh "dumpsys window | grep -E 'mCurrentFocus' | head -1"; }

# crashes <package>: fatal exceptions, ANRs and process deaths for the package since the last logcat clear
crashes() {
  "$ADB" -s $S logcat -d -v brief 2>/dev/null | grep -a -E "FATAL EXCEPTION|AndroidRuntime.*$1|ANR in $1|Process $1.*has died|Force finishing activity $1|SIGSEGV|Fatal signal" | grep -a -v "avc: " | head -${2:-12}
  echo "(crash scan done)"
}

# infront <package>: true when the focused window belongs to the package
infront() { sh "dumpsys window | grep -E 'mCurrentFocus'" | grep -q "$1"; }
# gtap <package> x y: tap only when that package is in front
gtap() { if infront "$1"; then sh "input tap $2 $3"; else echo "NOT TAPPING: $1 is not in front: $(focus)"; return 1; fi; }
# glong <package> x y [ms]: long press only when that package is in front
glong() { if infront "$1"; then sh "input swipe $2 $3 $2 $3 ${4:-900}"; else echo "NOT PRESSING: $1 is not in front: $(focus)"; return 1; fi; }
# gswipe <package> x1 y1 x2 y2 [ms]
gswipe() { if infront "$1"; then sh "input swipe $2 $3 $4 $5 ${6:-350}"; else echo "NOT SWIPING: $1 is not in front: $(focus)"; return 1; fi; }
# open <package>: bring the app to the front through its launcher entry
open() { sh "monkey -p $1 -c android.intent.category.LAUNCHER 1" >/dev/null 2>&1; }

# tapnode <package> <regex> [long]: tap (or long-press) the first node whose text or content-desc fully matches; refuses otherwise
tapnode() {
  if ! infront "$1"; then echo "NOT TAPPING: $1 is not in front: $(focus)"; return 1; fi
  local hit
  hit=$(sh "rm -f /sdcard/ui.xml; uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml 2>/dev/null" | python -I "$SCRATCH/findnode.py" "$2") || { echo "NOT TAPPING: no node matches /$2/"; return 1; }
  local xy=${hit%% |*}
  echo "tap ${hit}"
  if [ "$3" = "long" ]; then sh "input swipe $xy $xy 900"; else sh "input tap $xy"; fi
}
