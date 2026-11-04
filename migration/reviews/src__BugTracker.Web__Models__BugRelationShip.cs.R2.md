# Review R2 — src/BugTracker.Web/Models/BugRelationShip.cs

Target: `port/web/src/main/java/btnet/Models/BugRelationShip.java` (no template: §4 places templates only for pages/masters/.ascx/.inc).
Inputs read: RULEBOOK §2A (General conventions, EF6 entities), §2A Web API JSON config (L689-692), §4, §5, §6; inventory (`grep BugRelationShip` → rows only for `Models/Mapping/BugRelationShipMap.cs`, L117-123; none for this unit); source; target; consumers `Models/Mapping/BugRelationShipMap.cs`, `Models/Context.cs:26,64`.

## Findings

None. Each source line was checked against the target:

| Source | Target | Rule | Verdict |
|---|---|---|---|
| `BugRelationShip.cs:1-2` `using System; using System.Collections.Generic;` | no imports (none needed; `String`, `int` are `java.lang`/primitive) | §4 Imports (explicit single-type, only what is used) | ok |
| `:4` `namespace btnet.Models` | `:2` `package btnet.Models;` | §2A Namespace, §4 | ok |
| `:6` `public partial class BugRelationShip` | `:4` `public class BugRelationShip` | §2A `partial class` → one Java class (single part; no other part exists in `src/`) | ok |
| `:8` `public int Id { get; set; }` | `:6` `public int Id;` | §2A Auto-property → public field, same name; EF6 entities | ok |
| `:9` `public int Bug1Id { get; set; }` | `:7` `public int Bug1Id;` | same | ok |
| `:10` `public int Bug2Id { get; set; }` | `:8` `public int Bug2Id;` | same | ok |
| `:11` `public string Type { get; set; }` | `:9` `public String Type;` | same; `Type` is not a Java keyword (§2A names) | ok |
| `:12` `public int Direction { get; set; }` | `:10` `public int Direction;` | same | ok |

- Member order equals declaration order (matters for Jackson declaration-order output, §2A JsonConfig L689-692, and the map's `Property("…")` strings, inventory L117-123). Field names match the map's string keys exactly (`Id`, `Bug1Id`, `Bug2Id`, `Type`, `Direction`).
- Types: C# `int` (non-nullable) → `int`; `string` → `String` (nullable reference, default `null`, same as C#). No `T?` present, so no boxing required (§2A Nullable row not triggered).
- Implicit public no-arg constructor preserved (C# had none declared) — usable by runtime `DbSet<T>` materialization.
- §6.1 header `// Source: src/BugTracker.Web/Models/BugRelationShip.cs` present on line 1, repo-relative path correct.
- §6.2 completeness: 5/5 properties, 1/1 type. No source comments to copy.
- §6.3/§6.7 markers: zero `TODO(port)`/`BUG(port)` markers; trailer `// PORT STATUS: confidence=high todos=0` is the last line; count correct.
- §6.5: no inventory row for this unit, so no `// inventory:` citation is required. No per-site decision exists here, so the absence of rows is not an inventory bug (§5).
- No sanctioned fix (§0.1 F1–F5) applies; no quirk altered.

Counts: HIGH 0 / MEDIUM 0 / LOW 0.

## Rulebook obedience

OBEDIENT. Every applicable rule (§2A namespace, partial class, auto-property, member names; §2A EF6 entities; §4 path/package/imports; §6.1 header, §6.2 completeness, §6.3 markers, §6.4 scope, §6.5 inventory grep, §6.7 trailer) was followed. No deviations found. Path matches the §4 naming rule (`port/web/src/main/java/btnet/Models/BugRelationShip.java`).

## Rulebook gaps

1. **Unused C# `using` directives.** The rulebook (§4 Imports) says how to write Java imports but does not say explicitly that C# `using` directives with no used type are dropped rather than mapped (e.g. `using System.Collections.Generic;` → `import java.util.*` or similar). The implementer dropped them, which is the sensible reading. Proposed rule for §4: "C# `using` directives are not translated; imports are derived from the types the Java file actually references." Evidence: `Models/BugRelationShip.cs:1-2` (and the same pattern in every `Models/*.cs` entity).
2. **Single-part `partial class`.** §2A's `partial class` row and §4's `partial` line describe merging multiple parts; they do not state the single-part case (`partial` keyword on a class with only one part, as in all EF-generated entities). Proposed addition: "`partial` with a single part → plain class, modifier dropped." Evidence: `Models/BugRelationShip.cs:6`.
