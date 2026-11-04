# Review R1 — src/BugTracker.Web/Search/BugSearch.cs

Target: `port/web/src/main/java/btnet/Search/BugSearch.java` (224 lines). Core
unit (§0, `Search/`), so there is no template, no judge page contract (§6.6 n/a)
and no bakeoff.

Read: RULEBOOK.md 1–1884 (§2A NEST row :647, §3 `ElasticClient` :1176-1191,
`ElasticClient` failure semantics, `JsonConfig.mapper()`, `ToStringStrict`,
§3.1 index, §4, §6); inventory.tsv rows 405–426 (all rows for this file);
the C# source; the Java target; and, to check the calls compile, runtime
`Hit`/`Highlight`/`SearchResponse`/`ElasticClient`/`Convert`/`Strings`/
`Collections2`/`DataRowCollection`/`DataColumnCollection` and translated
`Util`/`DbUtil`/`SQLString`/`IBugSearch`/`BugSearchFactory`.

## What I checked and found equivalent (no finding)

- **SQL text**: I extracted all three `@"…"` literals from the CRLF source and
  decoded the Java literals with a script. All three are byte-identical
  (CRLF, trailing spaces after `from bugs ` / `bp_id, ` / `from bug_posts `,
  the 16-space indentation, `[desc]`, `[text]`) (§2A `@"verbatim"` C24).
- **Log messages** (`started creating search index for all bugs`, `Done
  recreating…`, `exception building search index: `, `started updating search
  index`, `done updating search index`, `exception updating search index: `):
  identical. `e.Message` → `Strings.cs(Exceptions.Message(e))` and
  `e.StackTrace` → `Exceptions.StackTrace(e)` (§2A Exceptions S1-10, S2-18).
  `catch (Exception e)` → `catch (RuntimeException e)` (C11). No
  End/Redirect/Transfer in either try body, so C7 does not apply.
- **Search body**: `{"size":500,"query":{"query_string":{"query":…}},"highlight":{"pre_tags":[…],"post_tags":[…],"fields":{"*":{}}}}`,
  built in that insertion order with `LinkedHashMap` and serialized by
  `JsonConfig.mapper()`, as §2A NEST row / inventory L55 require. The single
  `TODO(port): verify NEST 1.0.2 wire format` is present. `_client.Search("btnet", body)`
  is the inventory L55 form, and it matches the source's default index
  (`BugSearchFactory.cs:21` `defaultIndex: "btnet"`).
- **Security filter**: SQL, `alter_sql_per_project_permissions`, the eager
  `LinkedHashSet<Integer>` over `Rows().stream().map(Convert.ToInt32)`, and
  `Hits.stream().filter(…contains(Convert.ToInt32(h.Id)))` collected to
  `ArrayList` (C1, §2A collections/LINQ rows, inventory L108/L110). The
  call order matches the source (`GetSearchResultDataSet` before
  `GetHitsFilteredBySecurity`). Eager versus lazy `Where` is accepted under S1-44/D-006.
- **Result rows**: `#ffffff`; `Convert.ToInt32(bug.get("bg_id"))`;
  `Strings.ToStringStrict` at :78/:86/:87 (J-C17); the highlight block is the
  verbatim §2A NEST-row form (`!isEmpty()`, `entrySet().iterator().next()`,
  `isEmpty() ? null : get(0)`, public-field syntax, N1). The date string goes
  into a `LocalDateTime` column through `DataRow.set` coercion with no site
  marker, which is correct per J-C11/S1-43. `Convert.ToDecimal(hit.Score)` reaches the
  runtime's `ToDecimal(Object)`, whose `Double` branch is J-C11-exact.
  `hit.Source` null → NPE, as in .NET, where `(JObject)null` followed by an indexer gives an NRE.
- **Indexing**: `_client.Index(bugJson, "bug", Convert.ToInt64(bugRow.get("bg_id")))`
  (J-C10). The private `IndexBug(DataRow, DataTable)` overload and `ToJson`
  are kept with `Map<String,Object>` + `Collections2.set` +
  `JsonConfig.mapper().writeValueAsString` (S1-28, S1-12). `bugId.ToString()`
  → `Convert.ToString(bugId)` (§2A :183, int expression). The ignored return
  value of `sql.AddParameterWithValue(…)` at :147 is preserved. A null
  `get_datarow` result reaches `ToJson`, throws NPE, and is caught and logged
  with the .NET message, as the source does.
- **Members / modifiers**: every member is present: ctor, `_client` (`private final`),
  `IndexAll`, `Search`, `GetHitsFilteredBySecurity`, both `IndexBug`, `ToJson`,
  `GetSearchResultDataSet`, and `ResultColumns` with its 8 constants and their values. The
  private modifiers are kept (S2-12). The nested static class becomes `private static final` with a
  private ctor (§2A static class). Column types follow the `typeof` table
  (`Integer`, `String`, `LocalDateTime`, `BigDecimal`).
- **Compile-by-reading**: every import exists in §3.1 or as a translated unit.
  I checked the signatures I relied on: `Util.write_to_log(String)`,
  `Util.alter_sql_per_project_permissions(SQLString, IIdentity)`,
  `DbUtil.get_dataset/get_datarow(SQLString)`,
  `SQLString.AddParameterWithValue(String, Object)`, `ElasticClient.Search(String,String)`,
  `Index(String,String,long)`, `DataRowCollection implements Iterable<DataRow>`
  + `stream()`, `DataColumnCollection implements Iterable<DataColumn>`,
  `DataRow.get(DataColumn)`, `DataRow.Table()`,
  `DataColumnCollection.Add(String, Class<?>)` and `DataTableCollection.Add()`. The lambda capture
  `visibleBugIds` is effectively final. The two `highlight` names do not
  clash (`highlight_` in method scope, `highlight` in loop scope). No unused or missing
  imports.
- **Comments**: the source comments are copied. `//TODO: Change this…` is rendered
  `// (source TODO) …` (§6.2). The header is `// Source: src/BugTracker.Web/Search/BugSearch.cs` (§6.1).
  The trailer reads `todos=1`, and the file has exactly one `TODO(port)` marker (§6.7).

## Findings

F1 | severity LOW | port/web/src/main/java/btnet/Search/BugSearch.java:109-110 (also :103-104) | src/BugTracker.Web/Search/BugSearch.cs:87-88 (also :81, :83) | RULEBOOK §6.5; inventory rows `src/BugTracker.Web/Search/BugSearch.cs` L87, L88 (L81, L83) | The decisions at :87 (`Strings.ToStringStrict(bug.get("status"))`, an NPE on a missing property per that row) and :88 (`Convert.ToDecimal(hit.Score)`, J-C11 per that row) each come from their own inventory row but have no `// inventory:` citation. The comment at :107 cites only L86. The L81/L83 decisions (`entrySet().iterator().next()`, `isEmpty() ? null : get(0)`) sit under the L79 comment only. §6.5 says to cite the row "where a decision came from an inventory row". Runtime behavior is unaffected. | Add `// inventory: src/BugTracker.Web/Search/BugSearch.cs L87` before line 109 and `// inventory: src/BugTracker.Web/Search/BugSearch.cs L88` before line 110. Optionally add `… L81` / `… L83` beside lines 102/104, or extend the :100 comment to `L79, L81, L83`.

No HIGH or MEDIUM defects found. I compared behavior, SQL, messages, exception flow,
null/DBNull handling, ordering, members, markers and compile-visible typing. Apart from the
citation gap in F1, they match the source and the rulebook's prescribed forms.

## Rulebook obedience

Verdict: **DEVIATIONS: 1**.

1. §6.5: inventory citations are missing for rows L87 and L88 (and, more weakly, L81/L83).
   This is F1.

Checked and **not** deviations:
- `java.time.LocalDateTime.class` / `java.math.BigDecimal.class` are written
  fully qualified in `GetSearchResultDataSet` instead of being imported. §4 requires explicit
  single-type imports when importing and mandates FQN only for name clashes.
  It does not forbid FQN use, so this is not a deviation. It is inconsistent with the
  `typeof` table's spelling (`LocalDateTime.class`), but it compiles to the same token.
- `FirstOrDefault()` is translated as `isEmpty() ? null : get(0)` rather than the general
  §2A LINQ row's `findFirst().orElse(null)`. The specific §2A NEST row prescribes exactly
  this form for :83, so the implementer followed the more specific rule (see gap 2).
- One `TODO(port): verify NEST 1.0.2 wire format` marker for the whole unit, at
  :55. Inventory rows L55 and L79 both mention it. The rulebook states it once
  (§2A :647), so one marker is defensible (see gap 3).

## Rulebook gaps

1. **Json.NET date parsing of `_source` string fields.** NEST 1.0.2
   deserializes `_source` into a `JObject` with Json.NET's default
   `DateParseHandling.DateTime`. Any string value that looks like an ISO date
   becomes a `DateTime` `JValue`, and `ToString()` then renders it in en-US
   `G` format. This applies to `desc` and `status` as well as `bg_reported_date`, which
   §2A :505-510 already covers runtime-side. A bug whose short description is,
   for example, `2016-01-02T03:04:05` shows as `1/2/2016 3:04:05 AM` in .NET
   but raw in Java. The J-C17 note covers only the JSON-`null` case. Evidence:
   `BugSearch.cs:72,78,87`. Proposed rule: add to the J-C17 TODO in §2A :184 (runtime-owned, no site
   marker) that the runtime author confirm NEST 1.0.2's serializer settings and,
   if `DateParseHandling.DateTime` applies, either emulate it in
   `ElasticClient.Search` when building `Hit.Source` (ISO-looking strings →
   `LocalDateTime`, so `ToStringStrict` gives `G`) or record it as an accepted
   latent divergence in D-006.
2. **General LINQ `FirstOrDefault()` row is null-unsafe.** §2A :175 maps
   `FirstOrDefault()` → `findFirst().orElse(null)`, which throws
   `NullPointerException` when the first element is `null`. .NET returns
   `null` in that case. Here the NEST row overrides it, and the runtime can put a `null`
   fragment into `Highlight.Highlights` (`ElasticClient.java` adds `f == null ? null : …`).
   Proposed rule: for a `List` source, `FirstOrDefault()` → `xs.isEmpty() ? null : xs.get(0)`,
   and for a stream, `findFirst()` only where elements are known non-null.
3. **Marker cardinality for a rulebook-level TODO cited by several inventory
   rows.** Rows L55 and L79 both carry `TODO(port): verify NEST 1.0.2 wire
   format`. The rulebook does not say whether that means one marker per unit or one per
   cited site, so `todos=` counts can differ between implementers (1 vs 2).
   Proposed rule: "a TODO text that the rulebook states once for a construct
   family is placed once per unit, at its first site".
4. **Index argument to `ElasticClient.Search`.** C29 gives
   `Search(String index, String jsonBody)` with the runtime falling back to the default index
   for null/empty. The inventory (L55) writes the literal `"btnet"`, which duplicates
   `BugSearchFactory`'s configuration. Behavior is identical today. Proposed rule: state
   explicitly whether translators pass the literal or `_client.DefaultIndex()`, because NEST
   uses the connection's default index here.
