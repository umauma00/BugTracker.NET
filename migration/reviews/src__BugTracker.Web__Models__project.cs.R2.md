# Review R2 — src/BugTracker.Web/Models/project.cs

Target: `port/web/src/main/java/btnet/Models/Project.java` (manifest row 39).
No template applies (core layer, §0 / §4). Inventory: `grep "Models/project.cs" migration/inventory.tsv` returns no rows; the unit has no construct that needs a per-site decision.

## Method

Compared the 21 members of `project.cs:8-28` with `Project.java:6-26` one by one (name, type, order). Checked them against the §2A General conventions rows (auto-property → public field; `Nullable<T>` → boxed type; `partial class` → one class; Java-keyword names), §2A EF6 (entities), §4 (package, path, keyword collisions, imports) and §6 (header, completeness, markers, trailer). I also checked that every `t => t.X` named in `Models/Mapping/projectMap.cs` matches a public field of the translation: `comm` found no difference, so the map unit's `Property("X")` strings will resolve. Finally I checked that the runtime `DbSet` loader (`port/runtime/.../DbSet.java:94,281-306`) finds entities through `getFields()`, uses the no-arg constructor and handles primitive fields. The translation provides the implicit public no-arg constructor and public fields.

| Source | Target | Rule | OK |
|---|---|---|---|
| `namespace btnet.Models` | `package btnet.Models;` | §2A namespace, §4 | yes |
| `public partial class Project` (single part) | `public class Project` | §2A partial class | yes |
| `int Id`, `Active`, `EnableCustomDropDown1..3`, `Default` | `public int …;` | §2A auto-property, int→int | yes |
| `Nullable<int> DefaultUser`, `AutoAssignToDefaultUser`, `AutoSubscribeDefaultUser`, `EnablePOP3` | `public Integer …;` | §2A `Nullable<T>` → boxed | yes |
| `string Name`, `POP3UserName`, `POP3Password`, `POP3SourceEMail`, `CustomDropDownLabel1..3`, `CustomDropDownValue1..3`, `Description` | `public String …;` | §2A auto-property | yes |
| `Default` | `Default` (not renamed) | §2A / §4 keyword rule: Java keywords are case-sensitive, so `Default` is not `default` and gets no `_` | yes |
| member order `:8-28` | `:6-26` same order | §2A JSON declaration order (S2-34), §6.2 | yes |
| `using System; using System.Collections.Generic;` (unused) | no imports | §4 explicit imports only | yes |
| — | `// Source: src/BugTracker.Web/Models/project.cs` line 1 | §6.1 | yes |
| — | `// PORT STATUS: confidence=high todos=0` last line; 0 `TODO(port)` in file | §6.7 | yes |

## Findings

None. I found no defect that a rule section or a source line supports.

- Behavior: the members, their types, nullability (`Integer` for `Nullable<int>`, primitive `int` for non-nullable `int`, so a SQL NULL in a non-nullable column fails in the loader as EF6 did) and their order all match the C#.
- Compile level: the file references no runtime API, needs no imports, and has no keyword collision.
- Markers: none needed and none present. The source has no comments to copy and no quirks.

## Rulebook obedience

**OBEDIENT.** The implementer followed §2A (auto-property → public field, `Nullable<int>` → `Integer`, partial class → one class, names unchanged), §2A EF6 (entity = public fields, no JPA annotations), §1 (no Lombok, no JPA), §4 (package `btnet.Models`, manifest path, no wildcard or static imports, no spurious keyword suffix) and §6 (header, completeness, trailer with a correct `todos=0`). I found no deviation.

## Rulebook gaps

- Minor, no rule change needed for this unit: §2A's `T?` / `Nullable<T>` row has the survey count "(7)" and cites only `Models/BugFromEmail.cs`. Entity files such as `project.cs:11-14` add at least 4 more `Nullable<int>` sites. The mapping is still unambiguous. Proposed: refresh the count, or say the row covers every `Models/*.cs` entity property, so that a reviewer does not read the low count as limiting the rule's scope. Evidence: `project.cs:11-14`, RULEBOOK §2A line 151.
