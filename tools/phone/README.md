# Phone helpers: driving the TEST phone over adb

Small shell and Python helpers for the phone test of a pack (`packs/PROCESS.md`, section C). Load them in Git Bash:

    export LYNX_ADB_SERIAL=<serial of the test phone>; source tools/phone/phone.sh

- `ui [n]`: list the labelled screen nodes (text, content-desc, class, bounds), via `uidump.py`.
- `shot <path>`: screenshot as `<path>.png` plus a small `<path>-small.jpg` (`shrink.py`, needs Pillow).
- `tap`, `swipe`, `back`, `home`, `focus`: plain input and the focused window. `open <package>` starts an app.
- `gtap`, `glong`, `gswipe <package> ...`: tap, long press, swipe only when that package is in front.
- `tapnode <package> <regex> [long]`: find a control by its text or description (`findnode.py`) and tap it.
- `crashes <package>`: FATAL EXCEPTION, ANR and process deaths in logcat. `infront <package>`: true when focused.

Rules:

- Only the test phone, never the daily phone.
- Use the guarded helpers (`gtap`, `glong`, `gswipe`, `tapnode`): a tap happens only when the intended app is in
  front. BACK on an app's first screen leaves the app, and a blind tap then lands in whatever is behind it.
- `uiautomator dump` fails on screens that never go idle (video): take a screenshot there.
- Read-only testing: no posting, liking, following, messaging or account changes, and do not open friends' stories.
- Never type a password or a passcode. Decline optional prompts (notifications, Bluetooth).
