# 03 — Pilot run, round 1

Production pipeline (migration/pipeline/*.md) on 5 units; orchestrator-driven (nested agents unavailable — see report). Models: implementer/fixer Sonnet 5.5, reviewers/adjudicator Opus 5.5, effort medium.

| Unit | Impl (s) | R1 findings | R2 findings | Confirmed | Not confirmed | Obedience (R1/R2) | Fixer |
|---|---|---|---|---|---|---|---|
| btnet/util.cs | 242 | 5 (M1 L4) | 6 (M1 L5) | 7 | 3 | DEV 4 / DEV 4 | applied 7, flagged 0 |
| btnet_service/POP3Main.cs | 204 | 6 (M1 L5) | 8 (M3 L5) | 8 | 0 | DEV 5 / DEV 5 | applied 8, flagged 0 |
| Search/BugSearch.cs | 86 | 5 (M1 L4) | 4 (M1 L3) | 4 | 2 | DEV 5 / DEV 3 | applied 4, flagged 0 |
| Controls/BugList.ascx | 99 | 3 (M1 L2) | 4 (M2 L2) | 4 | 0 | DEV 3 / DEV 4 | applied 4, flagged 0 |
| edit_bug.aspx | 513 | 5 (M1 L4) | 3 (L3) | 4 | 2 | DEV 4 / DEV 5 | applied 4, flagged 0 |

HIGH findings: 0 across all units. Every unit had a §6.7 trailer deviation caused by the pipeline prompt (P1, fixed). Reviewers verified SQL/literals byte-for-byte by script in every core unit.

Pipeline defects found by the pilot: see pilot-notes below.

- P1 pipeline prompt contradicted RULEBOOK §6 trailer format (PORT-STATUS vs PORT STATUS); fixed in migration/pipeline/*.md after the BugSearch implementer reported it. Pilot units implemented before the fix carry the old trailer.
- Implementer gaps BugSearch: no rule for serializing DataTable to JSON (ToJson); DataRow.Table(), DataRow.get(DataColumn), Tables().Add() signatures missing in §3.
- Implementer gaps BugList: EventArgs not in §3; __scriptN numbering when several regions share a script body; diamond in conditional expressions.
- Implementer gaps POP3Main: rulebook vs inventory disagree (Threads.start vs hand-expand at POP3Main.cs:86-87,91-92; Encoding.Default.GetString vs new String(bytes, Encoding.Default()) row 970) — §5 says rule wins, inventory rows must be corrected; §3 missing StringComparison, ElapsedEventArgs, ICertificatePolicy/trust-all mapping, pop Message accessors; no rule for Console.WriteLine or the service's ConnectionString → JDBC URL.
- Implementer gaps util.cs: Strings.Split with string separator; CultureInfo.NumberFormat/InvariantCulture; DotNetFormat numeric "G"; Encoding.GetPreamble; HttpResponse.ContentEncoding; UInt16/32/64 in is_numeric_datatype; AppDomain config dir; concat rule (Strings.cs wrapping) noisy vs inventory L1117 "unchanged".
- P2 role prompts named repo files by relative path; agents working in {OUT} looked for {OUT}/migration/RULEBOOK.md. Fixed: absolute repo paths. P3 BugSearch fixer prompt was rendered before P1 fix (asked for fixed=<ids>); fixer correctly followed §6.7.

## Rulebook gaps — src__BugTracker.Web__Controls__BugList.ascx

G1. **`.ascx` class shape and constructor (B1/B6 vs §0).** §0 says `.ascx` → "Spring MVC `@Controller` class". B6 says it is a class extending `UserControl` with no mapping. Neither B6 nor §3 states that each `.ascx` class declares `public <Name>(String id) { super(id); }`, or which constructors runtime `UserControl` has. Proposed (B6): "`.ascx` → plain class extending `UserControl` (no `@Controller`/`@RequestMapping`/`@RequestScope`) that declares `public <Name>(String id) { super(id); }`. Runtime `UserControl` has exactly that constructor. The host creates it as `control(new <Name>("<ID>"))`." Affects BugList, MainMenu, Footer. (R1.G1 + R2.G1)

G2. **EF6 `OrderBy(...).Select(e => new T { … })` projection.** EF6 selects only the projected columns. The inventory (rows 29/33/37/41/45/49) prescribes a full entity load followed by an in-memory map. That is observably different if a mapped column is missing or a non-nullable int is NULL. It is latent with today's `setup.sql`. Proposed: a runtime `.Select("F", …)` projection rule, or record the full load as an accepted deviation. (R1.G2)

G3. **Source comments containing `TODO`/`FIXME`.** §6.2/§6.3 do not say whether these are copied, rewritten or dropped. Proposed: "copy source comments; rewrite a leading `TODO:`/`FIXME:` as `(source TODO)`/`(source comment)`." Evidence: BugList.ascx.cs:49. (R1.G3)

G4. **Inventory inconsistencies for BugList.ascx:128.** Row 26 still carries the withdrawn "string.Join has no RULEBOOK row … String.join chosen" note, which contradicts C3/J-U15. Row 27 says `__renderN()`, but rows 25/28 and B5 (a) say `__scriptN()`. Correct the inventory. (R1.G4)

G5. **Missing collection rows in §2A:** LINQ `array.Contains(x)` → `Arrays.asList(array).contains(x)` (BugList.ascx.cs:42), and `Dictionary.ContainsKey` → `containsKey`. The null key throws in .NET but returns false in Java; accept that difference unless the inventory flags a nullable key (BugList.ascx.cs:30). (R1.G5 + R2.G5)

G6. **Undeclared attributes on a user-control tag.** `bugs.aspx:55` has `ShowCheckbox="False"`, and BugList has no such property. ASP.NET puts it into `Attributes` and never renders it. Proposed: drop it, with a `// <attr>="<value>": no such property on <Type> (ignored by ASP.NET)` comment at the host field. (R2.G2)

G7. **Dictionary collection initializers.** `new Dictionary<K,V> { {k,v}, … }` (BugList.ascx.cs:18-26) has no row. .NET `Add` throws on a duplicate key and `put` overwrites. Proposed: instance (or static) initializer using `Collections2.Add`, or allow `put` when every key is a distinct literal. (R2.G3)

G8. **`IDictionary<K,V>` declared type.** C1 does not cover it. Proposed: `IDictionary<K,V>` → `java.util.Map<K,V>` (the instance stays `LinkedHashMap`). (R2.G4)

G9. **`DbContext.close()` signature.** §3 should state that runtime `DbContext`/`Context` `close()` is declared without `throws Exception`. Otherwise every `try (Context context = new Context())` fails javac (six here). (R2.G6)

## Rulebook gaps — src__BugTracker.Web__Search__BugSearch.cs

G1. **Source comments containing `TODO`/`FIXME`** (R1.G1). §6.2/§6.3 do not say how a copied source comment with a non-§3 marker token is handled (BugSearch.cs:102). Proposed: "Source comments are preserved; a leading `TODO`/`FIXME`/`HACK` token is rewritten to `(source TODO)`."

G2. **`typeof(<primitive>)` in DataColumn context** (R1.G2). §2A `typeof(T)` → `T.class` vs primitive table `int` → `int` is ambiguous (BugSearch.cs:199,203,205). Proposed: `typeof(int|long|bool|double)` → `Integer|Long|Boolean|Double.class`, `typeof(decimal)` → `BigDecimal.class`, `typeof(DateTime)` → `LocalDateTime.class`.

G3. **DataSet-family and JsonConfig members missing from §2A/§3** (R1.G3, R2.G4, R2.G3 part). Add rows for `DataRow.Table()`, `DataRow.get(DataColumn)`, `DataColumnCollection` iteration, `DataTableCollection.Add()`, `DataColumnCollection.Add(String, Class)`, `DataRowCollection.stream()` (implied by the AsEnumerable row), and name the accessor `JsonConfig.mapper()` (BugSearch.cs:108,181,183,186,197-205).

G4. **Serializing `DBNull` / nested `DataTable` values in a plain `Map`** (R1.G4, R2.G3). §2A JSON covers DataTable only for Web API `BugQueryResult.Data`; Json.NET writes DBNull as `null` and a DataTable as an array of row objects (BugSearch.cs:183-186). Proposed: "`JsonConfig.mapper()` registers `DBNull` → `null` and `DataTable` → `DataTableSerializer` in every context"; the TODO at BugSearch.java:177 could then go.

G5. **J-C11 fractional-seconds TODO: site marker or not** (R1.G5, R2.G5). NEST/Json.NET parses `bg_reported_date` to `DateTime` (`ToString()` → whole seconds); the port reads a String. Rulebook should say whether the rulebook-level TODO needs a site marker at BugSearch.java:101; check `search.aspx`/`search_text.aspx` rendering before closing.

G6. **NEST conditionless queries** (R1.G6, R2.G1). NEST 1.x omits a `QueryString` with null/empty `Query` (match_all); the §2A body always sends it. Unreachable today (see R2.F1 above). Proposed: either record as accepted, or add "if `Strings.IsNullOrEmpty(searchText)` omit `query`; `TODO(port)` verify for NEST 1.0.2", plus a judge expectation for empty `Query`.

G7. **ES error responses on search** (R2.G2). §3 says HTTP failure → `DotNetException`; NEST 1.0.2 default returns `IsValid=false` with empty `Hits` on 4xx/5xx. Runtime-author decision: give `Search` the same `TODO(port)` verify treatment as `Index`.

G8. **Lazy `Where` → eager `collect` exception ordering** (R1.G7). C1 materialization can reorder which exception surfaces first; both end in 500 and ids are numeric. Record as accepted.

G9. **Pipeline prompt vs §6.7 trailer** (from R1.F1/R2.F3 context, pilot-notes.md). The P1 pipeline prompt produced the `PORT-STATUS` trailer; confirm the corrected prompt matches §6.7 before the next batch.

## Rulebook gaps — src__BugTracker.Web__btnet__util.cs

G1. **TimeSpan component members** (R1.G1). Add: `ts.Days`→`toDays()`, `.Hours`→`toHoursPart()`, `.Minutes`→`toMinutesPart()`, `.Seconds`→`toSecondsPart()`, `new TimeSpan(h,m,s)`→`Duration.ofHours(h).plusMinutes(m).plusSeconds(s)`. Evidence: util.cs:683-739. The current translation is correct, but an agent could pick `toHours()`.

G2. **Length-changing case mapping** (R1.G2, R1.F4). §2A:162 `toUpperCase/toLowerCase(Locale.US)` changes string length for `ß`, ligatures and `İ`; .NET does not. Proposed: runtime `Strings.ToUpper/ToLower` mapping per `char`, mandated at least where the result feeds `IndexOf`/`Substring` arithmetic. Evidence: util.cs:443, 448 (Util.java:479, 484); also util.cs:549, 1011. After the amendment, regenerate the affected sites.

G3. **tinyint vs smallint identity, and the unsigned types** (R1.G3, R2.G1, R2.G2). §2A:347 maps tinyint and smallint both to `Short`, so `typeof(System.Byte)` and `typeof(Int16)` tests cannot be reproduced. Options: (a) `DataColumn.DataType()` returns a runtime marker class for tinyint while values stay `Short`; or (b) declare the divergence accepted, with mandated marker text. Also: `typeof(UInt16/32/64)` has no mapping. Proposed: drop these terms with a fixed comment and no TODO (SqlClient has no unsigned column types). Evidence: util.cs:577-596; C4 is pending this gap.

G4. **`decimal.ToString(IFormatProvider)` with no format** (R1.G4). Proposed: `d.ToString()` / `d.ToString(culture)` on `decimal` → `Convert.ToString(d)`. Evidence: util.cs:327, 377.

G5. **Runtime members used but not listed in §3** (R1.G5, R2.G5). The members: `Strings.Split(String, int options, String... separators)`, `CultureInfo.NumberFormat().NumberDecimalSeparator()`, `HttpResponse.CacheControl(String)`, `Expires(int)`, `ContentType(String)`, `ContentEncoding(Encoding)`, `Clear()`, `BinaryWrite(byte[])`, and `Encoding.GetPreamble()`. Specify .NET semantics: Split keeps empties, the preamble is `EF BB BF`, the en-US separator is `"."`. The implementer marked some of these with TODOs and not others, which shows the inconsistency. Evidence: util.cs:119-124, 167, 1165-1173; judge S7:25.

G6. **Local `const`** (R1.G6). Add: method-local `const T x = v` → `final T x = v`. Evidence: util.cs:750.

G7. **`o.GetType() == typeof(T)`** (R1.G7). Add: `o.getClass() == T.class`. `typeof(System.DBNull)` → `o == DBNull.Value` is acceptable only where a later dereference preserves the NRE on null. Evidence: util.cs:276, 282.

G8. **`AppDomain.CurrentDomain.SetupInformation.ConfigurationFile`** (R2.G3). §2A:461 decides `Util.GetAbsolutePath` (C7), but it does not map the expression itself. Clarify: `Path.GetDirectoryName(AppDomain.CurrentDomain.SetupInformation.ConfigurationFile)` → `AppPaths.MapPath(null)`. Evidence: util.cs:607.

G9. **Source comments containing `TODO:`/`FIXME`** (R2.G4). §6.2 (copy comments) and §6.3 (no other TODO styles) conflict. Proposed: copy the text and rewrite the keyword as `(source TODO)` (the form applied in C3). Evidence: util.cs:81.

G10. **`HttpRequest.Url()` type and `Uri.ToString()`** (R2.G6). .NET returns the unescaped canonical form; `java.net.URI.toString()` keeps escapes. Proposed: `Url()` returns a runtime `Uri` with .NET `ToString()` semantics. Evidence: util.cs:96.

G11. **§6.5 citation format** (R1 obedience #3). The inventory has no numeric id column, so "inventory row ids" is undefined. Proposed: cite `inventory <file>:L<line> <CLASSIFICATION>` (the form used in C5).

## Rulebook gaps — src__BugTracker.Web__edit_bug.aspx

- RG1 (R1.G1, R2.G1): a code region nested inside a `__condN`/`__itemsN` region (edit_bug.aspx:195 inside region 12, :191-203). J-U2a covers only nesting inside a `__renderN`, and J-U3 numbers only top-level regions. The implementer invented `__render12_1` with a TODO(port) (java:2096-2101, html:171). Proposed rule: emit it as `__render<N>_<k>()` (N = the enclosing region, k = 1-based order inside it), apply the same element/non-element rules as a top-level region (C8 for element content), call it as `${page.__render<N>_<k>()}`, and leave the top-level counter unchanged.
- RG2 (R1.G2, R2.G2): a declared `style` on a server control whose `Style` code changes (edit_bug.aspx:122, .cs:1324). B5's "literal HTML copied unchanged (including style)" conflicts with "`Style` → `style` … markup values are the field initializer's", and the template could end up with two style attributes. §3 names no fluent Style setter. Proposed rule: parse the declared `style` into `Style()` in the field initializer through a runtime fluent `Style(String name, String value)` (add it to §3), or sanction an instance-initializer/constructor form explicitly. The template drops the literal `style`, and only `bt:control` renders it.
- RG3 (R1.G3, R2.G5): for a whole-attribute start-tag region that `<%: %>` HTML-encodes (edit_bug.aspx:22), B5(c) does not say whether "the value" is the source literal's evident intent or the browser-decoded value of the encoded output. Proposed: it is the browser-decoded value of what the original emitted (apply HtmlEncode for `<%:`, then HTML attribute parsing), with `BUG(port)` when that differs from the evident intent. C2 applies this reading.
- RG4 (R1.G4): `<%= expr %>`/`<%: expr %>` in element content where `expr` is not a String field or no-arg page method (e.g. `btnet.Util.get_setting(...)` at L82, L195). Today only elimination places these in `__renderN`. Proposed: state explicitly that such a region is a `__renderN` writing `Strings.cs(expr)`, or `HttpUtility.HtmlEncode(expr)` for `<%:`.
- RG5 (R1.G5, R2.G4): §5 says the inventory decides which regions become `__renderN`/`__condN`, but for edit_bug.aspx it has only the 7 TEXT_REGION rows (656-662). Regions 1, 7, 9-16 and the nested :35/:195 have no rows. Proposed: add one inventory row per region (number, kind, host), or amend §5 to say that element-content regions are numbered by J-U3 alone.
- RG6 (R2.G3): the return type of runtime control setters used fluently in field initializers (`.Value("Update")`, `.AutoPostBack(true)` at java:73, 84, 111, 112, 129). §2A describes setters as `void X(T)`, and that conflicts with B1's chained example `control(new CheckBox("x")).Text("Admin")`. Proposed: runtime setters return `this` with the concrete type (self-bounded generics), or B1 prescribes a non-chained form.
- RG7 (R2.G6, and the template part of R1.F5): §3 gives template marker forms only for TODO. Proposed: add `<!-- FIXED(port): F<n> — see RULEBOOK §0.1 -->` and `<!-- BUG(port): <defect> — repro: <input> -->`, with any explanation on a separate comment.

## Rulebook gaps — src__btnet_service__POP3Main.cs

- G1 `System.Timers.Timer.Interval` validation (POP3Main.cs:311). .NET throws `ArgumentException` for an interval ≤ 0 (or above Int32.MaxValue). Java `schedule(task, 0)` runs at once, which gives a tight re-fetch loop when `FetchIntervalInMinutes=0`. Proposed addition to the C17 row: `if (interval <= 0) throw new ArgumentException("Invalid value '<v>' for parameter 'interval'.")`, or a runtime `Timers.scheduleOnce`. (R1.G1, R2.G5)
- G2 `ServicePointManager.CertificatePolicy` / `ICertificatePolicy` / `ServicePoint` / `WebRequest` (POP3Main.cs:81, 777-792). There is no §2A or §3 entry. Proposed: a runtime `btnet.runtime.net.ServicePointManager.CertificatePolicy(ICertificatePolicy)` that is static and sticky like C5. HttpClient construction then gets a trust-all `SSLContext` from it while it is set. Add stub types for `ICertificatePolicy`, `ServicePoint` and `WebRequest`. Also decide whether POP3 SSL is affected. R2 notes that OpenPop's `SslStream` does not consult `ServicePointManager`, so it should keep default trust (TODO verify). (R1.G2, R1.G4, R2.G2)
- G3 `PostAsync(string)` request-URI semantics (POP3Main.cs:704, 722). For a null, empty or relative URI, .NET throws `InvalidOperationException` synchronously. Proposed: a runtime helper (for example `HttpRequests.uri(String)`) that reproduces that exception, and add `InvalidOperationException` to the §3 exception list. Related: the C33 row should state that `firstAwait.run()` goes after any synchronous argument validation of the awaited call. (R1.G9, R2.G3)
- G4 Missing §3 members: `ElapsedEventArgs` (extends `EventArgs`), `StringComparison` (implied by the existing `Strings.IndexOf(s, v, StringComparison)` overload), the `pop.Pop3Client` members (`Connect`, `Authenticate`, `GetMessageUids`, `GetMessage`, `DeleteMessage`, `Disconnect`, close/dispose behavior), and the `pop.Message` accessors. (R1.G3, R1.G4, R2.G6)
- G5 `Console.WriteLine(object)` (POP3Main.cs:173). There is no rule, and `println` ends lines with `\n` where .NET writes `\r\n`. Proposed: a runtime `Console.WriteLine(Object)` = `System.out.print(Convert.ToString(o) + Environment.NewLine)`. (R1.G5, R2.G1)
- G6 HttpClient defaults in the J-C2 row. On redirect policy, .NET `AllowAutoRedirect` follows https→http, `Redirect.NORMAL` does not, and `ALWAYS` does. On timeout and version, .NET 4.5 uses 100 s and HTTP/1.1, while Java has no timeout and tries HTTP/2. Proposed: decide the redirect policy, and add `.version(HTTP_1_1)` plus a 100 s per-request timeout that maps to a runtime `TaskCanceledException`. (R1.G6, R2.G4)
- G7 Service `ConnectionString` → JDBC (POP3Main.cs:263, 768). `port/service` has no Spring DataSource. Proposed: either the service config carries a `jdbc:` URL, or the runtime `SqlConnection` converts .NET connection strings to mssql-jdbc URLs. (R1.G7, R2.G9)
- G8 Inventory/rule conflicts to correct (§5): rows L86/L91 (hand-expansion vs J-C25 (a)), L707 (`Encoding.Default()` vs the J-C2 field form), L403 (`XmlNodeType` package `btnet.runtime` vs §3 `btnet.runtime.io`), and L306 (`60L` vs the §2A int-wrap rule, see C7). Also, §2A rule (a) cites `POP3Main.cs:85-86,90-91`, but the actual lines are 86-87 and 91-92. (R1.G8, R2.G7)
- G9 The §6.1 header hard-codes `src/BugTracker.Web/<path>`. Service units need `src/btnet_service/<file>`, so the rule should say "source path". (R2.G8)
- G10 §6.5 asks for "inventory row ids", but `inventory.tsv` has no id column. Define the citation form, for example `inv:<file>:L<line>`. (R2.G10)

