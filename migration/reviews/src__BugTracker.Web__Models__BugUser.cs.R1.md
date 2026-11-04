# Review R1: src/BugTracker.Web/Models/BugUser.cs

Target: `port/web/src/main/java/btnet/Models/BugUser.java` (18 lines). The rulebook puts no template in §4 for a core `Models/` unit, so this file is the only target.

Read for this review: RULEBOOK.md (whole file, focusing on §0, §2A General conventions / Nullable / type mapping / EF6, §3.1, §4, §5, §6); inventory rows for `Models/BugUser.cs` (none) and `Models/Mapping/BugUserMap.cs` (L11–L38, 14 rows); the source; `Models/Mapping/BugUserMap.cs`; `Models/Context.cs:29`; runtime `btnet/runtime/data/DbSet.java` (EntityModel field discovery); sibling `Models/BugFromEmail.java`, to compare conventions.

## Side-by-side check

| Source (BugUser.cs) | Target (BugUser.java) | Rule | Verdict |
|---|---|---|---|
| `namespace btnet.Models` (:4) | `package btnet.Models;` (:2) | §2A namespace row, §4 | ok |
| `public partial class BugUser` (:6) | `public class BugUser` (:6) | §2A `partial class` → one Java class; §4 primary type = file stem | ok |
| `public int Id { get; set; }` (:8) | `public int Id;` (:8) | §2A auto-property → public field; `int`→`int` | ok |
| `BugId`, `UserId`, `Flag` `int` (:9-11) | `public int` (:9-11) | same | ok |
| `Nullable<DateTime> FlagDate` (:12) | `public LocalDateTime FlagDate;` (:12) | §2A `T?`/`Nullable<T>` → boxed type, example `LocalDateTime`; `DateTime`→`java.time.LocalDateTime` | ok |
| `int Seen` (:13), `Nullable<DateTime> SeenDate` (:14) | :13, :14 | same | ok |
| `int Vote` (:15), `Nullable<DateTime> VoteDate` (:16) | :15, :16 | same | ok |
| `using System; using System.Collections.Generic;` (:1-2) | `import java.time.LocalDateTime;` (:4) | §4 explicit single-type imports; the C# usings are unused | ok |
| — | `// Source: src/BugTracker.Web/Models/BugUser.cs` (:1) | §6.1 header, repo-relative [S1-41] | ok |
| — | `// PORT STATUS: confidence=high todos=0` (:18, last line) | §6.7; the file has 0 `TODO(port)` markers, so `todos=0` is right | ok |

- Every member is present (9 auto-properties, 9 fields), with the same names, casing, order and access (§6.2, §2A names). C# has an implicit public parameterless constructor, and so does the Java class. No members are missing.
- No initializers in the source, so none in the target. The Java defaults (0 / null) match the CLR defaults (0 / null for `Nullable<DateTime>`).
- Runtime compatibility: `DbSet.EntityModel` discovers mapped properties through `type.getFields()` (public, non-static, scalar types including `int` and `LocalDateTime`). The public fields therefore satisfy every `Property("…")` name that the inventory rows for `BugUserMap.cs` L11–L38 require (`Id`, `BugId`, `UserId`, `Flag`, `FlagDate`, `Seen`, `SeenDate`, `Vote`, `VoteDate`). The fail-fast check at `DbSet.java:63-71` will not trip on this entity.
- Jackson (§2A JSON, `JsonConfig` PUBLIC_ONLY, declaration order): the public fields serialize under the same PascalCase names, in the same order, as Newtonsoft's auto-properties.
- No banned constructs and no Lombok (§1). No markers are needed: the rulebook decides every construct, so the UNKNOWN rule never applies.
- Compile-level: `java.time.LocalDateTime` is imported; there are no runtime calls; the package path matches the directory. Nothing is wrong by reading.

## Findings

None.

## Rulebook obedience

**OBEDIENT.** I checked these rules and found no deviation from any of them: §2A General conventions (names unchanged, namespace→package, partial→one class, auto-property→public field, `Nullable<T>`→boxed/`LocalDateTime`, primitive type mapping), §2A EF6 (entities: auto-properties → public fields; no navigation properties in this entity), §4 (target path from the manifest, explicit imports, member names), §6.1 header, §6.2 completeness, §6.3 markers (none), §6.4 scope (only the target was written), §6.7 trailer with an accurate `todos=0`.

§6.5 says to cite `// inventory:` only where an inventory row drove a decision. There is no inventory row for `Models/BugUser.cs`, so the absence of citations is correct.

## Rulebook gaps

1. **`Nullable<DateTime>` long form is uncounted and has no inventory rows.** The §2A row reads "`T?` / `Nullable<T>` (7)". The 7 counts only the `int?` sites of `Models/BugFromEmail.cs` (inventory rows 76–82, `NULLABLE_REQUEST_FIELD`). The spelled-out form `Nullable<DateTime>` occurs in at least 10 Models files (`BugPost.cs`, `BugTask.cs`, `BugUser.cs`, `CustomColumnsMetaData.cs`, `EmailedLink.cs`, `Votes.cs`, `bug.cs`, `project.cs`, `query.cs`, `user.cs`), and none of them has an inventory row. §5 lists "which nullable columns" as an inventory decision. The rulebook row already decides these sites unambiguously, so the translation is not affected.
   - Proposed amendment: change the count to cover both spellings, and add one sentence: "`Nullable<DateTime>` → `LocalDateTime` (already a reference type; `null` = DB NULL); no inventory row needed for entity fields."
   - Evidence: `grep -l "Nullable<" src/BugTracker.Web/Models/*.cs`; `grep "Models/BugUser.cs" migration/inventory.tsv` returns nothing.
2. **Field-order note (runtime, not this unit).** EF6 builds the SELECT column list in CLR declaration order. The runtime instead uses configured-property order, then name order, and carries its own `TODO(port)` (`DbSet.java:96-101`). This has no translator-facing consequence for this entity. It is recorded only so the human knows the entity's declaration order is preserved but not used.
