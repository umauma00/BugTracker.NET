# Review R1 — src/BugTracker.Web/Models/category.cs

Target: `port/web/src/main/java/btnet/Models/Category.java`
Reviewer: R1 (adversarial, read-only)
Inputs read: RULEBOOK.md (whole, lines 1–1885), inventory rows for
`Models/category.cs` (none; related rows: `Models/Mapping/categoryMap.cs`
L11–L23, `categories.aspx.cs` L25, `Controls/BugList.ascx.cs` L92), source,
target, runtime `btnet/runtime/data/DbSet.java` and
`EntityTypeConfiguration.java` (to check how field names bind to mapped
columns), sibling entities `Priority.java`, `Status.java`, `Project.java`.

No templates belong to this unit (core layer, §0; §4 places templates only
for pages, masters, `.ascx` and `inc_diff.inc`).

## Findings

F1 | severity HIGH | port/web/src/main/java/btnet/Models/Category.java:9 | src/BugTracker.Web/Models/category.cs:11 | RULEBOOK §2A General conventions "Type/member names" + §4 "Members keep C# names and casing; Java-keyword collisions get a trailing `_`; nothing else is renamed"; inventory `src/BugTracker.Web/Models/Mapping/categoryMap.cs L23` | The auto-property `Default` became the field `Default_`. `Default` is **not** a Java keyword: Java keywords are case-sensitive and only lowercase `default` is reserved, so the `_` suffix does not apply here and the rename is forbidden. The rename also breaks behavior. Inventory row categoryMap.cs L23 fixes the map translation as `this.Property("Default").HasColumnName("ct_default")`. The runtime binds that string to the field by exact name (`DbSet.EntityModel.orderedFields`: `byName.remove(p)` finds no field `Default` and silently drops it). It then maps the leftover field `Default_` through `config.columnFor("Default_")`, whose fallback is the property name itself (`EntityTypeConfiguration.java:81`). So every `context.Categories` query selects a non-existent column `Default_` from `categories`. The result is a `SqlException` "Invalid column name 'Default_'" and a 500 on `categories.aspx` (`categories.aspx.cs:25`) and in `BugList.ascx` `GetFilterValues` (`BugList.ascx.cs:92`). EF6 selects `ct_default` and succeeds. Jackson (PUBLIC_ONLY, names as declared, §2A JSON) would also emit `Default_` instead of `Default` if the entity were ever serialized. The sibling entities are consistent with the correct reading: `Priority.java:11`, `Status.java:10` and `Project.java:25` all declare `public int Default;` | `public int Default;` |

Counts: HIGH 1, MEDIUM 0, LOW 0.

Checked and found correct:
- The header `// Source: src/BugTracker.Web/Models/category.cs` (§6.1, S1-41).
- Package `btnet.Models` (§2A namespace rule, §4).
- `partial class` → one plain `public class` (§2A).
- Auto-properties → public fields with the same names for `Id`, `Name` and `SortOrder`, with types `int`/`String`/`int` (§2A auto-property row, §2A EF6 "Entities").
- The `using` directives produce no imports, and no runtime API is used (§3.1 n/a).
- Completeness: all 4 members are present (§6.2).
- No markers are needed, and the trailer `// PORT STATUS: confidence=high todos=0` is the last line with the correct count (§6.7).

## Rulebook obedience

DEVIATIONS: 1.

1. §2A General conventions "Type/member names" / §4 "nothing else is renamed": the non-keyword member `Default` was renamed to `Default_` (F1). The implementer appears to have applied the keyword-suffix rule case-insensitively. The rulebook's own example (`default` → `default_`) is lowercase, and §2B B1 applies the suffix only to the lowercase file stem `default.aspx`.

No other deviation: §0 core posture, §2A auto-property, EF6 entity, access-modifier (S2-12: explicit `public` kept), §4 path/package, §6.1/6.2/6.3/6.7 are all followed.

## Rulebook gaps

- G1 (proposed clarification, §2A "Type/member names" and §4): state that the keyword test is **case-sensitive Java keyword matching**, so `Default`, `Class`, `New` and similar names are kept unchanged and only exact lowercase Java keywords or reserved literals (`default`, `new`, `class`, `true`, …) get `_`. Add the example `Models/category.cs:11` `Default` → `Default` (not `Default_`). Evidence: this unit went wrong on exactly this point, while `Priority.java`, `Status.java` and `Project.java` kept `Default`, so two agents did answer the question differently (meta-rule).
- G2 (proposed, §3 `DbSet`/`EntityTypeConfiguration`, runtime-owned): a `Property("<name>")` that names no public field of the entity is silently ignored, and the orphaned field falls back to a column named after itself. EF6 would reject a lambda over a non-existent member at compile time. Proposal: the runtime throws `InvalidOperationException("The property '<name>' is not a declared property on type '<T>'.")` when a configured property has no field, matching the existing key-component check in `DbSet.EntityModel`. Then a mismatch like F1 fails at model build with a clear message instead of as a SQL "Invalid column name" error. Do not edit the runtime from a loop; queue it for the runtime author.
