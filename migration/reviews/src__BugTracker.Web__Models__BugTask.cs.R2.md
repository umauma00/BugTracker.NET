# Review R2 — src/BugTracker.Web/Models/BugTask.cs (+ src/BugTracker.Web/Models/bug.cs)

Targets: `port/web/src/main/java/btnet/Models/BugTask.java`, `port/web/src/main/java/btnet/Models/Bug.java`
(manifest rows 42-43). EF6 entities, core layer, no template (§4: entities are not pages/controls).

Checked: RULEBOOK §0, §1, §2A (General conventions, EF6, Dates/numbers), §3/§3.1, §4, §5, §6;
inventory (`grep` of both source paths: **no rows**); the source files; the translations;
the consumers that fix the member contract (`Models/Mapping/BugTaskMap.cs:11-39`,
`Models/Mapping/bugMap.cs:53-90`, `Models/Context.cs:28,30`); and the runtime that
materializes these classes (`port/runtime/.../data/DbSet.java` `EntityModel`, `isScalar`, `materialize`).

## Findings

None.

Member-by-member verification (adversarial, every source line):

| Source | Target | Rule | Result |
|---|---|---|---|
| `BugTask.cs:6` `public partial class BugTask` | `BugTask.java:7` `public class BugTask` | §2A partial (one class), §4 package `btnet.Models` | OK |
| `BugTask.cs:8,10,13,17` `int` auto-props | `public int` fields `:9,11,14,18` | §2A auto-property → public field; `int` → `int` | OK |
| `BugTask.cs:16,20` `DateTime` | `LocalDateTime` `:17,21` | §2A type table | OK |
| `BugTask.cs:21,31,32,35` `Nullable<int>` | `Integer` `:22,32,33,36` | §2A `Nullable<T>` → boxed | OK (DBNull → null in `DbSet.materialize`; primitive `int` for non-nullable reproduces EF's "could not be set to a 'null' value") |
| `BugTask.cs:24-27` `Nullable<DateTime>` | `LocalDateTime` `:25-28` | §2A | OK |
| `BugTask.cs:28-29` `Nullable<decimal>` | `BigDecimal` `:29-30` | §2A (`decimal` → `java.math.BigDecimal`) | OK, import present `:4` |
| `BugTask.cs:30,36` `string` | `String` `:31,37` | §2A | OK |
| `BugTask.cs:11,14,18,22,33` `virtual` navigation props | public fields `Bug`, `CreatedUser`, `LastUpdatedUser`, `AssignedToUser`, `Status` | §2A EF6 "navigation properties → public fields, never loaded" | OK; non-scalar so `DbSet.isScalar` skips them |
| `bug.cs:6-42` all scalar props | `Bug.java:48-82` | same rows | OK, types and nullability match 1:1 (`AssignedToUserId`, `LastUpdatedUserId`, `UserDefinedAttributeId` `Integer`; `LastUpdatedDate` `LocalDateTime`) |
| `bug.cs:44` `virtual IList<BugTask> Tasks` | `public List<BugTask> Tasks;` `:84` | §2A [C1] `IList<T>` → `java.util.List<T>`; EF6 navigation never loaded | OK (no initializer in source, none in target) |
| member names / order | identical, declaration order kept | §2A names, §6.2 completeness | OK |
| Map property names (`BugTaskMap.cs:22-39`, `bugMap.cs:74-90`) | all exist as public fields with exact case | needed by `EntityModel` fail-fast | OK — no `InvalidOperationException` at model build |
| Headers | `// Source: src/BugTracker.Web/Models/BugTask.cs`, `// Source: src/BugTracker.Web/Models/bug.cs` | §6.1 [S1-41] | OK (source path case `bug.cs` preserved) |
| Trailers | `// PORT STATUS: confidence=high todos=0` as last line of each file | §6.7; 0 `TODO(port)` markers present | OK, count exact |
| Imports | `java.math.BigDecimal`, `java.time.LocalDateTime`, `java.util.List` — single-type, all used, no static | §4 | OK |
| Referenced types `User`, `Status`, `Priority`, `Organization`, `Category`, `Project` | same package `btnet.Models` (files exist in `port/web/src/main/java/btnet/Models/`) | C# resolves `User` to `btnet.Models.User` (nearer namespace) over `btnet.User`; Java same-package resolution matches | OK |
| Compile-level | field names equal to type names (`public Bug Bug;`, `public Status Status;`, `public Priority Priority;` …) | — | legal Java (declarations only; no qualified expression uses the obscured names) |
| Markers | none needed (no quirks, no unknowns) | §2A BUG/UNKNOWN rules | OK |

## Rulebook obedience

**OBEDIENT.** No deviation found: §2A auto-property/`Nullable`/type table/[C1]/EF6
navigation rows, §4 path, package, file name (`bug.cs` → `Bug.java` per the
case-insensitive primary-type rule) and import rules, §6.1 header, §6.2
completeness and §6.7 trailer were all followed. The implementer did not "fix"
anything, added no members, initializers, constructors, getters/setters or
annotations, and wrote nothing outside the manifest targets. The absence of
`// inventory:` citations is correct because no inventory row exists for either file (§6.5).

## Rulebook gaps

1. **`virtual` on entity navigation properties is not mentioned.** §2A EF6 says
   navigation properties become public fields but does not say what `virtual`
   becomes (it has no meaning on a Java field). Implementer correctly dropped it.
   Proposed rule (EF6 row): "`virtual` on an auto-property is dropped (fields are
   not overridable; lazy-loading proxies are not used)." Evidence:
   `Models/BugTask.cs:11,14,18,22,33`, `Models/bug.cs:12-35,44`.
2. **Survey count of `Nullable<T>` is stale.** §2A row "`T?` / `Nullable<T>` (7)"
   counts only the `int?` sites of `Models/BugFromEmail.cs`; `src/BugTracker.Web/Models/*.cs`
   alone has 37 `Nullable<…>` declarations (13 in this unit). The mapping is
   unambiguous, so no translation risk; proposed: correct the count or state that
   it covers both spellings. Evidence: `grep -c Nullable src/BugTracker.Web/Models/*.cs`.
3. **Entity units have no inventory rows** (both files grep empty). Not an
   inventory bug under §5 as written, since every construct is decided by a §2A
   rule, but §5 could say so explicitly for pure-entity files ("a unit whose every
   construct is covered by a §2A rule needs no rows") to stop reviewers flagging it.
4. (Runtime-owned, not this unit) `DbSet.EntityModel.orderedFields` orders the
   unconfigured fields by name rather than CLR declaration order (its own
   `TODO(port)`); this only affects which strict-materialization error surfaces
   first. Recorded for completeness; no unit-level action.
