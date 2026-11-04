# Review R1 — src/BugTracker.Web/Models/BugRelationShip.cs

Target: `port/web/src/main/java/btnet/Models/BugRelationShip.java` (12 lines). No template (core layer, §0; §4 places templates only for pages/masters/.ascx/.inc).

Inputs read: RULEBOOK §0, §0.1, §1, §2A (General conventions, EF6), §4, §5, §6; inventory (`grep BugRelationShip migration/inventory.tsv` → 7 rows, all for `Models/Mapping/BugRelationShipMap.cs` L11–L23, none for this unit); source; target; cross-checked `Models/Mapping/BugRelationShipMap.cs`, `Models/Context.cs:26,64`, `setup.sql:398-405`, runtime `btnet/runtime/data/DbSet.java` (materialize: public fields via `getFields()`, parameterless constructor, primitive field + DBNull → InvalidOperationException).

## Line-by-line check

| Source | Target | Rule | Verdict |
|---|---|---|---|
| `namespace btnet.Models` (:4) | `package btnet.Models;` (:2) | §2A namespace, §4 | OK |
| `public partial class BugRelationShip` (:6) | `public class BugRelationShip` (:4) | §2A `partial class` → one Java class; §4 path `port/web/src/main/java/btnet/Models/BugRelationShip.java` | OK |
| `public int Id { get; set; }` (:8) | `public int Id;` (:6) | §2A auto-property → public field; `int`→`int` | OK |
| `public int Bug1Id { get; set; }` (:9) | `public int Bug1Id;` (:7) | same | OK |
| `public int Bug2Id { get; set; }` (:10) | `public int Bug2Id;` (:8) | same | OK |
| `public string Type { get; set; }` (:11) | `public String Type;` (:9) | same; `string`→`java.lang.String` (§1 string policy) | OK |
| `public int Direction { get; set; }` (:12) | `public int Direction;` (:10) | same | OK |
| `using System; using System.Collections.Generic;` (:1-2) | no imports | unused usings; §4 explicit imports only where needed | OK |

Behavior: non-nullable `int` members stay primitive, so the runtime `DbSet.materialize` reproduces EF6's "could not be set to a 'null' value" failure for a NULL `re_id/re_bug1/re_bug2/re_direction` (all `not null` in `setup.sql:400-404`); nullable `re_type` maps to `String` (null allowed). Implicit parameterless constructor present, as the runtime requires. Member names, casing, order and access identical (§2A, §6.2). No SQL, strings, exceptions or control flow in the unit.

Header `// Source: src/BugTracker.Web/Models/BugRelationShip.cs` (§6.1) correct. Trailer `// PORT STATUS: confidence=high todos=0` is the last line (§6.7); file has zero `TODO(port)` markers, so the count is exact. No markers needed (no quirk, no unknown).

## Findings

None.

## Rulebook obedience

OBEDIENT. Checked: §2A auto-property → public field; §2A partial class → one class; §2A type mapping (`int`, `string`); §2A access modifiers (S2-12, explicit `public` kept); §2A EF6 entities; §4 package/path/naming/no-wildcard imports; §5 inventory grep (no rows for this unit — none required: the file contains no per-site gap construct; the inventory rows for the map belong to the map's unit); §6.1 header; §6.2 completeness (5/5 members); §6.3 no stray markers; §6.4 scope (only the target file); §6.7 trailer. No deviations.

## Rulebook gaps

- None decisive for this unit. Observation only (no action needed for correctness here): §2A EF6 does not state that entity member **declaration order** must be preserved, although runtime `DbSet` builds its column list from `Class.getFields()` (whose order the JVM does not guarantee). The translation keeps source order anyway. Proposed rule (for the human): "Entity fields keep the source declaration order; the runtime must not depend on reflection order" — evidence `port/runtime/src/main/java/btnet/runtime/data/DbSet.java:56`.
