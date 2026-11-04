# Review R2 — src/BugTracker.Web/btnet/sortable_html_table.cs

Target: `port/web/src/main/java/btnet/SortableHtmlTable.java` (core layer, §0
structure-preserving; no template — §4 places none for a `btnet/` core unit).

Inputs read: RULEBOOK.md (all 1,884 lines, incl. §3.1), inventory.tsv (no rows for
this unit — `grep sortable_html_table` matches nothing; no per-site decision is needed:
there are no `ref`/nullable/region/BUG sites), the source, the target, runtime
`Convert`, `Strings.cs`, `HttpResponse.Write*`, `HttpUtility.HtmlEncode`,
`DataSet`/`DataTableCollection`/`DataColumnCollection`/`DataRowCollection`/`DataRow`/`DataColumn`,
and the translated `btnet/Util.java` (`is_numeric_datatype(Class<?>)`,
`format_db_date_and_time(Object)`, `format_db_value(BigDecimal)`). Judge: S6 uses
`mytable` and the `td.datah` heading row (S6.md:41,113,119-120). Both survive verbatim.

## Findings

None.

Things I checked line by line and found correct:

- All 8 public static members are present with the same names, parameter names, order and
  overloads (4-arg, 5-arg, 6-arg `create_from_dataset`, `create_nonsortable_from_dataset`,
  `create_start_of_table`, `create_end_of_table`, `create_headings`, `create_body`) (§6.2, §2A names).
  Overload resolution of the delegating calls (target :29-35, :45) picks the same overloads as C#.
- Every literal output string matches byte for byte: `\n<div id=wait class=please_wait>&nbsp;</div>\n`,
  `click_to_sort`, `myholder`, `mytable border=1 class=datat`, `sortedby`, `datah valign=bottom`
  (the `\n` after the opening `<td>`), the `sort_by_col($col, \"$type\")` template, `\n<tr>` / `</tr>\n`,
  `datad align=right`, `&nbsp;`, and the edit/delete anchors.
- String `!=` → `!Objects.equals` (§2A Strings) at :123,126,130,209,212,217.
- Concatenation operands `edit_url`/`delete_url` and `dr[...]` wrapped in `Strings.cs` (S1-46, S2-18) at :215,220.
- `dr[i].ToString()` on DataRow values → `Convert.ToString(dr.get(i))` (§2A row for DataRow values) at :234,252.
- `r.Write(dr[i])` → `r.Write(dr.get(i))` resolves to `Write(Object)` = `Strings.cs` (S2-2), the same as .NET `HttpWriter.Write(object)`.
- `typeof(System.DateTime)` → `LocalDateTime.class`, `typeof(System.Decimal)` → `BigDecimal.class`, `Type` → `Class<?>` (§2A typeof table, S1-7). Because tinyint is `ClrTypes.Byte`, `is_numeric_datatype` (right alignment) behaves as in .NET.
- DataSet access `Tables().get(0)`, `Columns().Count()`, `Columns().get(i).DataType()`, `ColumnName()`, `dr.get(i)` (§2A DataSet table, S2-37). Both foreach loops use the `Iterable` collections (S1-19). `Count - 1` is evaluated again on every iteration, as in the source.
- `StartsWith`/`Replace` → ordinal `startsWith`/`replace` (§2A Strings. The culture-sensitive `StartsWith` difference is accepted under S2-11).
- `Convert.ToString(db_column_count)` and `Convert.ToDecimal(dr.get(i))` call existing runtime overloads. `btnet.Util.format_db_value(BigDecimal)` exists and is chosen statically, as C#'s `format_db_value(Decimal)` is.
- Compile-level: every import is single-type and used, and every class is in §3.1. There is no name clash (package `btnet` has no `Convert`/`Strings`/`HttpResponse`/`HttpUtility` type, J-C15). `Class<?> == LocalDateTime.class` compiles. No checked exceptions.
- No quirk was "fixed". Unencoded column headings and unquoted `href` values are reproduced as written.
- Header `// Source: src/BugTracker.Web/btnet/sortable_html_table.cs` is the first line (§6.1). The copyright block and all source comments are copied, including the commented-out debug writes (§6.2). The trailer `// PORT STATUS: confidence=high todos=0` is the last line, and the count is correct: there are 0 `TODO(port)` markers (§6.7).

## Rulebook obedience

**OBEDIENT** — I found no deviation from any rule. Rules exercised: §0 (core,
structure-preserving), §2A General (names, access modifiers kept `public`, indexers,
runtime-property convention), §2A Strings (`Objects.equals`, S1-46/S2-18 `Strings.cs`,
`Convert.ToString` for DataRow values, ordinal `startsWith`/`replace`), §2A Conversions,
§2A DataSet (+S1-19, S2-37, typeof table), §2A Web intrinsics (`HttpResponse` parameter
kept, not changed to `Writer`; `HttpUtility.HtmlEncode`), B3/S2-2 `Write` semantics, §4
(path, package `btnet`, explicit imports), §6.1/6.2/6.3/6.7.

## Rulebook gaps

1. **Core units with zero inventory rows.** §5 says "a site missing from the inventory is
   an inventory bug", but this unit has no rows at all, and it needs none. Proposed: add to §5
   "a core unit whose every construct is decided by a §2A table needs no inventory row;
   absence of rows for such a unit is not an inventory bug" (as S1-36 already says for
   element-content regions). Evidence: `grep sortable_html_table migration/inventory.tsv` → empty.
2. **`HttpUtility.HtmlEncode` overload choice.** The runtime has both `HtmlEncode(String)`
   and `HtmlEncode(Object)` (HttpUtility.java:37,82). The rulebook does not say which one a
   translator must target when the C# argument is `x.ToString()` and the Java argument is
   `Convert.ToString(x)` (statically `String`). Today both give the same result for non-null
   input. Proposed: "the overload is the one Java picks from the translated argument's
   static type; never cast to force the other". Evidence: target :252, source :245.
3. **Unencoded output that the source leaves raw** (column headings written without
   `HtmlEncode` even when `html_encode` is true, source :151,162; unquoted `href=` + raw
   id value, source :207-213). It is not decided whether this counts as a "defect" that needs
   `BUG(port)` under the BUG rule or as intended behavior. A second translator might add a
   marker. Proposed: "raw (unencoded) output of developer-controlled values (SQL column
   aliases, row ids) is not a BUG(port) site; only user-controlled data written unencoded
   needs a marker". Evidence: callers pass SQL with fixed aliases (e.g. users.aspx, reports.aspx).
