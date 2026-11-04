# 03 — Stress-test report (gate, signed off under the standing mandate)

| | Round 1 | Round 2 |
|---|---|---|
| Bakeoff files (core) | util.cs, POP3Main.cs, BugSearch.cs | print_bug.cs, bug_list.cs, version_control.cs |
| Substantive differences | 51 | 29 |
| rulebook right / native right / defensible / both wrong | 24 / 3 / 17 / 7 | 13 / 1 / 12 / 3 |
| Pilot units (prod pipeline) | 5 (+BugList.ascx, edit_bug.aspx) | 5 (+search.aspx, edit_user.aspx) |
| Pilot HIGH / MEDIUM findings | 0 / 7 | 0 / 3 |
| Confirmed findings (all applied by fixers, 0 flagged) | 27 | 12 |
| Amendments applied | S1-1..S1-48 | S2-1..S2-38 |

- Reports: `bakeoff-r1.md`, `bakeoff-r2.md`, `pilot-r1.md`, `pilot-r2.md`, `selection.md`.
- Workspaces deleted after each round (confirmed in `selection.md`).
- Stop condition met at section level only after round 2 → closed under the mandate (D-008).
- Pipeline defects found and fixed by the pilot: P1 trailer format, P2 relative repo paths, P3 stale rendered prompts. Nested agents unavailable → orchestrator drives every role (per-unit prompt files rendered by `migration/pipeline/render.py`).
- Interruption: account rate limit mid-round 2; all agents resumed from saved state.

## Next step estimate (04 translation fan-out)
Manifest 217 units (213 batches; 4 two-unit cycles). Pilot timings per unit: implementer 1.5–8.5 min, reviewers 3–7 min (parallel), adjudicator 1–2 min, fixer 0.3–1 min → ≈ 8–18 min wall-clock per unit through the pipeline; 5 agent runs per unit ≈ 1,085 runs.
【仮定】 6 units in flight → ≈ 217 × 13 min / 6 ≈ 8 h wall-clock, plus rate-limit pauses (5-hour windows) — realistically 1.5–3 days. Active attention: 0 (mandate).
