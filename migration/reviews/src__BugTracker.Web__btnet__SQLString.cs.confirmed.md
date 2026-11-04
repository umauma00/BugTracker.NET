# Confirmed findings — src__BugTracker.Web__btnet__SQLString.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

- **G1. `ToString()` on an application type that overrides it (SQLString).** J-C17 (`Strings.ToStringStrict`) and the §2A Database example (`sql.toString()`) disagree for an `SQLString` receiver that may be null.
  - Proposed rule: "`x.ToString()` where `x`'s static type is a translated application class that overrides `ToString()` → `x.toString()` (NPE on null ≈ NRE). J-C17 applies to `Object`/runtime-typed receivers only."
  - Evidence: RULEBOOK §2A Database code block (`sql.toString()`), the §2A J-C17 row, `SQLString.cs:57`, `dbutil.cs:24-31`.
- **G2. Mutating a `List<T>` while a `foreach` enumerates it.** `SQLString.cs:58-59` enumerates `toAppend.GetParameters()` and adds to `_parameters`. When these are the same list (`s.Append(s)`, or two `SQLString`s sharing a list through the `util.cs:435,487` constructor), .NET throws `InvalidOperationException("Collection was modified; enumeration operation may not execute.")`. Java throws `java.util.ConcurrentModificationException`. Both throw on a non-empty list and neither throws on an empty one; only the exception type and message differ.
  - Today this is unreachable: no call site in `src/` appends an `SQLString` to itself or to one that shares its list (grep of `.Append(` on `sql`).
  - Proposed rule: "`foreach` over a `List<T>` that the body may modify: accepted divergence (CME vs InvalidOperationException), recorded in the deviation log like D-006." Or, if it should be emulated, a runtime helper.
  - The rulebook decides neither.

- **G1 — `ToString()` call sites on an application type that overrides it
  (`SQLString`).** The J-C17 row ("`o.ToString()` on any other non-String
  receiver … → `Strings.ToStringStrict(o)`") read literally covers
  `toAppend.ToString()` (`SQLString.cs:57`) and every `sql.ToString()` in
  `dbutil.cs`, while the §2A Database code block shows `sql.toString()`. Two
  translators can therefore differ (behavior is the same for `SQLString`
  because its `toString()` returns a `String` and `ToStringStrict` delegates to
  it). Proposed rule: "`x.ToString()` where `x`'s static type is an
  application class that declares `override ToString()` → `x.toString()`
  (NPE on null ≈ NRE); wrap in `Strings.cs` when it is a concatenation
  operand. J-C17 applies to `Object`/BCL/runtime-typed receivers." Evidence:
  RULEBOOK §2A Database code block vs J-C17 row; `SQLString.cs:57`;
  `dbutil.cs:25`.
- **G2 — mutation of a `List<T>` during `foreach` (self-append).**
  `x.Append(x)` with a non-empty parameter list throws
  `InvalidOperationException("Collection was modified; enumeration operation
  may not execute.")` in .NET; the Java enhanced-for over `ArrayList` throws
  `java.util.ConcurrentModificationException`. Both are unchecked and caught
  by any `catch (Exception)` → `RuntimeException` mapping, but
  `Exceptions.Message(e)` text and the exception type differ. No caller in
  `src/` does a self-append (grep `(\w+)\.Append\(\1\)` over `src/**/*.cs`:
  no hits), so this is latent. Proposed rule: record as an accepted latent
  divergence (D-006 style), or have `Exceptions` map
  `ConcurrentModificationException` to the .NET message. Evidence:
  `SQLString.cs:58-59`.
- **G3 — inventory coverage.** The inventory has no rows with `file` =
  `src/BugTracker.Web/btnet/SQLString.cs`. Every construct in the unit is
  decided by a rule row (§2A), so this is not an inventory bug under §5; noted
  only so the human knows the unit was translated from rules alone.
