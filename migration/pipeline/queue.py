#!/usr/bin/env python3
"""Disk-state queue for the per-unit pipeline (prompt 04, CLAUDE.md rule 2).

A pipeline unit = one line of migration/depmap/order.txt (a cycle = one unit).
Its stage is derived ONLY from files on disk:
  implement   no target file yet
  review      target exists, R1 or R2 review missing
  adjudicate  both reviews exist, confirmed file missing
  fix         confirmed file has findings, no <uid>.done marker
  done        <uid>.done exists, or confirmed says NONE
Dependency order is respected: a unit becomes available for `implement` only
when every unit it depends on has at least been implemented (target on disk).

Usage:
  python3 -I migration/pipeline/queue.py status
  python3 -I migration/pipeline/queue.py next <n>     # up to n units whose next stage can start now
  python3 -I migration/pipeline/queue.py done <uid>   # write the done marker (after the fixer)
In-flight bookkeeping: the orchestrator touches migration/prompts/<uid>.<stage>.inflight when it
launches a stage and removes it when the stage's file appears; `next` skips in-flight units.
"""
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
MIG = REPO / "migration"


def load():
    manifest = {l.split("\t")[0]: l.split("\t")[1] for l in (MIG / "manifest.tsv").read_text().splitlines()[1:]}
    units = []
    for line in (MIG / "depmap/order.txt").read_text().splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        members = line.split("\t")
        units.append(members)
    deps = {}
    for l in (MIG / "depmap/edges.tsv").read_text().splitlines()[1:]:
        a, b = l.split("\t")[:2]
        deps.setdefault(a, set()).add(b)
    return manifest, units, deps


def uid(members):
    return members[0].replace("/", "__")


def stage(members, manifest):
    u = uid(members)
    rv = MIG / "reviews"
    targets = [REPO / manifest[m] for m in members]
    if (rv / f"{u}.done").exists():
        return "done"
    if not all(t.exists() and t.stat().st_size > 0 for t in targets):
        return "implement"
    if not ((rv / f"{u}.R1.md").exists() and (rv / f"{u}.R2.md").exists()):
        return "review"
    c = rv / f"{u}.confirmed.md"
    if not c.exists():
        return "adjudicate"
    text = c.read_text(errors="replace")
    head = text.split("## Not confirmed")[0]
    if "NONE" in head and "| C1 " not in head and "C1 |" not in head:
        return "done"
    return "fix"


def inflight(u, st):
    return (MIG / "prompts" / f"{u}.{st}.inflight").exists()


def main():
    manifest, units, deps = load()
    cmd = sys.argv[1] if len(sys.argv) > 1 else "status"
    implemented = set()
    stages = []
    for m in units:
        s = stage(m, manifest)
        stages.append((m, s))
        if s != "implement":
            implemented.update(m)
    if cmd == "status":
        from collections import Counter
        c = Counter(s for _, s in stages)
        print(f"units={len(units)} " + " ".join(f"{k}={c.get(k,0)}" for k in ["implement", "review", "adjudicate", "fix", "done"]))
        return
    if cmd == "done":
        (MIG / "reviews").mkdir(exist_ok=True)
        (MIG / "reviews" / f"{sys.argv[2]}.done").write_text("done\n")
        return
    if cmd == "next":
        n = int(sys.argv[2])
        # auto-land: an in-flight mark whose stage is no longer the unit's current stage is stale
        for m, s in stages:
            u = uid(m)
            for st in ("implement", "review", "adjudicate", "fix"):
                f = MIG / "prompts" / f"{u}.{st}.inflight"
                if f.exists() and st != s:
                    f.unlink()
        out = []
        for m, s in stages:
            if s == "done":
                continue
            u = uid(m)
            if inflight(u, s):
                continue
            if s == "implement":
                need = set().union(*(deps.get(x, set()) for x in m)) - set(m)
                if not need <= implemented:
                    continue
            out.append(f"{s}\t{u}\t{','.join(m)}")
            if len(out) >= n:
                break
        print("\n".join(out))


if __name__ == "__main__":
    main()
