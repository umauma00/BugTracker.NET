# Review R1: src/BugTracker.Web/Models/BugTask.cs (+ src/BugTracker.Web/Models/bug.cs)

Targets:
- `port/web/src/main/java/btnet/Models/BugTask.java` (39 lines), manifest row 42
- `port/web/src/main/java/btnet/Models/Bug.java` (47 lines), manifest row 43

Templates per RULEBOOK §4: none (core-layer `Models/` entities, §0; no page, master or `.ascx`).
Inventory rows for these source paths: none (`grep` of `Models/BugTask.cs` and `Models/bug.cs` in `migration/inventory.tsv` returns nothing). The only related rows are the `EF_MAPPING_REPLACED` rows of `Models/Mapping/BugTaskMap.cs` (21) and `Models/Mapping/bugMap.cs` (23), used below to cross-check field names.

## Checks performed

| Check | Rule | Result |
|---|---|---|
| Output paths = manifest naming; `bug.cs` primary type `Bug` (stem match case-insensitive) | §4 | OK: `btnet/Models/BugTask.java`, `btnet/Models/Bug.java` |
| Package = C# namespace verbatim (`btnet.Models`) | §2A Namespace, §4 | OK (line 2 of both) |
| Header `// Source: src/BugTracker.Web/Models/BugTask.cs` / `.../Models/bug.cs` (source casing kept) on line 1 | §6.1, S1-41 | OK |
| `partial class` → one Java class | §2A partial class | OK (only one part exists for each) |
| Auto-properties → public fields, same names/casing, no initializer in source | §2A Auto-property, §2A EF6 entities | OK: all 22 members of `BugTask` and all 22 members of `Bug` present, in source order, same names |
| `int` → `int`; `DateTime` → `LocalDateTime`; `decimal?` → `BigDecimal`; `string` → `String` | §2A primitive row (line 163) | OK |
| `Nullable<int>` → `Integer`; `Nullable<DateTime>` → `LocalDateTime` (boxed/reference) | §2A `T?` row (line 151) | OK: `AssignedToUserId`, `PercentComplete`, `StatusId`, `SortSequence` (BugTask); `AssignedToUserId`, `LastUpdatedUserId`, `UserDefinedAttributeId` (Bug) are `Integer`; non-nullable `int` stay `int` |
| `virtual` navigation properties (`Bug`, `CreatedUser`, `LastUpdatedUser`, `AssignedToUser`, `Status`, `ReportedUser`, `Priority`, `Organization`, `Category`, `Project`) → public fields, never loaded | §2A EF6 entities (line 556-559) | OK; `virtual` has no Java counterpart needed (no override, no lazy proxy) |
| `IList<BugTask> Tasks` → `List<BugTask>` | §2A C1 (line 148) | OK, `java.util.List` imported |
| Navigation `TODO(port)` needed only "if a site reads one" | §2A EF6 | OK: no source site reads any navigation property or `Tasks` (`grep` over `src/BugTracker.Web` for `.Tasks`, `new Bug`, `new BugTask`, `.Bugs`, `.BugTasks` hits only `Context.cs:28,30,66`), so `todos=0` is correct |
| Field names match every `t => t.X` lambda the maps use (EF_MAPPING_REPLACED rows turn them into `Property("X")` strings, so a misspelling would fail at runtime) | §2A EF6 maps; inventory `BugTaskMap.cs` L11-L39, `bugMap.cs` L11-L48 | OK: all 18 BugTaskMap names and all 18 bugMap names exist with exact casing |
| Referenced types `User`, `Status`, `Priority`, `Organization`, `Category`, `Project` exist in the same package | §4 home of shared types | OK: all present in `port/web/src/main/java/btnet/Models/`; no import needed; no runtime-name clash (J-C15) |
| Field named like its type (`public Bug Bug;`, `public Status Status;`, `public Project Project;`, …) | compile-by-reading | OK: legal Java (fields and types are in different namespaces); no member of these classes uses the simple name in a type position where obscuring would bite |
| Imports explicit single-type, no wildcard, no static; unused C# `using` dropped | §4 | OK; `BigDecimal` imported only in `BugTask.java` where used |
| Access: all members `public` in source and target; top-level classes `public` | §2A S2-12 | OK |
| No source comments to copy | §6.2 | OK |
| Markers: none needed | §6.3 | OK |
| Trailer last line `// PORT STATUS: confidence=high todos=0` (ends with a newline, nothing after) | §6.7 | OK in both files |
| Scope: only the two manifest targets written | §6.4 | OK |
| Inventory citation: no inventory decision used, so no `// inventory:` comment required | §5, §6.5 | OK |
| Thread-safety | n/a | Plain data holders, no shared state; same as C# |

## Findings

None. I looked for a defect in every member and could not find one that a rule or a source line supports: every member maps one-to-one under the §2A type table and the EF6 entity rule, and every §6 obligation is met. The only behavioral difference I found (default value of non-nullable `DateTime` fields) is something the rulebook has not decided and that no code path reaches, so it is listed under Rulebook gaps instead.

## Rulebook obedience

OBEDIENT. Rules applied and followed: §2A Auto-property (public fields), §2A `T?`/`Nullable<T>` (boxed types), §2A primitive mapping (`decimal` → `BigDecimal`, `DateTime` → `LocalDateTime`), §2A C1 (`IList<T>` → `List<T>`), §2A EF6 entities (navigation properties as never-loaded public fields), §2A partial class, §2A S2-12 access modifiers, §4 naming/package/imports, §5 inventory grep (no rows; the rulebook does not require an entity-level row, since rows record per-site decisions and these files have none), §6.1 header, §6.2 completeness, §6.7 trailer with a correct `todos=0`. No deviations.

## Rulebook gaps

1. **Default value of non-nullable value-type fields (`DateTime`, `decimal`, `TimeSpan`) on entities and DTOs.** C# `public DateTime CreatedDate { get; set; }` starts as `DateTime.MinValue` (`0001-01-01T00:00`); the §2A mapping to `LocalDateTime` makes it start as `null`. Sites: `Models/BugTask.cs:16,20` (`CreatedDate`, `LastUpdatedDate`), `Models/bug.cs:14` (`ReportedDate`); probably other `Models/*.cs` entities too. Today nothing can observe it: neither class is constructed with `new` or read outside `Context.cs` (`DbSet<BugTask> BugTasks`, `DbSet<Bug> Bugs`), and EF-loaded rows always set the field. **Proposed rule:** "An auto-property or field of C# type `DateTime` with no initializer keeps the Java default `null`, unless a source site reads it before it is assigned (for example a `new T()` that is then serialized or concatenated). In that case initialize it to `DateTimes.MinValue` (runtime constant `LocalDateTime.of(1, 1, 1, 0, 0)`) and cite an inventory row. `decimal` → `BigDecimal.ZERO` and `TimeSpan` → `Duration.ZERO` under the same condition." This way two translators cannot differ on a future site, such as a `BugFromEmail`-style DTO bound from a request.
