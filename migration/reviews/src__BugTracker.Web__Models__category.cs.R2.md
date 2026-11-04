# Review R2 — src/BugTracker.Web/Models/category.cs

Target: `port/web/src/main/java/btnet/Models/Category.java`
Sources read: `src/BugTracker.Web/Models/category.cs` (1-13), `src/BugTracker.Web/Models/Mapping/categoryMap.cs` (1-26, the map that consumes these member names),
RULEBOOK §0, §2A (General conventions; EF6), §4 (naming), §5, §6, §7; inventory rows for `Models/category.cs` (none) and `Models/Mapping/categoryMap.cs` (L287-292);
runtime `btnet/runtime/data/DbSet.java` (`EntityModel`, `orderedFields`) and `EntityTypeConfiguration.java` (`columnFor`). No template applies (entity, core layer).

## Findings

F1 | severity HIGH | port/web/src/main/java/btnet/Models/Category.java:9 | src/BugTracker.Web/Models/category.cs:11 | RULEBOOK §2A General conventions "Type/member names" (line 130); §4 "Members keep C# names and casing; Java-keyword collisions get a trailing `_`; nothing else is renamed" (line 1789-1790); §0 core layer "same member names"; inventory `src/BugTracker.Web/Models/Mapping/categoryMap.cs` L23 (`this.Property("Default")`) | The auto-property `Default` was renamed to `Default_`. `Default` (capital D) is **not** a Java keyword — Java keywords are case-sensitive and only lower-case `default` is reserved — so the keyword-suffix rule does not apply and the rename is forbidden ("nothing else is renamed"). Sibling entities translated under the same rule keep it unchanged (`Priority.java:11`, `Status.java:10`, `Project.java:25`: `public int Default;`). Behavioral consequence: per the inventory row, `CategoryMap` registers `this.Property("Default").HasColumnName("ct_default")`; runtime `DbSet.EntityModel` maps fields by reflective name (`orderedFields` → `byName.remove("Default")` finds nothing; `config.columnFor("Default_")` falls back to the field name), so every `context.Categories` query (`categories.aspx.cs:25`, `Controls/BugList.ascx.cs:92`, inventory rows 608/609 and 49/50) emits `select ..., Default_ from categories` and fails with SQL Server "Invalid column name 'Default_'" → 500, where EF6 reads `ct_default`. Also any translated reader of `category.Default` would not compile. | `public int Default;`

## Rulebook obedience

DEVIATIONS: 1.
- §2A "Type/member names" / §4 naming (lines 130, 1789-1790): keyword suffix applied to a non-keyword member name (`Default` → `Default_`). This is F1.

Followed correctly:
- §6.1 header `// Source: src/BugTracker.Web/Models/category.cs` (line 1) — correct repo-relative path.
- §2A namespace → package `btnet.Models` verbatim.
- §2A `partial class` → one Java class; access `public` kept (§2A Access modifiers, S2-12).
- §2A auto-property → public field with same name and type (`int` → `int`, `string` → `String`) for `Id`, `Name`, `SortOrder`; no initializers in source, none added.
- §6.2 completeness: all four members present (one misnamed, F1); no source comments to copy. Unused `using System; using System.Collections.Generic;` correctly produce no imports (§4 explicit imports only).
- §6.3/§6.7 no markers needed; trailer `// PORT STATUS: confidence=high todos=0` is the last line and the count (0) is correct. (Confidence "high" is unwarranted given F1, but that is not itself a rule violation.)
- §6.5: no inventory row exists for this source path, so no `// inventory:` citation is required; no decision here needed one.

## Rulebook gaps

G1 — The keyword-collision rule (§2A line 130, §4 line 1789) does not say explicitly that the test is case-sensitive, nor list the Java keyword set, and its only example (`default` → `default_`) sits next to a model property literally named `Default`, which invites this misreading. Proposed amendment: "The collision test is exact and case-sensitive against the Java 21 reserved keywords and literals (`true`, `false`, `null`) plus contextual `_`; `Default`, `Class`, `Type`, `Interface`, `Long`, `String` etc. are **not** keywords and keep their name." Evidence: `Models/category.cs:11`, `priority.cs`, `status.cs`, `project.cs` all declare `Default`; this unit renamed it, the siblings did not — two implementers answered the same question differently (meta-rule).

G2 — Neither §2A EF6 nor the runtime fails fast when a map's `Property("X")` names a field that does not exist on the entity (`DbSet.orderedFields` silently skips it, `columnFor` falls back to the Java field name), so an entity/map name mismatch surfaces only as a runtime SQL "Invalid column name" error. Proposed (runtime-side, for the human): `EntityModel` should throw `InvalidOperationException` when a configured property has no matching field, so the mismatch is caught at first `Context` use. Evidence: F1 consequence; `DbSet.java:101-105`, `EntityTypeConfiguration.java:154-157`.
