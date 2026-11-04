# Review R1: src/BugTracker.Web/Models/BugSubscription.cs

Target: `port/web/src/main/java/btnet/Models/BugSubscription.java` (manifest target per §4; Models are core-layer units, so §4 places no template).

## Inputs checked
- Source `src/BugTracker.Web/Models/BugSubscription.cs:1-12`: `public partial class BugSubscription` in namespace `btnet.Models` with three `int` auto-properties `Id`, `BugId`, `UserId` (`:8-10`). There are no other members or comments, and no other `partial` part exists in `src/` (grep: the only other references are `Models/Mapping/BugSubscriptionMap.cs:6` and `Models/Context.cs:27,65`).
- Inventory: no row for `Models/BugSubscription.cs`. Rows 124-128 belong to `Models/Mapping/BugSubscriptionMap.cs` (a different unit). They reference the field names `Id`, `BugId`, `UserId` as strings, and those names match the target. Auto-properties are handled by a §2A rule, not by per-site rows, so the missing row is not an inventory bug.
- RULEBOOK §0, §2A General conventions (partial class, access modifiers [S2-12], auto-property, `int`), §2A EF6, §3.1, §4, §6.
- Runtime compatibility: `btnet.runtime.data.DbSet` uses `type.getFields()` (DbSet.java:94), so it needs public fields. It also accepts `int.class` scalars (:128), handles primitives specially (:290) and needs an implicit no-arg constructor (:281). The target satisfies all three.

## Findings

None. Line-by-line comparison:

| Source | Target | Rule | Verdict |
|---|---|---|---|
| `namespace btnet.Models` (:4) | `package btnet.Models;` (:2) | §2A Namespace, §4 | correct |
| `public partial class BugSubscription` (:6) | `public class BugSubscription` (:4) | §2A `partial class` (one Java class), §2A access modifiers | correct |
| `public int Id { get; set; }` (:8) | `public int Id;` (:6) | §2A auto-property, §2A EF6 entities | correct |
| `public int BugId { get; set; }` (:9) | `public int BugId;` (:7) | same | correct |
| `public int UserId { get; set; }` (:10) | `public int UserId;` (:8) | same | correct |
| `using System; using System.Collections.Generic;` (:1-2) | none | no Java equivalent is needed, and §4 forbids wildcard imports | correct (see gap G1) |
| n/a | `// Source: src/BugTracker.Web/Models/BugSubscription.cs` (:1) | §6.1 [S1-41] | correct |
| n/a | `// PORT STATUS: confidence=high todos=0` (:10, the last line) | §6.7: 0 `TODO(port)` markers, 0 counted | correct |

- Completeness (§6.2): all 3 members are present, with the same names, types and declaration order. Declaration order matters for the runtime's `getFields()` column order and for JSON [S2-34]. The source has no comments to copy.
- Markers (§6.3): none are needed and none are present. No `FIXED(port)` appears and no quirk was "fixed".
- Compile-level: no imports are needed, `int` is a JDK primitive, and the file uses no runtime API.
- Null/DBNull: the C# `int` is non-nullable, and so is the Java `int`. A NULL column fails in both. `bs_bug` and `bs_user` are NOT NULL in practice.

## Counts
HIGH: 0, MEDIUM: 0, LOW: 0

## Rulebook obedience

OBEDIENT. The implementer followed every rule that applies:
- §2A `partial class` → one Java class: followed.
- §2A access modifiers [S2-12] → explicit `public` kept on the type and on every member: followed.
- §2A auto-property → public field with the same name: followed. There are no initializers to carry over.
- §2A EF6 → entity auto-properties as public fields, no JPA annotations (§1 bans JPA): followed.
- §4 → path `port/web/src/main/java/btnet/Models/BugSubscription.java`, package `btnet.Models` verbatim, a single file, no extra types: followed.
- §6.1 header, §6.2 completeness, §6.3 markers, §6.4 scope (only the target was written), §6.7 trailer: followed.
- §6.5 inventory citation: not applicable, because no inventory row exists for this unit.

No deviations.

## Rulebook gaps

- G1 (very low impact): no rule says what to do with C# `using` directives that have no Java counterpart (`using System;`, `using System.Collections.Generic;`). The implementer dropped them without a comment, which matches the sibling entity `Models/BugRelationShip.java`. Two translators could still differ: one might drop them, another might leave a comment. Proposed rule for §6 or §4: "C# `using` namespace directives are dropped, with no comment; Java imports are derived only from the types actually referenced (explicit single-type imports, §4)." Evidence: `Models/BugSubscription.cs:1-2` and `Models/BugRelationShip.cs:1-2`, both translated without the directives.
