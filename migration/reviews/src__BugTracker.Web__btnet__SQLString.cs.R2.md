# Review R2 — src/BugTracker.Web/btnet/SQLString.cs

Target: `port/web/src/main/java/btnet/SQLString.java` (77 lines). Core layer,
structure-preserving (RULEBOOK §0). There is no template for this unit (§4:
templates are for pages/masters/.ascx only).

Inputs read: RULEBOOK §0, §0.1, §1, §2A (General conventions, Strings,
Database), §3 runtime list, §4, §5, §6; inventory (no row has `file` =
`src/BugTracker.Web/btnet/SQLString.cs`; the 5 rows that mention the file are
caller-side rows in `dbutil.cs`, `bug.cs`, `Security.cs`,
`add_customfield.aspx.cs`, `get_db_datetime.aspx`); the source; the
translation; runtime `btnet.runtime.data.SqlParameter`, `btnet.runtime.Strings`
(`cs`, `ToStringStrict`), `btnet.runtime.DBNull`.

## Line-by-line check

| Source | Target | Rule | Verdict |
|---|---|---|---|
| `SQLString.cs:8` `public class SQLString` | `:10` | §4 package `btnet`, same name | OK |
| `:10` `private string _value;` | `:12` | §2A access modifiers [S2-12] | OK |
| `:11` `private IList<SqlParameter> _parameters;` | `:13` `List<SqlParameter>` | §2A `IList<T>` → `java.util.List<T>` [C1] | OK |
| `:13-17` ctor(string) | `:15-19` `new ArrayList<SqlParameter>()` | §2A `List<T>` → `ArrayList` | OK |
| `:19-23` ctor(string, IList) | `:21-25` stores the reference (aliasing kept, needed by `util.cs:487`) | §6.2 completeness | OK |
| `:25-28` `override ToString()` | `:27-31` `@Override public String toString()` | §2A Database "override ToString()" | OK |
| `:30-38` `AddParameterWithValue(string, object)` | `:33-44` | §2A Database (null → `DBNull.Value`, `@` prefix, both overloads); `StartsWith` → ordinal `startsWith` (§2A Strings, accepted [S2-11]); `"@" + parameter` → `"@" + Strings.cs(parameter)` [S1-46, S2-18]; object initializer → construct + assignments in textual order (`ParameterName` then `Value`) via a local [C6], runtime facade setters `ParameterName(String)`/`Value(Object)` exist (`SqlParameter.java:41,45`) | OK |
| `:40-46` `AddParameterWithValue(string, int)` | `:46-55` | same rules; `p.Value(value)` boxes to `Integer` as C# boxes to `System.Int32`. Java overload resolution matches C#: `int`/`short`/`byte`/`char` → int overload, `long`/boxed/reference (incl. `Integer null`, phase-1 subtyping beats unboxing) → `Object` overload → `null` becomes `DBNull.Value`, as C# `int?` null | OK |
| `:49-53` `Append(string)` `_value += toAppend;` | `:58-62` `_value = Strings.cs(_value) + Strings.cs(toAppend);` | [S2-17]: `_value` is a field that can hold `null` (ctor arg) → `x = Strings.cs(x) + <wrapped e>`; `toAppend` is a `String` parameter → wrapped [S1-46] | OK |
| `:55-62` `Append(SQLString)` | `:64-71` | [S2-17] as above; `toAppend.ToString()` on a `null` `toAppend` → NPE ≈ NRE, as C#; a `null` `_value` of `toAppend` → `""` via `Strings.cs`, as `string.Concat`; `foreach` → enhanced for; parameters appended by reference in order | OK (see Rulebook gaps G1) |
| `:63-66` `GetParameters()` | `:72-75` returns `List<SqlParameter>` (same live list, as C#) | [C1]; `dbutil.cs` rows L28/L41/L60/L100/L148 call `.toArray(new SqlParameter[0])` on it, which compiles | OK |
| — | `:1` `// Source: src/BugTracker.Web/btnet/SQLString.cs` | §6.1 | OK |
| — | `:77` `// PORT STATUS: confidence=high todos=0` | §6.7; file has zero `TODO(port)` markers, so `todos=0` is exact | OK |
| — | imports `btnet.runtime.DBNull`, `btnet.runtime.Strings`, `btnet.runtime.data.SqlParameter`, `java.util.ArrayList`, `java.util.List` | §4 explicit single-type imports; all used; all types exist in §3 / runtime; no name clash [J-C15] | OK |

No SQL text in this unit (§6.2 byte-identity not applicable). No source
comments to copy. No user-visible messages. No exceptions thrown or caught by
the unit itself. No §0.1 fix applies; none was applied. No markers needed;
none present. `using System.Linq` is unused in the source and correctly has no
Java counterpart.

## Findings

None. I could not construct an input on which the Java output (string value,
parameter list contents/order/names/values, exception-vs-no-exception) differs
from the C# within the rulebook's accepted divergences (S2-11 ordinal
`StartsWith`). I found no compile-level error by reading: every runtime member
called (`SqlParameter()`, `ParameterName(String)`, `Value(Object)`,
`DBNull.Value`, `Strings.cs(Object)`) exists with a matching signature.

Counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

**OBEDIENT.** Every construct in the unit maps to an explicit §2A row and the
implementer used that row: C1 (`IList` → `List`), `List<T>` → `ArrayList`,
S2-12 (access modifiers), C6 (object initializer, textual order), S1-46/S2-18
(every non-literal concatenation operand wrapped, including the parameter
`parameter` and the never-null-in-practice `toAppend.toString()`), S2-17
(`+=` on a nullable field rewritten as `x = Strings.cs(x) + …`), §2A Database
(`override ToString()`, DBNull for null, `@` prefix, both overloads kept),
§2A Strings (`StartsWith` → ordinal), §4 (package, name, path, imports),
§6.1/§6.7 (header, trailer, exact todo count), §6.4 (only the target file
written; no template needed).

One judgement call, not counted as a deviation: `toAppend.ToString()` was
written as a direct `toAppend.toString()` rather than
`Strings.ToStringStrict(toAppend)` (J-C17 row). The rulebook's own §2A
Database code block writes `sql.toString()` for the identical receiver type
(`dbutil.cs:24-31`, `SqlCommand(sql.toString(), conn)`), and the results are
identical (NPE on null receiver; `Strings.ToStringStrict` → `Convert.ToString`
→ `toString()` for an application object; the outer `Strings.cs` maps a null
`_value` to `""` either way). See G1.

## Rulebook gaps

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
