# Review R1: src/BugTracker.Web/btnet/sortable_html_table.cs

Target: `port/web/src/main/java/btnet/SortableHtmlTable.java` (core layer, §0: structure-preserving; no template, §4).
Inputs read: RULEBOOK.md lines 1–1884 (whole), inventory.tsv (no rows for this source path), the C# source, the Java
target, and the runtime members it calls (`DataSet.Tables`, `DataTableCollection.get(int)`, `DataColumnCollection`
(Iterable, `get(int)`, `Count`), `DataColumn.ColumnName/DataType`, `DataRow.get(int)`, `HttpResponse.Write(String|Object)`,
`HttpUtility.HtmlEncode(String)`, `Convert.ToString(Object)`, `Convert.ToDecimal(Object)`, `Strings.cs`) plus the
translated `btnet.Util` (`is_numeric_datatype(Class<?>)`, `format_db_date_and_time(Object)`, `format_db_value(BigDecimal)`).
Judge spec check skipped: this is a core unit, not a page/control (prompt applies the judge contract to pages/controls).

## Findings

None. Every check below was done against the source line by line and found faithful.

| Area | Source | Target | Verdict |
|---|---|---|---|
| Members / overloads (4 `create_from_dataset`-family entry points, `create_start_of_table`, `create_end_of_table`, `create_headings`, `create_body`), names, parameter names, `public static`, class `public` | :18-260 | :25-267 | complete, §6.2, §2A names/access [S2-12] |
| Literal output text | all `"..."` | all `"..."` | byte-identical (mechanical diff of every string literal: same sequence) |
| `edit_url != ""` / `delete_url != ""` | :116,119,123,202,205,210 | :123,126,130,209,212,217 | `!Objects.equals(x, "")`, §2A Strings row 1; a `null` url behaves as C# (counts as non-empty, concatenates as `""`) |
| `foreach (DataColumn dc in ds.Tables[0].Columns)` / `foreach (DataRow dr …Rows)` | :113,195 | :120,202 | §2A DataSet table [S1-19], §2A foreach row |
| `typeof(System.DateTime)` / `typeof(System.Decimal)` | :138,233,237 | :145,240,244 | `LocalDateTime.class` / `BigDecimal.class`, §2A typeof table [S1-7]; tinyint → `ClrTypes.Byte` stays non-numeric via `Util.is_numeric_datatype` |
| `Convert.ToString(db_column_count)` | :158 | :165 | §2A Convert table (int → plain digits) |
| `StartsWith("$no_sort_")`, `Replace` | :149,151 | :156,158 | §2A Strings `StartsWith` (ordinal; culture variant accepted, S2-11) and `Replace` rows |
| Concatenation `"<td…href=" + edit_url + dr[n-1] + ">edit…"` | :207-208,212-213 | :214-215,219-220 | every non-literal operand wrapped in `Strings.cs` (String parameter included), §2A concatenation [S1-46, S2-18]; DBNull → `""`, Double → G15 as C# |
| `dr[i].ToString() == ""` | :227 | :234 | DataRow value receiver cannot be null → `Convert.ToString`, §2A row "`o.ToString()` … DataRow values" + `Objects.equals` |
| `HttpUtility.HtmlEncode(dr[i].ToString())` | :245 | :252 | §2A HtmlEncode row; String overload selected |
| `r.Write(dr[i])` (object) | :249 | :256 | `Write(Object)` = `Strings.cs`, §2B B3 / §3 [S2-2]; Double → G15 [S1-6] (the §2A Convert row names this file explicitly) |
| `Util.format_db_value(Convert.ToDecimal(dr[i]))` | :239 | :246 | Java overload resolution picks `format_db_value(BigDecimal)` exactly as C# picks `(Decimal)` |
| `ds.Tables[0]` on an empty DataSet | :113 | :120 | runtime throws `IndexOutOfRangeException("Cannot find table 0.")` like .NET |
| Comments copied (`// html encode`, `// data`, `//r.Write ("<br>"); // for debugging`, `// end SortableHtmlTable`, `// end namespace`) | — | — | §6.2 |
| Header `// Source: src/BugTracker.Web/btnet/sortable_html_table.cs`; trailer `// PORT STATUS: confidence=high todos=0` (0 `TODO(port)` markers in file) | — | :1, :271 | §6.1, §6.7 |
| Imports: explicit single-type, all used, all present in §3.1 (`btnet.runtime.Convert`, `Strings`, `data.DataColumn/DataRow/DataSet`, `web.HttpResponse/HttpUtility`); no name clash with an app type | — | :9-18 | §4 imports, §3.1 |
| Compile-level reading: every call resolves to an existing member with a matching signature (`Class<?>` for `DataType()`, `Object` returns into `Strings.cs`/`Convert.*`/`format_db_date_and_time(Object)`) | — | — | OK |
| Thread-safety: no shared state in source or target | — | — | OK |
| Sanctioned fixes: none applicable; no quirk "fixed" (unquoted `href=` attribute value, raw `ColumnName` output, raw cell output when `html_encode == false` all preserved) | — | — | §0.1 |

Counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

OBEDIENT. No deviation found. Rules exercised and followed: §0 (core, structure-preserving), §2A General conventions
(names, access modifiers S2-12, foreach), §2A Strings (`==` → `Objects.equals`, concatenation `Strings.cs` S1-46/S2-18,
`ToString` on DataRow values → `Convert.ToString`, `StartsWith`/`Replace`), §2A Convert table, §2A DataSet mapping table
(S1-19, S2-37) and `typeof` table (S1-7), §2A web intrinsics (`HttpResponse` parameter kept, `HttpUtility.HtmlEncode`),
§2B B3 `Response.Write` row (S2-2), §3.1 (all imports exist), §4 (path, package `btnet`, imports), §5 (inventory grep:
no rows; no site in this file needs a per-site decision, so the absence is not an inventory bug), §6.1/6.2/6.3/6.7.

## Rulebook gaps

1. **Raw `ColumnName` in heading cells** (`sortable_html_table.cs:151,162`): column names (SQL aliases, including
   custom-field and query-author aliases) are written without encoding even when `html_encode == true`. The rulebook does
   not say whether this counts as a source defect requiring a `BUG(port)` marker (The BUG rule) or is intended
   behavior (the `html_encode` flag only covers cell values). The implementer reproduced it unmarked; given the flag's
   evident intent this is defensible. Proposed rule: "Unencoded output of database-derived identifiers (column names)
   that the source never encodes is reproduced unmarked; a `BUG(port)` marker is required only where the source
   encodes the same value elsewhere on the same path." Evidence: `:151,162` vs `:243-245`.
2. **Inventory coverage for files without per-site decisions**: §5 says "a site missing from the inventory is an
   inventory bug", but this unit has zero rows and no construct that needs one. Proposed clarification (cf. S1-36 for
   element-content regions): "A unit whose constructs are all decided by rule rows needs no inventory row; zero rows is
   not an inventory bug." Evidence: `grep sortable_html_table migration/inventory.tsv` → empty.
