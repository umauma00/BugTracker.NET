# Per-unit translation pipeline (prompt 04; the 03 pilot runs exactly this)

One **unit** = one row group of `migration/manifest.tsv` (a cycle batch from
`migration/depmap/order.txt` is processed as one unit). Five roles, five separate
contexts, models fixed by the Model plan (effort medium for all):

| Role | Model | Writes |
|---|---|---|
| Implementer | Sonnet 5.5 | the unit's target file(s) only |
| Reviewer R1, R2 | Opus 5.5 | `<OUT>/migration/reviews/<uid>.R1.md` / `.R2.md` only |
| Adjudicator | Opus 5.5 | `<OUT>/migration/reviews/<uid>.confirmed.md` only |
| Fixer | Sonnet 5.5 | the unit's target file(s) only |

`<OUT>` is the output root: the repository root for the fan-out, a scratch
directory for the 03 pilot (targets are written under `<OUT>/<target path>`).
`<uid>` = the first source path of the unit with `/` → `__`.

Order: Implementer → (R1 ∥ R2) → Adjudicator → Fixer (skipped when nothing is
confirmed). The orchestrator checks after each stage that the expected file
exists and is non-empty (queue = disk state; `queue_runner.mjs`).

The role prompts are `implementer.md`, `reviewer.md`, `adjudicator.md`,
`fixer.md` in this directory. They are the only prompts used — the pilot uses
no special versions. Placeholders: `{UNIT_SOURCES}` (all source files of the
unit, from `migration/depmap/units.tsv`), `{TARGETS}` (manifest targets),
`{OUT}`, `{UID}`, `{REVIEWER}` (R1|R2).

Nobody in the loop compiles, runs tests, edits RULEBOOK.md or the inventory, or
runs mutating git commands (`.claude/settings.json` denies them). Rulebook
amendment proposals go into the reviewer/fixer reports, never into the rulebook.
