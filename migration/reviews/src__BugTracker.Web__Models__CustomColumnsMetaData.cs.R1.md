# Review R1 — src/BugTracker.Web/Models/CustomColumnsMetaData.cs

Target: `port/web/src/main/java/btnet/Models/CustomColumnsMetaData.java` (11 lines).
Layer: core (`Models/`, structure-preserving, RULEBOOK §0). No template (§4: entities have none).

## Sources read

- `src/BugTracker.Web/Models/CustomColumnsMetaData.cs:1-13` (partial class, 4 auto-properties)
- `port/web/src/main/java/btnet/Models/CustomColumnsMetaData.java:1-11`
- `migration/RULEBOOK.md` 1-1885 (all of it, incl. §2A General / EF6, §3.1, §4, §6)
- `migration/inventory.tsv`: no rows for `Models/CustomColumnsMetaData.cs`; rows 164-171 cover
  `Models/Mapping/CustomColumnsMetaDataMap.cs` (field-name strings `Order`, `DropDownValues`,
  `SortSequence`, `DropDownType`), which the entity's field names must match.
- Cross-checks: `Models/Mapping/CustomColumnsMetaDataMap.cs:11-29`; `setup.sql:490-496`
  (`ccm_colorder int not null`, `ccm_dropdown_vals nvarchar(1000) not null`, `ccm_sort_seq int` nullable,
  `ccm_dropdown_type varchar(20) null`); runtime `DbSet.materialize` (`port/runtime/.../data/DbSet.java:289-320`:
  public fields via reflection, parameterless constructor, DBNull→null for reference types, type check on boxed type).

## Line-by-line check

| Source | Target | Rule | Verdict |
|---|---|---|---|
| `namespace btnet.Models` (:4) | `package btnet.Models;` (:2) | §2A namespace, §4 | OK |
| `public partial class CustomColumnsMetaData` (:6) | `public class CustomColumnsMetaData` (:4) | §2A `partial class` → one class; §4 primary type | OK (no other partial part exists in `src/`) |
| `public int Order { get; set; }` (:8) | `public int Order;` (:6) | §2A auto-property → public field; `int`→`int` | OK; PK column is `not null`, so primitive is faithful (EF6 also maps `int`) |
| `public string DropDownValues { get; set; }` (:9) | `public String DropDownValues;` (:7) | §2A | OK |
| `public Nullable<int> SortSequence { get; set; }` (:10) | `public Integer SortSequence;` (:8) | §2A `Nullable<T>` → boxed type | OK; column nullable, runtime stores `null` for DBNull |
| `public string DropDownType { get; set; }` (:11) | `public String DropDownType;` (:9) | §2A | OK |
| `using System; using System.Collections.Generic;` (:1-2) | none | §4 explicit imports only for used types | OK (nothing used) |
| — | `// Source: src/BugTracker.Web/Models/CustomColumnsMetaData.cs` (:1) | §6.1 | OK, exact repo-relative path |
| — | `// PORT STATUS: confidence=high todos=0` (:11, last line) | §6.7 | OK; file has 0 `TODO(port)` markers |

Member names match the inventory's map field-name strings (rows 164-171) exactly and case-sensitively, so
`DbSet` resolves `Property("Order")` etc. No name is a Java keyword (§2A, F-A18). Field initializers: none in
source, none in target. Implicit public no-arg constructor exists (required by `DbSet.materialize`). Field
declaration order equals source order. No comments in source to copy (§6.2). No markers needed: nothing
undecided, no BUG-worthy behavior, no §0.1 site.

## Findings

None.

## Rulebook obedience

OBEDIENT. Every applicable rule was followed: §2A General (auto-property → public field, `Nullable<int>` → `Integer`,
`partial` → one class, names unchanged), §2A EF6 (entity = public fields, no JPA annotations), §4 (path
`port/web/src/main/java/btnet/Models/CustomColumnsMetaData.java`, package verbatim, one file, no imports needed),
§6.1 header, §6.2 completeness (all 4 members), §6.3 markers (none, none required), §6.4 scope (only the target
written), §6.7 trailer with correct `todos=0`. §6.5 (inventory citation) is not triggered: no inventory row
exists for this unit and no decision came from one. No deviations.

## Rulebook gaps

None that affect this unit. (Observation only, no change proposed: §2A "Access modifiers" and the EF6 entity
bullet together fully decide plain entity classes; the absence of inventory rows for `Models/*.cs` entity files
is consistent with §5, since no per-site decision exists.)
