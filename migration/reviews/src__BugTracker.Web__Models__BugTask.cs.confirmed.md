# Confirmed findings — src__BugTracker.Web__Models__BugTask.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

1. **Default value of non-nullable value-type fields (`DateTime`, `decimal`, `TimeSpan`) on entities and DTOs.** C# `public DateTime CreatedDate { get; set; }` starts as `DateTime.MinValue` (`0001-01-01T00:00`); the §2A mapping to `LocalDateTime` makes it start as `null`. Sites: `Models/BugTask.cs:16,20` (`CreatedDate`, `LastUpdatedDate`), `Models/bug.cs:14` (`ReportedDate`); probably other `Models/*.cs` entities too. Today nothing can observe it: neither class is constructed with `new` or read outside `Context.cs` (`DbSet<BugTask> BugTasks`, `DbSet<Bug> Bugs`), and EF-loaded rows always set the field. **Proposed rule:** "An auto-property or field of C# type `DateTime` with no initializer keeps the Java default `null`, unless a source site reads it before it is assigned (for example a `new T()` that is then serialized or concatenated). In that case initialize it to `DateTimes.MinValue` (runtime constant `LocalDateTime.of(1, 1, 1, 0, 0)`) and cite an inventory row. `decimal` → `BigDecimal.ZERO` and `TimeSpan` → `Duration.ZERO` under the same condition." This way two translators cannot differ on a future site, such as a `BugFromEmail`-style DTO bound from a request.

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
