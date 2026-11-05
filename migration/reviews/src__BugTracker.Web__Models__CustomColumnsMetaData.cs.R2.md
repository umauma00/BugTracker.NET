# Review R2 — src/BugTracker.Web/Models/CustomColumnsMetaData.cs

Target: `port/web/src/main/java/btnet/Models/CustomColumnsMetaData.java` (11 lines). No template (core unit, §0, §4).

## Inputs checked

- Source: `src/BugTracker.Web/Models/CustomColumnsMetaData.cs:1-13`: `public partial class` with 4 auto-properties (`int Order`, `string DropDownValues`, `Nullable<int> SortSequence`, `string DropDownType`).
- Inventory: no rows for `src/BugTracker.Web/Models/CustomColumnsMetaData.cs`. Rows 164-171 belong to `Models/Mapping/CustomColumnsMetaDataMap.cs`. They use the field names `Order`, `DropDownValues`, `SortSequence`, `DropDownType` as strings, and the target declares fields with exactly those names. A plain entity with no ref, nullable-column or region decisions needs no inventory row (§5), so the missing rows are **not** an inventory bug.
- RULEBOOK read end to end (lines 1-1884), including §2A General conventions, §2A EF6, §3/§3.1, §4 and §6.
- Runtime check: `btnet.runtime.data.DbSet` materializes with `getDeclaredConstructor().newInstance()` and `Class.getFields()` (DbSet.java:106,293). The target's implicit public no-arg constructor and its public fields meet that requirement.
- Cross-references: no translated or source code outside `Models/` references this type (grep). The map's Java target does not exist yet, so nothing can disagree with it.

## Findings

None.

Checked against the C# and found no defect:

| Item | Source | Target | Rule | Verdict |
|---|---|---|---|---|
| Header | — | :1 `// Source: src/BugTracker.Web/Models/CustomColumnsMetaData.cs` | §6.1 [S1-41] | correct repo-relative path |
| Package | :4 `namespace btnet.Models` | :2 `package btnet.Models;` | §2A namespace, §4 | correct |
| `partial` class | :6 | :4 `public class CustomColumnsMetaData` | §2A `partial class` (one Java class); manifest has one unit for this type | correct |
| `public int Order { get; set; }` | :8 | :6 `public int Order;` | §2A auto-property → public field; `int` → `int` | correct |
| `public string DropDownValues` | :9 | :7 `public String DropDownValues;` | same | correct |
| `public Nullable<int> SortSequence` | :10 | :8 `public Integer SortSequence;` | §2A `T?`/`Nullable<T>` → boxed type | correct (default `null` = C# default) |
| `public string DropDownType` | :11 | :9 `public String DropDownType;` | auto-property | correct |
| Member order | :8-11 | :6-9 | declaration order matters for Jackson [S2-34] and DbSet `getFields()` | preserved |
| `using System; using System.Collections.Generic;` | :1-2 | no imports | §4 explicit imports only; nothing used | correct |
| Names | — | unchanged; `Order` is not a Java keyword | §2A names [F-A18], §4 | correct |
| Markers / trailer | — | :11 `// PORT STATUS: confidence=high todos=0` | §6.7 | count 0 = actual 0 |
| Completeness | 4 members | 4 fields | §6.2 | complete |
| Scope | — | only the manifest target written | §6.4 | correct |

## Rulebook obedience

**OBEDIENT.** I found no deviations. Every applicable rule is followed: §2A auto-property, `Nullable<T>`, `partial class` and namespace rows; §2A EF6 ("Entities: auto-properties → public fields"); §4 naming, path and imports; §6.1 header; §6.2 completeness; §6.3 markers (none needed); §6.4 scope; §6.7 trailer. The unit has no inventory rows, so §6.5 citations do not apply.

## Rulebook gaps

1. **Unused C# `using` directives in core units.** The rulebook settles this only for markup (§2B B3 "Static/`<%@ Import %>` directives → nothing"). §4 only implies that a core `.cs` `using` with no counterpart is dropped, and does not say whether it should be copied as a comment. Proposed rule for §2A General conventions: "`using X;` directives → no Java statement; Java imports are derived from the types actually referenced (§4)." Evidence: `Models/CustomColumnsMetaData.cs:1-2` (and every `Models/*.cs`), where the translation dropped them, which is the reading every reviewer will expect. Low impact.
2. **Entity nullability vs. the map.** `SortSequence` is `Nullable<int>` and correctly becomes `Integer`. `Order` is a non-nullable `int` primary key, so a SQL NULL in `ccm_colorder` would fail in EF6 with `InvalidOperationException` ("The 'Order' property … could not be set to a 'null' value"). §3 does not say what `DbSet` materialization does when it writes `DBNull` into a primitive field. Proposed rule for §2A EF6 / §3 `DbSet`: "materializing SQL NULL into a primitive (`int`, `boolean`, …) field throws `InvalidOperationException("The '<Prop>' property on '<Type>' could not be set to a 'null' value. …")`, as EF6." This belongs to the runtime author; translators do nothing. Latent: `ccm_colorder` is a key column.
