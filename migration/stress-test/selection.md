# 03 — Stress-test file selection (round 1)

Selected by score, not taste: per unit, sum over its inventory rows (excluding the
mechanical EF_MAPPING_REPLACED rows) of 1/sqrt(rows in that classification) — rare
classifications weigh more — plus 0.5 per distinct classification exercised.
Computed from `migration/inventory.tsv` + `migration/depmap/units.tsv` (2026-11-03).

| Rank | Unit | Score | Classes | Lines | Role |
|---|---|---|---|---|---|
| 1 | src/BugTracker.Web/btnet/util.cs | 21.34 | 13 | 1372 | bakeoff + pilot |
| 2 | src/btnet_service/POP3Main.cs | 15.89 | 12 | 793 | bakeoff + pilot |
| 3 | src/BugTracker.Web/Controls/BugList.ascx | 10.46 | 6 | 347 | pilot only (UI = redesign, D-004) |
| 4 | src/BugTracker.Web/Search/BugSearch.cs | 8.55 | 5 | 221 | bakeoff + pilot |
| — | src/BugTracker.Web/edit_bug.aspx | (design-level) | — | 2580 | pilot only: hardest page, sanctioned fixes F1/F2, judge S3/S8 |

Bakeoff ([3] = 3 core files): util.cs, POP3Main.cs, BugSearch.cs. UI units are excluded
from the bakeoff because the UI layer is a redesign (README "If you're redesigning";
RULEBOOK §7 D-004); their substitute is the pilot plus adversarial review of the §2B
design (already done in the 02 joint audit, J-U1..J-U18).

Pilot: the same 3 core files + BugList.ascx + edit_bug.aspx through the production
pipeline (implementer Sonnet 5.5, two reviewers Opus 5.5, adjudicator Opus 5.5,
fixer Sonnet 5.5; effort medium).

Guardrails: `.claude/settings.json` installed (adapted template; D-006).
Workspaces: A = git worktree `/tmp/claude-0/-home-user/9c6707b8-af5b-57fb-b9d4-be71e6af9275/scratchpad/st-A` on branch `stress-A-throwaway` (never merged);
B = `/tmp/claude-0/-home-user/9c6707b8-af5b-57fb-b9d4-be71e6af9275/scratchpad/st-B` (the 3 source files only);
pilot = `/tmp/claude-0/-home-user/9c6707b8-af5b-57fb-b9d4-be71e6af9275/scratchpad/st-pilot`.

## Round 1 closed (2026-11-04)
- Workspace A (worktree `st-A`, branch `stress-A-throwaway`) deleted: `git worktree remove --force` + `git branch -D` — confirmed, 0 remaining.
- Workspace B (`st-B`) deleted: `rm -rf` — confirmed.
- Pilot workspace `st-pilot` kept until the re-round reads its review files, then deleted.
