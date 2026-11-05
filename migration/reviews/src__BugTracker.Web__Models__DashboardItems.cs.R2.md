# Review R2 — src/BugTracker.Web/Models/DashboardItems.cs

Reviewer: R2 (adversarial, read-only). Date: 2026-11-05.
Target: `port/web/src/main/java/btnet/Models/DashboardItems.java` (13 lines). No template (core-layer entity, §0 / §4).

Read: RULEBOOK.md 1–1884 (whole), inventory rows (none for this unit; rows 172–179 belong to `Models/Mapping/DashboardItemsMap.cs`), source, target, `Models/Mapping/DashboardItemsMap.cs`, `Context.cs:33,71`, runtime `DbSet.java` (field reflection), `setup.sql:726` / `upgrade.sql:651-659` (column nullability).

## Side-by-side check

| Source (`DashboardItems.cs`) | Target (`DashboardItems.java`) | Rule | Verdict |
|---|---|---|---|
| `namespace btnet.Models` (:4) | `package btnet.Models;` (:2) | §2A namespace, §4 | OK |
| `public partial class DashboardItems` (:6) | `public class DashboardItems` (:4) | §2A `partial class` → one Java class | OK |
| `public int Id { get; set; }` (:8) | `public int Id;` (:5) | §2A auto-property → public field; §2A EF6 entities | OK |
| `public int UserId { get; set; }` (:9) | `public int UserId;` (:6) | same | OK |
| `public int ReportId { get; set; }` (:10) | `public int ReportId;` (:7) | same | OK |
| `public string ChartType { get; set; }` (:11) | `public String ChartType;` (:8) | same | OK |
| `public int Column { get; set; }` (:12) | `public int Column;` (:9) | same; `Column` is not a Java keyword (§2A names, F-A18) | OK |
| `public int Row { get; set; }` (:13) | `public int Row;` (:10) | same | OK |
| `using System; using System.Collections.Generic;` (:1-2), unused | no imports | §4 imports (explicit, only what is used) | OK |

Additional checks:
- Header `// Source: src/BugTracker.Web/Models/DashboardItems.cs` matches the on-disk file name exactly (§6.1, S1-41).
- Trailer `// PORT STATUS: confidence=high todos=0` is the last line; 0 `TODO(port)` markers in the file, so the count is right (§6.7).
- Field declaration order = source order, so Jackson's declaration-order serialization (§2A JSON, S2-34) and `DbSet`'s `getFields()` ordering both see what EF/Newtonsoft would.
- Every name `DashboardItemsMap.cs:11,14,20-25` passes to `Property(...)`/`HasKey(...)` (`Id`, `ChartType`, `UserId`, `ReportId`, `Column`, `Row`) exists as a public, non-static, scalar field, so runtime `DbSet.EntityModel`'s fail-fast check (`DbSet.java:62-70`) will not trip.
- Implicit public no-arg constructor exists (`DbSet.java:293` `getDeclaredConstructor().newInstance()`).
- Nullability: every `ds_*` column is `not null` (`upgrade.sql:653-658`), so primitive `int` (not `Integer`) matches the C# non-nullable `int` (§2A `T?` row applies only to `Nullable<T>`).
- No source comments to copy (§6.2); no markers needed (§6.3); no inventory rows for this file, and none are required for a plain entity (§5).
- Scope: only the manifest target was written (§6.4); no second top-level type (§4).

## Findings

None. HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

OBEDIENT. Every applicable rule was followed: §2A auto-property → public field, §2A EF6 entities, §2A `partial class`, §2A names/keywords, §4 package/path/imports/one-file-per-unit, §6.1 header, §6.2 completeness (all 6 members, no extras), §6.3 markers (none), §6.5 inventory (no rows to cite), §6.7 trailer. No deviations found.

## Rulebook gaps

- G1 (minor, no defect today): the §2A `partial class` row speaks only of partials merged across units (pages + designer, `Startup`). It does not say outright that an EF-generated `partial` entity with **one** part (all 23 `Models/*.cs` entities) just drops the `partial` keyword with no comment or marker. Implementers got it right here and in the sibling entities (`Priority.java`), but a two-agent disagreement is possible (one could add a `// partial` comment or a TODO). Proposed rule text: "A `partial` type with a single part in `src/` → an ordinary Java class; the keyword is dropped silently (no comment, no marker)." Evidence: `Models/DashboardItems.cs:6`, `Models/priority.cs` → `port/web/src/main/java/btnet/Models/Priority.java:4`.
