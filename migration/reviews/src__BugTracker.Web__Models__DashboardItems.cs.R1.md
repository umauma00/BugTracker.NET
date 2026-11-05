# Review R1 — src/BugTracker.Web/Models/DashboardItems.cs

Target: `port/web/src/main/java/btnet/Models/DashboardItems.java` (manifest.tsv:46). No template (core unit, RULEBOOK §0 / §4).

Inventory: no rows key on `src/BugTracker.Web/Models/DashboardItems.cs`. The only `DashboardItems` rows (inventory.tsv:172-179) belong to `Models/Mapping/DashboardItemsMap.cs`. They use the field names `Id`, `UserId`, `ReportId`, `ChartType`, `Column`, `Row`, and all six exist in the target with the same names. A pure auto-property entity needs no per-site decisions, so the missing rows are not an inventory bug.

## Side-by-side check

| Source (DashboardItems.cs) | Target (DashboardItems.java) | Rule | Verdict |
|---|---|---|---|
| L4 `namespace btnet.Models` | L2 `package btnet.Models;` | §2A namespace row, §4 | OK |
| L6 `public partial class DashboardItems` | L4 `public class DashboardItems` | §2A `partial class` (one Java class; single part here) | OK |
| L8 `public int Id { get; set; }` | L5 `public int Id;` | §2A auto-property → public field; §2A `int`→`int` | OK |
| L9 `public int UserId { get; set; }` | L6 `public int UserId;` | same | OK |
| L10 `public int ReportId { get; set; }` | L7 `public int ReportId;` | same | OK |
| L11 `public string ChartType { get; set; }` | L8 `public String ChartType;` | same; String policy §1 | OK |
| L12 `public int Column { get; set; }` | L9 `public int Column;` | same; `Column` is not a Java keyword (§2A names row) | OK |
| L13 `public int Row { get; set; }` | L10 `public int Row;` | same; `Row` is not a keyword | OK |
| L1-2 `using System; using System.Collections.Generic;` (unused) | no imports | §4 imports: explicit single-type imports only for types used | OK |

Other checks:
- Default values: C# `int` = 0 and `string` = null. Java `int` = 0 and `String` = null. Same.
- Field order matches the source's declaration order, which matters for Jackson declaration order (§2A JSON, S2-34) and the runtime `DbSet` field ordering.
- Column types: `dashboard_items` columns (`setup.sql:726-733`) are `int not null` ×5 and `varchar(8) not null`. They map to `Integer`/`String` (§2A Database results), which matches `int`/`String` fields. No `Nullable<T>` is needed (§2A `T?` row does not apply).
- Header L1 `// Source: src/BugTracker.Web/Models/DashboardItems.cs` matches §6.1 (S1-41).
- Trailer L13 `// PORT STATUS: confidence=high todos=0` is the last line (ends with a single LF), and the count is correct: there are 0 `TODO(port)` markers (§6.7).
- No markers are required, none are present, and nothing was "fixed" (§0.1).
- Scope (§6.4): only the manifest target was written.
- Compile-level: no imports needed, no runtime API used, and the implicit no-arg constructor is available for runtime reflection.

## Findings

None.

(Severity counts: HIGH 0, MEDIUM 0, LOW 0.)

## Rulebook obedience

OBEDIENT. Deviations: 0. Every applicable rule was checked and followed:
- §2A General conventions (names, namespace→package, partial class, auto-property→public field, primitive/String types)
- §2A EF6 (entities: auto-properties → public fields)
- §4 (path, package, imports)
- §6.1 header, §6.2 completeness (all 6 members), §6.3 markers, §6.4 scope, §6.5 inventory grep (no rows, so nothing to cite), §6.7 trailer

## Rulebook gaps

1. **Unused `using` directives.** The rulebook never says that C# `using` directives with no referenced type produce no Java import. §4 only says "explicit single-type imports". The implementer did the obvious thing. Proposed clarification for §4: "A C# `using` directive is not translated; imports are derived from the types the Java file references." Evidence: `Models/DashboardItems.cs:1-2` (and every `Models/*.cs` entity).
2. **Single-part `partial class`.** The §2A `partial class` row covers pages+designer and `Startup` (multi-part). It does not explicitly cover a `partial` modifier on a type with only one part, which is common in the EF-generated `Models/*`. Proposed addition: "A `partial` type with a single part → an ordinary class; the modifier is dropped." Evidence: `Models/DashboardItems.cs:6`.
