# 02 — Gap inventory report (gate, signed off under the standing mandate)

- Sweep: `migration/scripts/gap_sweep.py` → `migration/inventory/sweep.tsv`: **956 sites** (13 categories).
- Inventory: `migration/inventory.tsv` — `wc -l` = 985 (header + **984 rows**: every swept site + 28 sites added by reviewers/auditors). Status: 984 confirmed, **0 unknown**.
- Shards: `migration/inventory.*.tsv` (merged, deduped on (file, symbol, source_construct)).
- Skeptic review (2 × Opus, disjoint core/UI): core 146 confirmed / 13 refuted / 74 amended; UI 271 / 15 / 40 — all applied after checking the source (`review.core.tsv`, `review.ui.tsv`).
- Rulebook amendments: C1–C35 (classifiers, reviewers) + joint audit J-C1–J-C26 (core) and J-U1–J-U18 (UI) + N1–N8 — applied to RULEBOOK.md under the mandate (D-005); log in `rulebook-conflicts.md`, `joint-audit.core.md`, `joint-audit.ui.md`.
- UNKNOWN rows: none left (all decided under the mandate, each decision cited in the row).

Top classifications:
```
    315 EF_MAPPING_REPLACED
    128 END_RESPONSE
     73 TEXT_REGION
     42 REGEX_SAME
     38 FORMAT_SAME
     32 SESSION_USER
     32 REF_HOLDER
     29 DEFAULT
     27 CATCH_MAP_TYPE
     24 CACHE_SHARED_MUTABLE
     23 REDIRECT_END
     23 LINQ_TO_SQL
```

Next step estimate (03 stress test): 3 bakeoff files (~2,400 lines) × 2 translators + inspector, pilot 5 units × 5 agents ≈ 31 agent runs; 【仮定】 wall-clock 1.5–3 h, attention 0 min (mandate).
