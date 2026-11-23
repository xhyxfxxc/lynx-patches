# Briefs for pack review and port sessions

- `AUDIT_BRIEF.md`: first audit of a pack that is not in the repo yet (security and P0/P1 bugs).
- `UPGRADE_AUDIT_BRIEF.md`: review of an upstream update that `pack_sync.py sync` staged but did not commit.
- `PACK_BRIEF.md`: porting an imported pack (apply the Lynx changes, mark it modified, `LYNX.md`, build, validate).

A main session hands one of these to a Sonnet sub-session together with a pack-specific prompt (the pack, its
paths, scope, versions and leads). Sub-sessions cannot write report files: they return the report as text, and
the main session saves it under `C:\Users\thoma\Morpheapp\upstream-src\_audit\`. The order of work is in
[../PROCESS.md](../PROCESS.md).
