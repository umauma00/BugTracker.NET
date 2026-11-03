#!/usr/bin/env python3
"""Emit the --sub arguments for migration-kit/scripts/make_manifest.py from the
PROVISIONAL naming rule (to be ratified as RULEBOOK.md §4 in prompt 02).

Why not plain prefix/extension substitutions: Java requires a public class to
live in <ClassName>.java under its package directory, and in this repo 33 .cs
files are named differently from their class or namespace (btnet/bug.cs declares
btnet.Bug; App_Start/WebApiConfig.cs declares btnet.WebApiConfig), while page
classes collide with core classes (print_bug.aspx = btnet.print_bug vs
btnet/print_bug.cs = btnet.PrintBug; tags.aspx vs btnet/tags.cs differ only in
case — fatal on case-insensitive file systems). So each unit gets one exact
full-path substitution derived from its declared types.

Provisional rule:
  module   src/BugTracker.Web -> port/web,  src/btnet_service -> port/service
  .cs      <module>/src/main/java/<namespace as path>/<PrimaryType>.java
           primary type = the top-level type whose name equals the file stem
           (case- and underscore-insensitive), else the first declared type;
           global-namespace types go to package `btnet`
  markup   .aspx/.Master/.asax/.inc -> package btnet.pages, class = Inherits type
           file stem (not Inherits: LoggedOut.Master inherits btnet.LoggedIn); .ascx -> its own
           namespace (btnet.Controls)
Refuses (exit 1) if two units map to the same path case-insensitively.

Usage: migration/scripts/.venv/bin/python -I migration/scripts/manifest_subs.py --repo . > migration/depmap/manifest_subs.txt
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import depmap_csharp as d  # noqa: E402

MODULES = {"src/BugTracker.Web": "port/web", "src/btnet_service": "port/service"}


def norm(s):
    return s.replace("_", "").replace(".", "").lower()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True, type=Path)
    a = ap.parse_args()
    repo = a.repo.resolve()
    rows = [l.split("\t") for l in (repo / "migration/depmap/units.tsv").read_text().splitlines()[1:]]
    out, seen = [], {}
    for unit, _pkg, _files in rows:
        mod = next(m for m in MODULES if unit.startswith(m + "/"))
        base = f"{MODULES[mod]}/src/main/java/"
        p = repo / unit
        ext = p.suffix.lower()
        if ext == ".cs":
            info = d.FileInfo()
            d.parse_cs(p.read_text(encoding="utf-8-sig", errors="replace").encode("utf-8"), info)
            nested = {t[0] for t in info.types}
            tops = [t[0] for t in info.types if not any(t[0].startswith(o + ".") for o in nested if o != t[0])]
            stem = p.name[:-3]
            prim = next((t for t in tops if norm(t.rsplit(".", 1)[-1]) == norm(stem)), tops[0] if tops else None)
            if prim is None:
                print(f"ERROR no type declared in {unit}", file=sys.stderr)
                sys.exit(1)
            if "." not in prim:
                prim = "btnet." + prim
            target = base + prim.replace(".", "/") + ".java"
        else:
            text = p.read_text(encoding="utf-8-sig", errors="replace")
            inherits = None
            for kind, attrs, _ in d.directives(text):
                if kind in ("page", "control", "master", "application"):
                    inherits = attrs.get("inherits")
            # file stem, not Inherits: LoggedOut.Master inherits btnet.LoggedIn (a
            # copy-paste in the original), yet it is a distinct layout
            cls = p.name.rsplit(".", 1)[0]
            if ext == ".ascx":
                ns = inherits.rsplit(".", 1)[0] if inherits and "." in inherits else "btnet.Controls"
            else:
                ns = "btnet.pages"
            target = base + ns.replace(".", "/") + "/" + cls + ".java"
            if ext == ".inc":
                target = base + "btnet/pages/" + p.name.replace(".", "_") + ".java"
        key = target.lower()
        if key in seen and seen[key][1] == target and ext == ".cs" and seen.get(("partial", target)):
            # A partial class split across units (Startup.cs + App_Start/Startup.Auth.cs,
            # one SCC in order.txt) becomes ONE Java class: both rows share the target
            # and are translated together as one batch.
            print(f"NOTE partial class merged: {unit} + {seen[key][0]} -> {target}", file=sys.stderr)
            out.append(f"{unit}={target}")
            continue
        if key in seen:
            print(f"ERROR collision: {unit} and {seen[key][0]} -> {target}", file=sys.stderr)
            sys.exit(1)
        seen[key] = (unit, target)
        if ext == ".cs" and "partial" in p.read_text(encoding="utf-8-sig", errors="replace"):
            seen[("partial", target)] = True
        out.append(f"{unit}={target}")
    print("\n".join(out))


if __name__ == "__main__":
    main()
