#!/usr/bin/env python3
"""Diff a reviewer's independently gathered edge rows against the map, in both
directions, restricted to the reviewer's own `from` units.

Usage: python3 -I migration/scripts/depmap_diff.py --map migration/depmap/edges.tsv --review A1.tsv [--units u1,u2,...]
  MISSED   = reviewer row not in the map (candidate script miss)
  INVENTED = map edge from a reviewed unit that the reviewer did not list (candidate invention)
Each discrepancy must be checked against the cited source line before the script is touched.
"""
import argparse


def read(path):
    rows = {}
    for i, line in enumerate(open(path, encoding="utf-8").read().splitlines()):
        if i == 0 or not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        rows.setdefault((parts[0].strip(), parts[1].strip()), parts[2].strip() if len(parts) > 2 else "")
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--map", required=True)
    ap.add_argument("--review", required=True)
    ap.add_argument("--units", default=None, help="comma-separated reviewed units (default: every `from` in the review)")
    a = ap.parse_args()
    m, r = read(a.map), read(a.review)
    units = set(a.units.split(",")) if a.units else {f for f, _ in r}
    missed = sorted(k for k in r if k not in m and k[0] in units)
    invented = sorted(k for k in m if k[0] in units and k not in r)
    print(f"reviewed_units={len(units)} review_rows={len(r)} map_rows_in_scope={sum(1 for k in m if k[0] in units)} "
          f"missed={len(missed)} invented={len(invented)}")
    for k in missed:
        print(f"MISSED\t{k[0]}\t{k[1]}\t{r[k]}")
    for k in invented:
        print(f"INVENTED\t{k[0]}\t{k[1]}\t{m[k]}")


if __name__ == "__main__":
    main()
