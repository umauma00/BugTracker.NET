# Review R2 — src/BugTracker.Web/Models/priority.cs

Target: `port/web/src/main/java/btnet/Models/Priority.java` (manifest row 38).
No template: a `Models/` entity is a core unit and gets no template (RULEBOOK §0, §4).
Inventory: `grep "priority.cs" migration/inventory.tsv` finds no rows.

## Side-by-side check

| C# (priority.cs) | Java (Priority.java) | Rule | Verdict |
|---|---|---|---|
| `namespace btnet.Models` | `package btnet.Models;` (:2) | §2A namespace row, §4 | OK |
| `public partial class Priority` (:6) | `public class Priority` (:4) | §2A `partial class` → one Java class; §4 primary type / path | OK |
| `public int Id { get; set; }` (:8) | `public int Id;` (:6) | §2A auto-property → public field; §2A EF6 entities | OK |
| `public string Name { get; set; }` (:9) | `public String Name;` (:7) | same; String policy §1 | OK |
| `public int SortOrder { get; set; }` (:10) | `public int SortOrder;` (:8) | same | OK |
| `public string BackgroundColor { get; set; }` (:11) | `public String BackgroundColor;` (:9) | same | OK |
| `public string Style { get; set; }` (:12) | `public String Style;` (:10) | same | OK |
| `public int Default { get; set; }` (:13) | `public int Default;` (:11) | §2A names identical. `Default` is not a Java keyword (Java keywords are case-sensitive; only `default` is one), so no trailing `_` (§4) | OK |
| `using System; using System.Collections.Generic;` (:1-2) | no imports | §4 explicit imports only, and nothing is used | OK |
| — | `// Source: src/BugTracker.Web/Models/priority.cs` (:1) | §6.1 header [S1-41] | OK |
| — | `// PORT STATUS: confidence=high todos=0` (:13, last line) | §6.7. The file has no `TODO(port)`, so 0 is correct | OK |

Behavior: the default values match (C# `int` 0 / `string` null map to Java `int` 0 / `String` null).
The field types match the `priorities` columns in setup.sql: `pr_sort_seq` and `pr_default` are
`int not null`, and `pr_style` is a nullable `nvarchar`, so it maps to `String`. No member is missing (§6.2).
The source has no comments to copy. The field declaration order matches the source, which matters
for Jackson declaration-order serialization (§2A JSON [S2-34]). No markers are needed and none are present (§6.3).
The file contains no SQL, messages, control flow or runtime API calls, so it has no compile-level errors.

## Findings

None.

Counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

The implementer followed the rulebook. I checked these rules: §0 core layer, structure-preserving;
§2A General conventions (names, namespace, partial class, auto-property → public field,
access modifiers [S2-12]: explicit `public` kept); §2A EF6 entities; §4 output path,
package and keyword rule; §6.1 header, §6.2 completeness, §6.3 markers, §6.4 scope
(only the manifest target written), §6.5 (no inventory rows, so no `// inventory:` citation is
owed), §6.7 trailer. I found no deviation.

Verdict: OBEDIENT.

## Rulebook gaps

- **G1 (minor, procedural): units with no inventory rows.** §5 says "a site missing from the
  inventory is an inventory bug", and S1-36 exempts only element-content regions. The rulebook
  does not say outright that a unit whose only constructs are fully decided by rule rows
  (auto-properties here) needs no inventory row. An implementer or reviewer could treat the
  empty grep result as an inventory bug.
  *Proposed rule (§5):* "A unit whose every construct is decided by a §2A/§4 rule row needs no
  inventory row. An empty grep for such a unit is not an inventory bug."
  *Evidence:* `grep priority.cs migration/inventory.tsv` returns nothing. `Models/status.cs`
  (`Status.java`) is the same shape.
