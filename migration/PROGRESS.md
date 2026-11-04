# Migration progress and standing mandate

## Standing mandate (2026-11-03, from the user)

> 推奨のやり方で06までやり切れる？ 一回一回こちらに聞かなくていい。全て推奨のやり方でユーザーが承認しています。

All gate decisions are pre-approved **as the recommendations stated in the gate
reports**. The orchestrator records each gate as signed off under this mandate
and continues to the next prompt without stopping. It still stops and asks
only for things it cannot do itself (environment settings, credentials).

### Decisions signed off under the mandate

| Gate | Decision |
|---|---|
| 00 | Java 21 + Spring Boot 4 + Thymeleaf; models Opus 5.5 (rulebook, reviewers, adjudication) / Sonnet 5.5 (implementers, fixers), effort medium; screen_capture out of scope (`feasibility.md` §9) |
| 00b | Judge accepted (validation deferred, D-001). Seed patches P1/P2 approved. **Original defects: option (b)** — the major defects listed in `judge-report.md` §5 are specified as *fixed* behavior; the judge tests that pin them are rewritten to the fixed expectation (see D-003) |
| 01 | Dependency map + manifest accepted; provisional naming (`migration/scripts/manifest_subs.py`) becomes RULEBOOK §4; **single Maven module** for the web app (package cycle tolerated), separate module for btnet_service |
| SQL Server | Recommendation: allow `pmc-geofence.trafficmanager.net` in the environment's network settings (user action). Until then 05b/05c/06 runtime proofs are blocked — see D-001 |

## Step status

| Step | Status | Artifacts |
|---|---|---|
| 00 feasibility | done | `migration/feasibility.md` |
| 00b judge | done (validation deferred) | `judge/`, `migration/judge-report.md` |
| 01 dependency map | done | `migration/depmap/`, `migration/manifest.tsv` |
| 02 gap inventory + rulebook | done | `migration/RULEBOOK.md`, `migration/inventory*.tsv` |
| 03 stress test | done (2 rounds, D-008) | `migration/stress-test/` |
| 04 translation | in progress | |
| 05 survey build | — | |
| 05b smoke / 05c parity | — | |
| 06 post-parity | — | |
