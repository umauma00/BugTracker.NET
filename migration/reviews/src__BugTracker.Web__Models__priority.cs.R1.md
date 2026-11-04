# Review R1 — src/BugTracker.Web/Models/priority.cs

Reviewer: R1 (adversarial, read-only). Date: 2026-11-04.
Target: `port/web/src/main/java/btnet/Models/Priority.java` (manifest row 38).
No template: core layer, `Models/` entity (§0 core posture, §4).

## Inputs checked

- Source `src/BugTracker.Web/Models/priority.cs:1-15`: `public partial class Priority` in `btnet.Models`, six auto-properties
  `int Id`, `string Name`, `int SortOrder`, `string BackgroundColor`, `string Style`, `int Default`.
- Target `Priority.java:1-13`.
- Inventory: `grep "Models/priority.cs" migration/inventory.tsv` → **no rows**. The unit has no gap sites
  (no nullable, ref/out, LINQ, strings, SQL), so no row is expected and its absence is not an inventory bug (§5).
  Related rows for `Models/Mapping/priorityMap.cs` (L11 `HasKey("Id")`, L14 `Property("Name")`, …) use the field
  names `Id`, `Name`, …, which the target matches.
- Cross-checks: `Models/Mapping/priorityMap.cs:11-32` maps all six members (pr_id, pr_name, pr_sort_seq,
  pr_background_color, pr_style, pr_default); `setup.sql:471-479` declares pr_id/pr_sort_seq/pr_default as `int`
  (→ `Integer` values per §2A DataSet, so primitive `int` fields match C# `int` and EF's own type expectations),
  pr_name/pr_background_color/pr_style as nvarchar (→ `String`). No other `partial class Priority` part exists in
  `src/` (grep), so nothing needs merging.
- RULEBOOK read in full (lines 1-1884), incl. §3.1.

## Findings

None.

Item-by-item verification:

| Check | Rule | Source | Target | Result |
|---|---|---|---|---|
| Header | §6.1 [S1-41] | — | `:1` `// Source: src/BugTracker.Web/Models/priority.cs` | correct, repo-relative |
| Package | §2A namespace row; §4 | `:4` `namespace btnet.Models` | `:2` `package btnet.Models;` | correct, same case |
| Path | §4 naming rule | file stem `priority`, type `Priority` (case-insensitive match) | `btnet/Models/Priority.java` | matches manifest row 38 |
| `partial class` | §2A `partial class` row | `:6` | `:4` one `public class Priority` | correct (single part) |
| Access | §2A access modifiers [S2-12] | `public` | `public` | kept |
| Auto-properties → public fields, same names, same order | §2A auto-property row; §2A EF6 "Entities"; §2A JSON (declaration order) [S2-34] | `:8-13` | `:6-11` | all six present, same order, same names/casing |
| Types | §2A primitive row (`int`→`int`); `string`→`java.lang.String` (§1 String policy) | `int`/`string` | `int`/`String` | correct; no initializers in source, none in target |
| Keyword collision | §2A general / §4 (`default` → `default_`) | `Default` | `Default` | Java is case-sensitive; `Default` is not a keyword, so no `_` — correct |
| `using System; using System.Collections.Generic;` | §4 imports (explicit, only what is used) | `:1-2` | no imports | correct (nothing used; `String` is `java.lang`) |
| Completeness | §6.2 | 6 members, no comments | 6 members | complete; no source comments to copy |
| Markers | §6.3, §3 marker formats | no BUG/TODO sites | none | correct |
| Trailer | §6.7 | — | `:13` `// PORT STATUS: confidence=high todos=0` | last line; `todos=0` equals the count of `TODO(port)` markers (0) |
| Runtime API | §3.1 | — | no runtime calls/imports | nothing to verify |
| Compile-level | — | — | valid Java 21: public top-level class matches file name, fields well-typed | no errors visible |
| Sanctioned fixes | §0.1 | — | no `FIXED(port)` | none applicable, none claimed |

Behavioral notes considered and rejected as findings:
- Primitive `int` for `Id`/`SortOrder`/`Default`: C# declares non-nullable `int`; the columns are `int not null`
  (`setup.sql:473,475,478`), so no DBNull/`Integer` boxing question arises at the entity (§2A `T?` row applies only to
  `int?`). Correct.
- Field order matters for Jackson (§2A JSON, declaration order) and is preserved.
- Thymeleaf/SpEL access of `p.Name` etc. on public fields is a consumer/runtime concern (B1/B5), not this unit's.

## Rulebook obedience

**OBEDIENT.** The implementer followed every applicable rule: §6.1 header, §2A namespace/package, §2A `partial
class`, §2A access-modifier rule [S2-12], §2A auto-property → public field, §2A EF6 entity rule, §2A primitive type
mapping, §2A/§4 keyword rule (correctly not applied to `Default`), §4 naming/path and import rules, §6.2
completeness, §6.3 markers, §6.4 scope (only the manifest target), §6.7 trailer. No deviation found, including none
masked by a correct-looking result. §6.5 (`// inventory:` citation) is not triggered: no inventory row decided anything
in this unit.

## Rulebook gaps

1. **Unused `using` directives in core units.** The rulebook says imports are "explicit single-type imports" (§4)
   and `<%@ Import %>` → "nothing" (§2B B3), but never states the C# `using` directive rule for `.cs` units (drop; emit
   a Java import only for types actually referenced). Every implementer here got it right by inference, so risk is
   low. Proposed rule (§2A General): "`using N;` directives are not translated; each referenced type gets its own
   single-type import per §4; a `using` that no referenced type needs produces nothing." Evidence:
   `Models/priority.cs:1-2`, `Models/status.cs` (same pattern; `Status.java` has no imports).
2. **Single-part `partial class` outside pages/Startup.** The `partial class` row (§2A) cites pages+designer and
   `Startup`; EF entities (`Models/*.cs`, generated by EF Power Tools as `partial`) are single-part. The row's
   "one Java class" covers them, but an explicit note would close the question "is `partial` a signal to look for
   another part / leave a TODO?". Proposed addition: "A `partial` type with only one part in `src/` (EF entities,
   `Models/*.cs`) → a plain class; no marker." Evidence: `Models/priority.cs:6`; grep finds no second part.
