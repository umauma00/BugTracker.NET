# Review R1 — src/BugTracker.Web/btnet/SQLString.cs

Target: `port/web/src/main/java/btnet/SQLString.java` (no template; core unit, §0).
Inventory: no rows have `file` = `SQLString.cs`. Rows that mention it (dbutil.cs L28/L41/L60/L100/L148 `LINQ_TOARRAY`) require
`GetParameters()` to return `java.util.List<SqlParameter>` (so callers can use `toArray(new SqlParameter[0])`). It does.

Checked against RULEBOOK §0 (structure-preserving), §2A General (IList→List [C1], property facade convention, object initializer [C6],
string concatenation [S1-46/S2-17/S2-18], StartsWith [S2-11], `var`), §2A Database (`override ToString()`, `AddParameterWithValue`
null→DBNull, `@` prefix, both overloads kept), §3 runtime (`btnet.runtime.data.SqlParameter`: `ParameterName(String)`, `Value(Object)`;
`btnet.runtime.DBNull.Value`; `Strings.cs`), §6 (header, completeness, markers, trailer).

## Findings

None.

Checked one by one, all faithful:

- Fields `_value`, `_parameters` (`SQLString.cs:10-11`) map to `private String`/`private List<SqlParameter>` (`SQLString.java:12-13`). C1 and S2-12 are kept.
- Both constructors (`:13-23` → `:15-25`). The two-arg constructor keeps the caller's list by reference, so the aliasing used at `util.cs:435,487` (`new SQLString(..., sql.GetParameters())`) is the same.
- `override ToString()` maps to `@Override public String toString()` and returns `_value`, which may be null (§2A Database).
- `AddParameterWithValue(string, object)` (`:30-38` → `:33-44`):
  - null becomes `DBNull.Value` (§2A Database).
  - `StartsWith("@")` becomes ordinal `startsWith` (§2A row; the culture difference is accepted under S2-11).
  - `"@" + parameter` wraps its operand in `Strings.cs` (S2-18).
  - The object initializer is built as a local, with `ParameterName` and then `Value` in source order (C6) and facade setters (§2A property row; runtime `SqlParameter.java` has `void ParameterName(String)` and `void Value(Object)`).
  - It returns `this`.
  - A null `parameter` throws an NPE at `startsWith`, matching the NRE at `:34`.
- `AddParameterWithValue(string, int)` (`:40-46` → `:46-55`) has no DBNull substitution, which is correct. The `int` is boxed to `Integer`, and §2A Parameter binding infers `int`, as SqlClient does for `Int32`. Java overload choice matches C# at the call sites: `short`/`char`/`int` go to the `int` overload, while `long` and boxed or nullable values go to `Object`.
- `Append(string)` (`:49-53` → `:58-62`): `_value` is a field that may be null, so `_value = Strings.cs(_value) + Strings.cs(toAppend)` is exactly the S2-17 field form. C# `string.Concat` drops null on both sides.
- `Append(SQLString)` (`:55-62` → `:64-71`): the text is concatenated as above. A null `toAppend` throws an NPE, matching the NRE. Parameters are appended in enumeration order. The same `SqlParameter` objects are shared, not copied, as in C#.
- `GetParameters()` returns the live list (`:63-66` → `:72-75`).
- Imports: `btnet.runtime.DBNull`, `btnet.runtime.Strings` and `btnet.runtime.data.SqlParameter` are all listed in §3. `java.util` is used for the rest. No call goes outside the §3 runtime. By reading, nothing should fail to compile.
- §6:
  - The header `// Source: src/BugTracker.Web/btnet/SQLString.cs` is correct.
  - Every member is present.
  - There is no SQL text, so the byte-identity check does not apply.
  - There are no markers. The trailer `confidence=high todos=0` matches 0 `TODO(port)`.

## Rulebook obedience

OBEDIENT. I found no deviation.

One point looked like a deviation, but I am not counting it. At `SQLString.java:66`, `toAppend.toString()` is called on a parameter that could be null. The J-C17 row (§2A, "`o.ToString()` on any other non-String receiver … → `Strings.ToStringStrict(o)`") read literally would require `Strings.ToStringStrict(toAppend)`. But the §2A Database block uses `sql.toString()` directly on an `SQLString` (`new SqlCommand(sql.toString(), conn)`), so the implementer followed the more specific example the rulebook gives for this exact type. Both forms behave the same:
- On null, both throw an NPE.
- Otherwise `Convert.ToString(o)` falls through to `o.toString()` for an application type (`Convert.java:67`), and the `Strings.cs` wrap makes a null `_value` give `""` either way.

See Gap G1.

## Rulebook gaps

- **G1. `ToString()` on an application type that overrides it (SQLString).** J-C17 (`Strings.ToStringStrict`) and the §2A Database example (`sql.toString()`) disagree for an `SQLString` receiver that may be null.
  - Proposed rule: "`x.ToString()` where `x`'s static type is a translated application class that overrides `ToString()` → `x.toString()` (NPE on null ≈ NRE). J-C17 applies to `Object`/runtime-typed receivers only."
  - Evidence: RULEBOOK §2A Database code block (`sql.toString()`), the §2A J-C17 row, `SQLString.cs:57`, `dbutil.cs:24-31`.
- **G2. Mutating a `List<T>` while a `foreach` enumerates it.** `SQLString.cs:58-59` enumerates `toAppend.GetParameters()` and adds to `_parameters`. When these are the same list (`s.Append(s)`, or two `SQLString`s sharing a list through the `util.cs:435,487` constructor), .NET throws `InvalidOperationException("Collection was modified; enumeration operation may not execute.")`. Java throws `java.util.ConcurrentModificationException`. Both throw on a non-empty list and neither throws on an empty one; only the exception type and message differ.
  - Today this is unreachable: no call site in `src/` appends an `SQLString` to itself or to one that shares its list (grep of `.Append(` on `sql`).
  - Proposed rule: "`foreach` over a `List<T>` that the body may modify: accepted divergence (CME vs InvalidOperationException), recorded in the deviation log like D-006." Or, if it should be emulated, a runtime helper.
  - The rulebook decides neither.
