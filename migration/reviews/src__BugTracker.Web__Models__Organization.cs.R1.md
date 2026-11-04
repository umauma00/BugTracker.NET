# Review R1 — src/BugTracker.Web/Models/Organization.cs

Target: `port/web/src/main/java/btnet/Models/Organization.java`. The path follows the RULEBOOK §4 `.cs` rule: module `port/web`, namespace `btnet.Models`, primary type `Organization`.
No templates. This is an EF6 entity, not a page, control or master, so §4's template paths and the judge contract do not apply.

## Inputs read
- Source `src/BugTracker.Web/Models/Organization.cs:1-37`
- Target `port/web/src/main/java/btnet/Models/Organization.java:1-35`
- RULEBOOK §0, §1, §2A General conventions (:128-175), §2A EF6 (:554-590), §3 `DbContext` (:1569), §3.1, §4, §5, §6, §7
- inventory.tsv: **no rows** for `Models/Organization.cs`. Rows 198-202+ (`Models/Mapping/OrganizationMap.cs`) refer to the fields `Id`, `Name` and `Domain` by name. `OrganizationMap.cs:11-51` names all 28 properties, and all 28 exist in the target with the same spelling and casing, including the source typo `CategoryFieldPErmissionLevel`.
- Usage in source: `Context.cs:35` `DbSet<Organization> Organizations`; `Controls/BugList.ascx.cs:79` `context.Organizations.OrderBy(p => p.Name)` (reads `Name`); `orgs.aspx.cs:16` `IEnumerable<Organization>`; `Models/bug.cs:23` is a navigation property.
- Runtime `DbSet` (port/runtime): it materializes through `getDeclaredConstructor().newInstance()` and `Class.getFields()`. The target has an implicit public no-arg constructor and public fields, so it fits.
- `setup.sql` `orgs` table: every `int` column is `not null`, and `og_domain` is the only nullable column (a `String`). There is no DBNull-to-primitive mismatch with today's schema.

## Line-by-line check
| Source | Target | Verdict |
|---|---|---|
| `:1-2` `using System; using System.Collections.Generic;` | none | correct. Nothing needs an import (§4 explicit single-type imports) |
| `:4` `namespace btnet.Models` | `:2` `package btnet.Models;` | correct (§2A namespace row, §4) |
| `:6` `public partial class Organization` | `:4` `public class Organization` | correct (§2A `partial class` row). No other partial part exists in `src/` |
| `:8` `public int Id { get; set; }` | `:6` `public int Id;` | correct (§2A auto-property :137, types :163) |
| `:9-10` `string Name`, `string Domain` | `:7-8` `public String …;` | correct |
| `:11-35` 25 × `public int X { get; set; }` | `:9-33` 25 × `public int X;` | correct. Same order, same names, all `int` |
| header | `:1` `// Source: src/BugTracker.Web/Models/Organization.cs` | correct (§6.1, repo-relative path [S1-41]) |
| trailer | `:35` `// PORT STATUS: confidence=high todos=0` | correct (§6.7). The file has 0 `TODO(port)` markers |

Completeness (§6.2): all 28 members of the source are present. The source has no comments, constructors, methods or constants.

## Findings

None. 0 HIGH, 0 MEDIUM, 0 LOW.

Checks that found no defect:
- Default values: C# `int` defaults to 0 and Java `int` defaults to 0. C# `string` defaults to `null` and Java `String` defaults to `null`. They match.
- Thread-safety and ordering do not apply: the class is a plain data holder.
- No SQL, output text or exception paths exist in this unit.
- Compile level: there are no unresolved types and no runtime API calls.

## Rulebook obedience

**OBEDIENT.** I found no deviations. Rules I checked:
- §2A namespace → package
- §2A `partial class` → one class
- §2A auto-property → public field with the same name and no initializer
- §2A types (`int` → `int`, `string` → `String`)
- §2A EF6 entity rule
- §4 path, naming and imports
- §6.1 header
- §6.2 completeness
- §6.3 no stray markers
- §6.4 scope (only the manifest target was written)
- §6.5 citations: no inventory row exists, so no `// inventory:` citation is required
- §6.7 trailer

## Rulebook gaps

None new for this unit. The §5 statement "A site missing from the inventory is an inventory bug" does not apply here. The unit has no construct that needs a per-site decision, and §2A decides every member.
