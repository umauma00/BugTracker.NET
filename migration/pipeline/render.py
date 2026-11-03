#!/usr/bin/env python3
"""Render a role prompt for one unit (placeholders filled) to a file, so the
orchestrator hands each agent a one-line pointer instead of a long prompt.

Usage: python3 -I migration/pipeline/render.py --unit <unit> --out <OUT> [--roles implementer,reviewer,adjudicator,fixer]
Writes <OUT>/migration/prompts/<uid>.<role>[.R1|.R2].md and prints their paths.
A unit is a units.tsv unit name; for a cycle batch pass several --unit values (one pipeline).
"""
import argparse
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--unit", action="append", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--roles", default="implementer,reviewer,adjudicator,fixer")
    a = ap.parse_args()
    units = {l.split("\t")[0]: l.split("\t")[2] for l in (REPO / "migration/depmap/units.tsv").read_text().splitlines()[1:]}
    manifest = {l.split("\t")[0]: l.split("\t")[1] for l in (REPO / "migration/manifest.tsv").read_text().splitlines()[1:]}
    sources, targets = [], []
    for u in a.unit:
        sources += units[u].split(",")
        t = manifest[u]
        if t not in targets:
            targets.append(t)
    uid = a.unit[0].replace("/", "__")
    out = Path(a.out)
    pdir = out / "migration" / "prompts"
    pdir.mkdir(parents=True, exist_ok=True)
    (out / "migration" / "reviews").mkdir(parents=True, exist_ok=True)
    fills = {"{UNIT_SOURCES}": ", ".join(sources), "{TARGETS}": ", ".join(targets),
             "{OUT}": str(out), "{UID}": uid}
    for role in a.roles.split(","):
        text = (REPO / "migration/pipeline" / f"{role}.md").read_text()
        variants = ["R1", "R2"] if role == "reviewer" else [None]
        for v in variants:
            t = text
            for k, val in fills.items():
                t = t.replace(k, val)
            if v:
                t = t.replace("{REVIEWER}", v)
            p = pdir / (f"{uid}.{role}.{v}.md" if v else f"{uid}.{role}.md")
            p.write_text(t)
            print(p)


if __name__ == "__main__":
    main()
