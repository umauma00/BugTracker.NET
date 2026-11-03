# BugTracker.NET → Java migration

This repository is being migrated to Java with the Claude Code Migration Kit
(vendored unmodified in `migration-kit/`, upstream commit in
`migration-kit/UPSTREAM-COMMIT.txt`). Every migration session follows the
kit's operating manual:

@migration-kit/CLAUDE.md

- Kit path: `migration-kit/` (fill `[kit path]` in prompts with this).
- Target language: Java.
- Migration artifacts live in `migration/`. Current gate: see
  `migration/feasibility.md` §9 (gate decisions signed off 2026-11-03:
  Java 21 + Spring Boot 4 + Thymeleaf; models Opus 5.5 / Sonnet 5.5 at
  medium effort; old app cannot run — deviation D-001).
