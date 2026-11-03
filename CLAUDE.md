# BugTracker.NET → Java migration

This repository is being migrated to Java with the Claude Code Migration Kit
(vendored unmodified in `migration-kit/`, upstream commit in
`migration-kit/UPSTREAM-COMMIT.txt`). Every migration session follows the
kit's operating manual:

@migration-kit/CLAUDE.md

- Kit path: `migration-kit/` (fill `[kit path]` in prompts with this).
- Target language: Java.
- Migration artifacts live in `migration/`. Current gate: see
  `migration/feasibility.md` (Step 00 verdict) — do not start prompt 00b or
  01 until the human signs off on it.
