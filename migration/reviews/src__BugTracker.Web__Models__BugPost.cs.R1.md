# Review R1 — src/BugTracker.Web/Models/BugPost.cs

Target: `port/web/src/main/java/btnet/Models/BugPost.java` (manifest.tsv row 32; path matches RULEBOOK §4 `.cs` rule: module `port/web`, namespace `btnet.Models`, primary type `BugPost`).
No templates: entity class, not a page/control/master, so the judge contract does not apply (§4 templates are for pages, masters and `.ascx` only).

## Inputs read
- Source `src/BugTracker.Web/Models/BugPost.cs:1-25`
- Target `port/web/src/main/java/btnet/Models/BugPost.java:1-25`
- RULEBOOK §2A General conventions (namespace :130, `partial class` :133, auto-property :137, `Nullable<T>` :151, primitive/BCL type table :163), §2A EF6 entities (:556-559), §2A JSON/Jackson (:688-694), §4, §5, §6
- inventory.tsv: **no rows** for `Models/BugPost.cs`. Rows 93-116 belong to `Models/Mapping/BugPostMap.cs` and reference fields by name: `Id`, `Type`, `Comment`, `FromEmail`, `ToEmail`, `File`, `ContentType`, `CCEmail`, `BugId`, `UserId`, `Date`, `CommentSearch`, `Size`, `ParentId`, `OriginalCommentId`, `HideFromExternalUsers`. All 16 exist in the target with exactly those names and that casing.
- Usage in source: `BugPostMap.cs:6` (`EntityTypeConfiguration<BugPost>`) and `Context.cs:25` (`DbSet<BugPost> BugPosts`). No `.BugPosts` query and no `new BugPost` anywhere in `src/`. The entity is never materialized, constructed or serialized by any call site.

## Line-by-line check
| Source | Target | Verdict |
|---|---|---|
| `:1-2` `using System; using System.Collections.Generic;` | none | correct: neither is needed. `Nullable<T>` maps to a boxed type, and no collection is declared. §4 asks for explicit single-type imports |
| `:4` `namespace btnet.Models` | `:2` `package btnet.Models;` | correct (§2A namespace row, §4) |
| `:6` `public partial class BugPost` | `:6` `public class BugPost` | correct (§2A `partial class` row: one Java class) |
| `:8` `public int Id` | `:8` `public int Id;` | correct (§2A :137, :163) |
| `:9` `public int BugId` | `:9` | correct |
| `:10` `public string Type` | `:10` `public String Type;` | correct |
| `:11` `public int UserId` | `:11` | correct |
| `:12` `public DateTime Date` | `:12` `public LocalDateTime Date;` + `:4` `import java.time.LocalDateTime;` | correct per §2A :163 / :232. Default value differs (see Rulebook gaps G1). No call site observes it, so this is not a finding |
| `:13` `string Comment` | `:13` | correct |
| `:14` `string CommentSearch` | `:14` | correct |
| `:15` `string FromEmail` | `:15` | correct |
| `:16` `string ToEmail` | `:16` | correct |
| `:17` `string File` | `:17` `public String File;` | correct. `java.io.File` is not imported, so there is no clash. A field name may equal a type name |
| `:18` `Nullable<int> Size` | `:18` `public Integer Size;` | correct (§2A :151) |
| `:19` `string ContentType` | `:19` | correct |
| `:20` `Nullable<int> ParentId` | `:20` `public Integer ParentId;` | correct (§2A :151) |
| `:21` `Nullable<int> OriginalCommentId` | `:21` `public Integer OriginalCommentId;` | correct (§2A :151) |
| `:22` `int HideFromExternalUsers` | `:22` `public int HideFromExternalUsers;` | correct (it stays `int`, not `boolean`, as in the source) |
| `:23` `string CCEmail` | `:23` `public String CCEmail;` | correct (casing kept, §4) |
| — | `:1` `// Source: src/BugTracker.Web/Models/BugPost.cs` | correct (§6.1, repo-relative path) |
| — | `:25` `// PORT STATUS: confidence=high todos=0` | correct: it is the last line, and the file has 0 `TODO(port)` markers (§6.7) |

Other checks:
- **Member order:** same as the source. This matters because Jackson emits fields in declaration order (§2A :690-692).
- **Names and casing:** unchanged (§4). No Java keyword collisions (`Type`, `File` and `Date` are not keywords).
- **Completeness (§6.2):** 16 of 16 properties are present. There are no methods, constructors or comments to copy.
- **Constructor:** the implicit public no-arg constructor is kept.
- **Runtime API:** none used, so there is nothing to check against §3.1.
- **Compile-level:** all types are resolvable (`int`, `String`, `Integer`, and `LocalDateTime` imported). No syntax errors are visible.
- **Out of scope for this unit:** SQL, output text, redirects, exceptions, thread-safety and formatting.

## Findings

None. (0 HIGH, 0 MEDIUM, 0 LOW)

## Rulebook obedience

**OBEDIENT.** I checked each of these rules and found the target follows it:
- §2A namespace, `partial class`, auto-property, `Nullable<T>` and type-table rows
- §2A EF6 entity rule (auto-properties become public fields, no JPA annotations)
- §4: output path, package, member names, explicit imports, one file per unit with no extra top-level types
- §6.1 header, §6.2 completeness, §6.3 markers (none needed), §6.4 scope (only the manifest target exists, and no template is required), §6.7 trailer

§6.5 needs no `// inventory:` citations, because no inventory row has `file` = `Models/BugPost.cs`.

The one item I cannot verify from disk: §5 says "a site missing from the inventory is an inventory bug: flag it in the report". The three `Nullable<int>` sites (`:18, :20, :21`) have no inventory rows (see G2). The rule row decides them fully, and rule rows win over inventory rows (§5), so the translation is right. Whether the implementer flagged the missing rows in its final message is not recorded on disk. That is not a defect in the target file, so I list no deviation for it.

## Rulebook gaps

- **G1. Default value of a non-nullable `DateTime` entity field.** C# `public DateTime Date { get; set; }` defaults to `DateTime.MinValue` (0001-01-01T00:00:00). The §2A :163 / :232 mapping (`LocalDateTime`, a reference type) defaults to `null`. Today nothing observes the difference: `BugPost` is never constructed, queried or serialized (`grep -rn "\.BugPosts\|new BugPost" src` finds nothing). It would surface if a future site serialized a fresh entity (Newtonsoft writes `"0001-01-01T00:00:00"`, Jackson writes `null`) or if `DbSet` materialization wrote a SQL NULL into it (EF6 throws `InvalidOperationException`, the port silently stores `null`). The same gap applies to every non-nullable `DateTime` auto-property in `Models/*.cs`, `bug.cs` among them.
  - Proposed rule: "A non-nullable `DateTime` field or auto-property with no initializer gets the initializer `= LocalDateTime.MIN_DOTNET` (runtime constant `DateTimes.MinValue` = `LocalDateTime.of(1, 1, 1, 0, 0)`). Alternatively, the rule records that the default is accepted as `null` because no site reads an unassigned value."
  - Evidence: `Models/BugPost.cs:12`, RULEBOOK §2A :163 (silent on defaults).
- **G2. The `Nullable<T>` count and inventory coverage are stale.** §2A :151 says `T?` / `Nullable<T>` has 7 sites and cites only `Models/BugFromEmail.cs:11-30`, and the inventory's NULLABLE rows for `Models/` cover only `BugFromEmail.cs`. `grep -n "Nullable<int>" src/BugTracker.Web/Models/*.cs` finds 26 more sites, including `BugPost.cs:18,20,21`, `BugTask.cs`, `bug.cs`, `project.cs`, `query.cs`, `user.cs`, `EmailedLink.cs`, `Votes.cs` and `CustomColumnsMetaData.cs`. §5 lists "which nullable columns" as an inventory decision, so each of these is formally an inventory bug that every entity implementer would have to flag.
  - Proposed: correct the count, and add a sentence to §2A :151 or §5: "Entity declarations `Nullable<T>` in `Models/*.cs` are decided by this row alone and need no inventory row. Inventory rows are required only at *use* sites (lifted comparisons, `??`, `.Value`), as `bugs.aspx.cs` L61 shows."
- **G3. Single-file `partial` and unused `using` directives.** These are minor and were raised independently on BugPostAttachment R2. The `partial class` row (:133) is written for multi-part partials. A one-line note that a single-part `partial` simply drops the modifier would close it. Likewise, nothing states that `using` directives are not translated and that imports come from referenced types only.
  - Evidence: `Models/BugPost.cs:1-2,6`.
