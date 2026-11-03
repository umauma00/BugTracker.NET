# Bakeoff round 1: diff inspection (rulebook stress test)

Date: 2026-11-03. Inspector: diff inspector (read-only; this file is the only output).

| Input | Path |
|---|---|
| Sources | `src/BugTracker.Web/btnet/util.cs`, `src/BugTracker.Web/Search/BugSearch.cs`, `src/btnet_service/POP3Main.cs` |
| A (rulebook to the letter) | `st-A/port/web/src/main/java/btnet/Util.java`, `…/btnet/Search/BugSearch.java`, `st-A/port/service/src/main/java/btnet/POP3Main.java` |
| B (no rulebook, idiomatic Spring) | `st-B/out/Util.java`, `BugSearch.java`, `POP3Main.java` |
| Rules | `migration/RULEBOOK.md`, `migration/inventory.tsv` |

`st-A`/`st-B` are under `/tmp/claude-0/-home-user/9c6707b8-af5b-57fb-b9d4-be71e6af9275/scratchpad/`.

**Method.**
- **Formatting ignored.** No Java formatter is available in this environment, so I ignored pure formatting, whitespace, brace style and naming style (B's camelCase) by hand.
- **Posture-only differences are listed separately.** Some differences only reflect B's freedom to redesign (Spring beans, `List<Map>`, records, dropped interfaces, renamed members). They indict nothing and are not counted.
- **Behavior is judged against the C#.** The question for every row is who reproduces what the original actually does.
- **JDK checks.** A few JDK facts were checked with `jshell` (JDK 21): the CLDR pattern, the `Double` and `windows-1252` behavior, regex `$`, and `strip()`. No port code was compiled.

A followed the rules carefully: all 13 `RULEBOOK-SILENT` flags are real gaps, and its TODO counts match its trailers (9/3/7). The defects below are mostly in the rulebook itself, or in things neither the rulebook nor the inventory noticed in the source.

## Summary

| Verdict | Rows |
|---|---|
| rulebook right | 24 |
| native right | 3 (D39, D47, D48) |
| both defensible | 17 |
| **both wrong** | **7** (D08, D24, D35, D40, D43, D44, D49) |
| **Total substantive rows** | **51** (+ 11 posture-only items, not counted) |

**Headline findings.**
1. **The original POP3 service never posts a bug (D38).** `LoginUrl` is missing from the key list in `get_settings` (`POP3Main.cs:374-389`), so `LoginUrl` is always null. `PostAsync(null, …)` then throws synchronously, every message is counted as an error, and the service stops after `TotalErrorsAllowed` (100 in `btnet_service.exe.config`).
   - Neither the rulebook nor the inventory noticed this.
   - A reproduces it by accident (an NPE, with no `BUG(port)` marker).
   - B silently fixes it.
2. **J-C1's async release point is wrong (D39).** Because of item 1, the original's first `await` never yields, so `fetch_messages` runs fully synchronously. A releases the caller *before* the synchronous throw. That creates cross-website and cross-project concurrency the original never had. B's sequential design is the faithful one here.
3. **The `Convert.ToString(Double)` rule is wrong (D24).** .NET Framework 4.5 prints `G15`, not shortest round-trip. A and B are both wrong, in different ways. This reaches every grid cell that prints a `float` column, across the whole fan-out.
4. **tinyint is reported as numeric (D08).** The `tinyint → Short` value mapping makes `is_numeric_datatype` return true where .NET's `System.Byte` returns false. B does the same through JDBC. This replicates to every `DataType` comparison.
5. **The service's database rules are web-centric (D47, D48).** Per-website connection strings are ignored, and the source's connection leak becomes pool exhaustion under Hikari. B is right here.

## Difference table

Line numbers: `A:` / `B:` are lines of the respective Java file. `cs:` is the C# source.

### util.cs → Util

| # | Area | A | B | Rule / gap | Verdict and evidence |
|---|---|---|---|---|---|
| D01 | Culture | A:306 `return CultureInfo.EN_US;` | B:261 `return LocaleContextHolder.getLocale();` | §2A Dates/culture | **rulebook right.** cs:269 uses `Thread.CurrentCulture`. Web.config has no `<globalization culture="auto">`, so ASP.NET uses the server culture, never Accept-Language. In B every format, parse and `ToUpper` varies per request (B:563 `capitalizeFirstLetter`: Turkish `i`→`İ`). |
| D02 | `"g"` default date format | A:354 `DotNetFormat.Format(dt, date_time_format, get_culture_info())` | B:266-268 CLDR `FormatStyle.SHORT` pattern | §2A DotNetFormat | **rulebook right.** JDK 21 en-US SHORT is `M/d/yy, h:mm a` (jshell), so B prints `1/2/2020, 3:04 PM`. .NET `g` prints `1/2/2020 3:04 AM` with no comma (cs:304-317). |
| D03 | .NET escapes in custom formats | A:407 `DotNetFormat.Format(d, get_setting("SQLServerDateFormat", …), CultureInfo.EN_US)` | B:327 `DateTimeFormatter.ofPattern(getSetting("SQLServerDateFormat", …), Locale.ROOT)` | §2A DotNetFormat (`\` listed) | **rulebook right.** Web.config:240 is `yyyyMMdd HH\:mm\:ss`. java.time prints the backslashes: `20200102 03\:04\:05` (jshell). In B, `request_to_string_for_sql` (cs:1086) therefore emits an unparseable SQL literal for every custom datetime field. |
| D04 | `decimal.ToString(culture)` | A:366 `DotNetFormat.Format(val, "G", get_culture_info())` | B:313 `bd.toPlainString().replace('.', sep)` | gap (SILENT F2) | **both defensible.** .NET decimal `G` without a precision is fixed-point and keeps trailing zeros, which equals `toPlainString` in en-US (cs:327). Pin the rule (amendment 15). |
| D05 | `decimal.Parse` → invariant | A:414-418 `Decimal.Parse(val, …)` … `Format(x, "G", CultureInfo.InvariantCulture)` | B:333-335 regex `tryParseDecimal`, `NumberFormatException` | §2A Conversions | **both defensible.** Same output for real input. B rejects `NumberStyles.Number`'s trailing sign (`5-`) and throws a different exception type (cs:375 `FormatException`). |
| D06 | `DateTime.Parse(s, culture)` | A:229,399 `DateTimes.Parse(…)` | B:367-391 ISO-`T` plus CLDR SHORT/MEDIUM patterns | §2A DateTimes (pattern list unpinned) | **rulebook right.** B rejects `2020-01-02` (the datepicker `yy-mm-dd` at Web.config:216, and JustDateFormat at :230) and `2020-01-02 15:04`. .NET accepts both, so in B every datepicker date fails `is_datetime`. The rule's own pattern list is unpinned (amendment 8). |
| D07 | `Int32.Parse` family | A:180 `Int32.Parse(maybe_int)`; POP3 A:722 | B:149,191 `Integer.parseInt(x.strip())`; POP3 B:355 | §2A Int32.Parse | **rulebook right (edge).** Java accepts non-ASCII Unicode digits, and `strip()` removes all Unicode whitespace. .NET `NumberStyles.Integer` accepts ASCII digits and only U+0009–000D/U+0020. `sanitize_integer` (cs:557) is an injection guard; B lets `١٢٣` through it. |
| D08 | tinyint "numeric" | A:627-635 `datatype == Short.class` (tinyint→Short per §2A) | B:200-206 `type == Short.class` (JDBC tinyint→Short) | §2A DataSet value-type map | **BOTH WRONG.** cs:580-588 excludes `System.Byte`, so a tinyint column is non-numeric in .NET: left-aligned and string-sorted (`bug_list.cs:784`, `sortable_html_table.cs:134,218`, `print_bugs.aspx.cs:136`). Both ports call it numeric. It is reachable through saved-query SQL (setup.sql has no tinyint), but the map is global (amendment 4). |
| D09 | `strip_html` | A:251 `"<[^>]*>"` (C25) | B:215 `replaceAll("(?s)<.*?>", "")`, `HtmlUtils.htmlUnescape` | §2A C25 | **both defensible.** The patterns are equivalent, and the entity decoders differ only on exotic entities. |
| D10 | `strip_dangerous_tags` case folding | A:264 `DotNetRegex.Replace(…, RegexOptions.IgnoreCase)` | B:239 `Pattern.CASE_INSENSITIVE` (ASCII only) | §2A Regex | **both defensible.** They differ only on non-ASCII case pairs (U+212A, U+017F). Both keep the `onSAFEresetK` typo. |
| D11 | `GenerateRandomString` | A:575-584 `java.util.Random`; bound re-drawn each pass; `BUG(port)` | B:525-532 `SecureRandom`, fixed alphabet and length | §0.1 (no fixes) | **both defensible.** Salts are not observable. B's unsanctioned fix breaks §0.1 in letter only. |
| D12 | `HashString` | A:537 runtime `Rfc2898DeriveBytes` | B:493-496 `PBKDF2WithHmacSHA1` | B7 | **both defensible.** Same algorithm. Both still depend on the setup.sql:168 acceptance vector (UTF-8 replacement granularity). |
| D13 | `Trim` | A:705 `Strings.Trim(array[i])` | B:576 `array[i].strip()` | §2A Trim | **rulebook right.** `strip()` keeps U+00A0 (jshell); .NET `Trim` removes it (cs:657). |
| D14 | `validate_email` `$` | A:77 `reEmail.IsMatch(s)` | B:143 `RE_EMAIL.matcher(s).find()` | §2A Regex (UNIX_LINES) | **rulebook right (edge).** Java `$` matches before a final `\r\n`, so `a@b.com\r\n` → true (jshell). .NET matches only before `\n` → false. |
| D15 | `how_long_ago` components | A:741 `ts.toDaysPart()` | B:595 `ts.toDays()` | gap (SILENT F6/F7) | **both defensible.** Both equal `TimeSpan.Days` here. |
| D16 | `filename_to_content_type` | A:1059 `Path.GetExtension(filename).toLowerCase(Locale.US)` | B:615-617 its own extension parse | §2A Path | **both defensible.** |
| D17 | `request_to_string_for_sql` | A:1118-1140 if-chain | B:639,645 `switch (datatype)` | §2A switch-on-string (null → default) | **rulebook right (edge).** cs:1070-1092 sends a null datatype to `null`/`N'…'`; B's switch throws an NPE. |
| D18 | `GetAbsolutePath` base | A:656 `AppPaths.MapPath(path)` | B:667 `btnet.app-root` (default `user.dir`) | gap (SILENT F5) | **both defensible.** |
| D19 | `get_folder` message | A:671 `" specified in Web.config, \""` … `"Edit Web.config."` | B:678 `" specified in application config, \""` … `"Edit the configuration."` | §6.2 messages identical | **rulebook right.** cs:623-625. |
| D20 | `IsLocalUrl` | A:1151 runtime `UrlHelper` (MVC 5.2.3, N2) | B:701-712 also rejects control characters and `~//` | §2A N2 | **rulebook right.** §3 reproduces the MVC 5.2.3 code; B implements ASP.NET Core semantics. |
| D21 | `redirect` reads `url`/`qs` | A:1148 `Request.QueryString().get("url")` | B:728 `request.getParameter("url")` | §2A QueryString | **rulebook right.** cs:1100 reads the query string only and comma-joins repeats. `getParameter` merges the POST body and returns the first value. |
| D22 | `UrlDecode` | A:1166 `HttpUtility.UrlDecode(qs)` | B:735 `URLDecoder.decode(qs, UTF_8)` | §2A UrlDecode | **rulebook right.** `qs=%zz` throws `IllegalArgumentException` (500) in B; .NET keeps it literally and redirects. |
| D23 | bool in CSV | A:1278 `Convert.ToString(drv.get(col))` | B:824 `v.toString()` | §2A Convert.ToString | **rulebook right.** cs:1226 prints `True`; B prints `true`. |
| D24 | double in CSV and grids | A:1278 `Convert.ToString(…)` (= §2A "R" shortest) | B:824 `v.toString()` | §2A Convert table, Double row | **BOTH WRONG.** .NET Framework 4.5 `Double.ToString()` = `G` = 15 significant digits. .NET prints `1.0/3` → `0.333333333333333`, `5.0` → `5`, `1e7` → `10000000`. A prints `0.3333333333333333`; B prints `0.3333333333333333`, `5.0`, `1.0E7` (jshell). The same rule drives `bug_list.cs:795 dr[i].ToString()` and every grid (amendment 3). |
| D25 | `Response.Clear` | A:1214 `Response.Clear()` | B:791 `response.reset()` | §2B B3 | **rulebook right (minor).** `Clear` keeps headers; `reset()` also drops headers and status. |
| D26 | related-users SQL | A:818-932 verbatim text, runtime `Fill` | B:864 `"set nocount on\n"` + text blocks + `:name` parameters | §2A @verbatim / SqlBatch | **both defensible.** The runtime's `Fill` skips update counts; B reaches the same first result set through NOCOUNT. |
| D27 | parameter types | A:553 `AddParameterWithValue("id", Convert.ToString(us_id))` | B:507 `.param("id", usId)` | §2A SqlBatch typing | **both defensible.** nvarchar vs int; implicit conversion returns the same rows (also BugSearch A:163 / B:152). |
| D28 | memory-log URL | A:127 `Strings.ToStringStrict(HttpContext.Current().Request().Url())` | B:118 `request.getRequestURL()` | §2A intrinsics | **rulebook right (minor).** `Request.Url` includes the query string; MemoryLogEnabled=1 (Web.config:791). |
| D29 | `Expires` header | A:155 `Response.Expires(-1)` | B:135 `setDateHeader("Expires", 0)` | B3 | **both defensible.** |
| D30 | settings key lookup | A:163 case-insensitive `NameValueCollection` | B:96 `env.getProperty(SETTINGS_PREFIX + name)` (case-sensitive) | §2A Settings | **both defensible.** Checked: no `get_setting` key in the code differs in case from an uncommented Web.config key. |

### BugSearch.cs → BugSearch

| # | Area | A | B | Rule / gap | Verdict and evidence |
|---|---|---|---|---|---|
| D31 | ES endpoint and client | A:85-87 `_client.Search("btnet", body)` → `POST /btnet/bug/_search` | B:40,83 `INDEX = "bug"`, co.elastic `ElasticsearchClient` | §1 Search, §3 | **rulebook right.** BugSearchFactory.cs:19-22 sets `defaultIndex: "btnet"`, type `bug` (cs:55,164), on ES 1.x. B's client and index cannot reach the existing index. |
| D32 | `desc`/`status` | A:105,114 `Strings.ToStringStrict(bug.get("desc"))` | B:111,115 `String.valueOf(bug.get("desc"))` | §2A J-C17 | **rulebook right.** cs:78,87 throw an NRE on a missing field (search returns 500); B renders `"null"`. |
| D33 | date into a DateTime column | A:113 `resultRow.set(ResultColumns.Date, Strings.ToStringStrict(…))` | B:207-213 `parseDate` → `null` on failure | §2A J-C11 | **rulebook right.** cs:86: an unparseable value throws `ArgumentException` in .NET; B swallows it. Both keep fractional seconds (known rule TODO). |
| D34 | score | A:115 `Convert.ToDecimal(hit.Score)` | B:116 `BigDecimal.valueOf(hit.score())` | §2A J-C11 | **rulebook right (minor).** 15 vs 17 significant digits. |
| D35 | `IndexBug` for a missing bug | A:165/191: NPE in `ToJson`, catch logs `e.getMessage()` | B:154-156 early `return`, logs `"bug {} not found"` | §2A Exceptions (`e.Message → getMessage()`) | **BOTH WRONG (log text).** cs:137-150: the posts query still runs, then an NRE logs "…Object reference not set to an instance of an object." A gets the control flow right but logs JDK helpful-NPE text. B skips the query and logs a new message. The same gap reaches pages that print `ex.Message` (amendment 7). |
| D36 | index JSON | A:207-215 `JsonConfig.mapper().writeValueAsString(bugDictionary)` (values: DBNull, LocalDateTime, DataTable) | B:189-205 `normalize` Timestamp → ISO; the client serializes | gap: `JsonConvert.SerializeObject` outside Web API | **both defensible.** The rule should pin DBNull→null and DataTable→rows here (amendment 12). |
| D37 | search failure | A: runtime throws `DotNetException` | B:91-92 `UncheckedIOException` | §3 ElasticClient | **both defensible (open).** NEST 1.0.2's default (no ThrowExceptions) may return an invalid response with empty Hits instead. If so, both are wrong (amendment 13). |

### POP3Main.cs → POP3Main

| # | Area | A | B | Rule / gap | Verdict and evidence |
|---|---|---|---|---|---|
| D38 | `LoginUrl` never loaded | A:320 `LoginUrl = settings.get("LoginUrl");` → A:750 `URI.create(LoginUrl)` (NPE) | B:109-111 "the old get_settings never loaded LoginUrl … Loaded here" | §0.1 (no fixes); §5 (no inventory row) | **rulebook right (outcome).** cs:374-389 and :406-420 omit `LoginUrl`, although exe.config:86 sets it. `settings["LoginUrl"]` is null. `HttpClient.PostAsync(null, …)` throws `InvalidOperationException` synchronously (`PrepareRequestMessage`), which is caught at :732 and counted. No bug is ever posted, no DELE is sent, and the service stops after `TotalErrorsAllowed`. A reproduces this by accident and without a `BUG(port)` marker; B silently fixes it. |
| D39 | async release point | A:746 `firstAwait.run();` then `httpClient.send(HttpRequest.newBuilder(URI.create(LoginUrl))…` | B:425-455 sequential `postToWebApp` | §2A async row (C33/J-C1) | **NATIVE RIGHT.** Because of D38 the original's first `await` never yields: the throw happens before any Task exists, so the whole async-void body runs synchronously on do_work's thread. A releases the caller before the throw. `fetch_messages_for_projects` and `do_work` then move on and reassign the shared fields (cs:261-282: filters, `InsertBugUrl`, `TrackingIdString`, `MessageOutputFile`) while the body still reads them. `total_error_count++` races, and `resume()` re-arms the timer during work. The release must come after the request is built (amendment 1). |
| D40 | unhandled exception in async void | A: runAsync's uncaught handler logs (A:613,631,636 unguarded) | B: null-safe, or caught by `catch (Exception)` | §3 `Threads.runAsync` (runtime TODO) | **BOTH WRONG.** cs:566 `Convert.ToInt32(uid)`, :584 `WriteAllBytes`, :589 `From.Address` and :599/:659 `subject.IndexOf` sit outside any try in an async void. .NET 4.5 rethrows on the thread pool, so the process terminates. Both ports keep running (amendment 6, human decision). |
| D41 | UID used as message number | A:587/613/618 `GetMessageUids()` → `Convert.ToInt32(…)` → `GetMessage(n)` | B:312 `inbox.getMessages()` | §2A OpenPop row | **rulebook right.** cs:540,566,571. A non-numeric UIDL crashes (D40); a numeric one fetches a wrong or missing message. A has no `BUG(port)` marker, and there is no inventory row. |
| D42 | null `From`/`Subject` | A:636 `mimeMessage.Headers().From().Address()` | B:332-334 `nz(…)` → `""` | gap: runtime `pop.Message` nullness | **rulebook right (B wrong).** OpenPop's `From` is null when the header is absent, so cs:589 throws an NRE (→ D40). B processes the message. |
| D43 | accept-all certificates | A:108-110 `new AcceptAllCertificatePolicy();` (effect not applied, TODO) | B:89-90,432-436 opt-in `trustAllCertificates`, default false | gap (SILENT F10/F13) | **BOTH WRONG (latent behind D38).** cs:81 sets `ServicePointManager.CertificatePolicy`. .NET 4.5's `HttpClientHandler` is built on HttpWebRequest, so HTTPS `LoginUrl`/`InsertBugUrl` accept any certificate and host name. OpenPop's SslStream is unaffected (POP3 TLS validation matches in all three). |
| D44 | HTTP timeout | A:739-753 none (infinite) | B:431,464 connect 30 s, request 2 min | gap | **BOTH WRONG (latent).** The .NET `HttpClient.Timeout` default is 100 s; it raises `TaskCanceledException`, caught at :732 and counted. A can hang until the watchdog fires (which itself only kills an idle thread). |
| D45 | form encoding | A:745,775 `FormUrlEncodedContent.encode(…)` | B:459-462 `URLEncoder.encode` | J-C2 | **both defensible (latent).** Decoded values are equal. B drops the .NET `EscapeDataString` length limit that A's TODO keeps. |
| D46 | `Encoding.Default` | A:762 `Encoding.Default.GetString(…)` | B:142,366 `Charset.forName("windows-1252")` | J-C2 | **rulebook right (edge, latent).** Java decodes 0x81 to U+FFFD (jshell); .NET decodes it to U+0081. |
| D47 | per-website connection string | A:835 `new SqlConnection(ConnectionString)`; the runtime ignores non-`jdbc:` strings and uses the Spring DataSource | B:269 `new DriverManagerDataSource(site.connectionString())` | §2A Database (`GetConnection` paragraph) | **NATIVE RIGHT.** cs:263,768 use each `<Website>`'s own string (exe.config:93 `server=(localdb)\v11.0;database=btnet;Trusted_Connection=yes;`). The rule sends every website to one DataSource, and `port/service` has no Spring DataSource. B requires the config to be converted to JDBC URLs (documented) (amendment 5). |
| D48 | connection leak | A:835-839 connection never closed (faithful) | B: JdbcClient closes it | §2A Database | **NATIVE RIGHT.** cs:767-772 leaks one connection per website per cycle. SqlClient (Max Pool Size 100) reclaims connections whose owning object was garbage-collected. Hikari (default max 10) never does, so A's port exhausts the pool after about 10 cycles (≈2.5 h) and then logs "Error trying to process messages" every cycle. |
| D49 | fetch interval arithmetic | A:357 `60L * 1000 * FetchIntervalInMinutes` | B:257 `TimeUnit.MINUTES.toSeconds(…)` | §2A ("overflow wraps"); C17 | **BOTH WRONG (edge).** cs:311 uses int arithmetic, which wraps above 35 791 min. `Interval = 0` throws `ArgumentException`: the timer is never re-armed and the thread dies. Both ports instead busy-loop on 0. Inventory row L306 (`60L`) contradicts §2A. |
| D50 | DBNull POP3 username | A:508 `(String) dr.get("pj_pop3_username")` | B:274-275 `rs.getString(…)` | §2A casts | **rulebook right.** cs:463: `InvalidCastException` → catch at :468 returns and skips the remaining projects. B passes null and continues. |
| D51 | DELE | A:794 `client.DeleteMessage(message_number)` | B:369,392 `setFlag(DELETED)` + `close(expunge)` | runtime pop | **both defensible (latent).** At the POP3 level this is the same: DELE is committed at QUIT. |

### Posture-only differences (no indictment; structure-preserving was signed off)

These were not counted. In each case A is correct under the posture.

- B uses Spring `@Service`/`@Component` beans and constructor injection, and drops `IBugSearch`.
- B uses `List<Map>`/records instead of DataSet/DataTable, and `null` instead of DBNull (this also changes `format_db_date_and_time`'s null path).
- B renames members to camelCase and drops `context`/`Request`/`dummy`/`reCommas`/`rePipes`.
- B's `Response.Redirect`/`End` no longer end the request, so callers must return. Routes become `bugs`/`mbugs` instead of `bugs.aspx`.
- The `Application["custom_columns_dataset"]` cache becomes a bean field with an explicit `invalidate`. Other units null the Application key, `add_customfield.aspx.cs:285`.
- The service config moves to `@ConfigurationProperties`, losing the per-cycle re-read at cs:249.
- The service logs through SLF4J instead of the daily `btnet_service_log_*.txt` file plus Console.
- `ScheduledThreadPoolExecutor` (daemon) plus a generation counter replaces Thread/Abort/Timer. Note: the original's watchdog aborts a thread that only sleeps, because do_work runs on timer threads (cs:308); A reproduces that.
- B removes dead code: `maybe_append_next_line`, `ReadInputStreamCharByChar`, the duplicate `onmouseup`.
- B's SQL text blocks and named parameters change whitespace and parameter syntax only.
- B's initial `STOPPED` state plus `SmartLifecycle`.

## A's 13 RULEBOOK-SILENT flags: proposed rules

| F | Site | A's choice | Assessment | Proposed rule |
|---|---|---|---|---|
| F1 | Util A:199 `val.Split(new string[]{sep}, StringSplitOptions.None)`, `ci.NumberFormat.NumberDecimalSeparator` | `Strings.Split(val, StringSplitOptions.None, ci.NumberFormat().NumberDecimalSeparator())` | Correct guess. | `s.Split(string[] seps, opts)` → `Strings.Split(s, opts, String... seps)`: ordinal, longest-first .NET separator semantics, empties kept for `None`. The runtime provides both the `char...` and `String...` varargs forms. `ci.NumberFormat.X` → `ci.NumberFormat().X()` (runtime `NumberFormatInfo` with .NET property names; en-US `.`/`,`). |
| F2 | Util A:364 `val.ToString(get_culture_info())` (decimal) | `DotNetFormat.Format(val, "G", ci)` | Correct; semantics unpinned. | `decimal.ToString()` / `ToString(IFormatProvider)` → `DotNetFormat.Format(x, "G", culture)`. The runtime specifies decimal `G` without a precision as **fixed-point, scale preserved, never exponent** (`1.50m` → `"1.50"`), with the culture's decimal separator. |
| F3 | Util A:416 `CultureInfo.InvariantCulture` | `CultureInfo.InvariantCulture` | Correct; missing from §3. | Add `CultureInfo.InvariantCulture` to §3 (decimal `.`, group `,`, invariant date patterns). |
| F4 | Util A:625 `typeof(UInt32/UInt64/UInt16)` | `Long`/`BigInteger`/`Integer` | Harmless today but non-deterministic across agents. It also exposes D08. | Add runtime marker class tokens `btnet.runtime.ClrTypes.Byte, SByte, UInt16, UInt32, UInt64` (empty final classes). `typeof(System.X)` → `ClrTypes.X.class`. `DataColumn.DataType()` for **tinyint returns `ClrTypes.Byte.class`** (values stay `Short`), so `== typeof(byte)` comparisons and `is_numeric_datatype` match .NET. No SQL type yields the unsigned tokens. |
| F5 | Util A:654 `Path.GetDirectoryName(AppDomain.CurrentDomain.SetupInformation.ConfigurationFile)` | `AppPaths.MapPath(path)` (restructured) | Right result for plain paths, but changes the expression and treats `~`/`..` through MapPath. | Translate literally: `AppDomain.CurrentDomain.SetupInformation.ConfigurationFile` → `AppPaths.ConfigurationFile()` (= `<webroot>/Web.config`). Keep `Path.Combine(appRootPath, path)` unchanged. |
| F6 | Util A:731 `new TimeSpan(0,0,seconds)` | `Duration.ofSeconds(seconds)` | Correct. | `new TimeSpan(h, m, s)` → `Duration.ofHours(h).plusMinutes(m).plusSeconds(s)`; `new TimeSpan(d, h, m, s)` adds `ofDays(d)`. |
| F7 | Util A:739 `ts.Days/Hours/Minutes/Seconds` | `toDaysPart/toHoursPart/toMinutesPart/toSecondsPart` | Correct (sign follows the span; differs only for negative fractional spans). | `.Days/.Hours/.Minutes/.Seconds/.Milliseconds` → `(int) d.toDaysPart()` / `toHoursPart()` / `toMinutesPart()` / `toSecondsPart()` / `toMillisPart()`. `.TotalX` per C20. |
| F8 | BugSearch A:208 `foreach (DataColumn c in row.Table.Columns)`, `row[column]` | `bugRow.Table().Columns()` + `get(column.ColumnName())` | Equivalent (J-C12 makes names unique) but indirect. | `DataColumnCollection implements Iterable<DataColumn>`; `dr.Table()`; `dr.get(DataColumn)` overload. Translate `row[column]` → `row.get(column)`. |
| F9 | BugSearch A:226 `ds.Tables.Add()` | `result.Tables().Add()` | Correct. | `ds.Tables.Add()` → `ds.Tables().Add()`: returns a new `DataTable` named `Table`, `Table1`, … (.NET naming). |
| F10 | POP3 A:108 `ServicePointManager.CertificatePolicy = new AcceptAllCertificatePolicy()` | Construct it; effect not applied (TODO) | A's safe default diverges (D43). | Runtime `btnet.runtime.net.ServicePointManager.CertificatePolicy(ICertificatePolicy)`: static and process-wide. Every `java.net.http.HttpClient` in translated code is built through the runtime `HttpClients.newBuilder()`, which, when a policy is set, consults it (accept-all → trust-all `SSLContext` plus hostname verification off). It never affects `Pop3Client`. **Security: human decision first** (amendment 9). |
| F11 | POP3 A:204 `Console.WriteLine(o)` | inline `System.out.print(Convert.ToString(o) + Environment.NewLine)` | Behavior right; not 1:1. | Runtime `btnet.runtime.Console.WriteLine(Object|String)` = `Convert.ToString(o)` (Throwable → `dotNetToString`) + `Environment.NewLine` to stdout, UTF-8. |
| F12 | POP3 A:276 `ElapsedEventArgs eea` | typed `EventArgs` | Changes the member signature. | Add runtime `btnet.runtime.ElapsedEventArgs extends EventArgs` (`LocalDateTime SignalTime()`). The parameter keeps that type; the C17 TimerTask keeps calling `do_work(null, null)`. |
| F13 | POP3 A:843 `class AcceptAllCertificatePolicy : ICertificatePolicy` | interface dropped, parameters `Object` | Changes the type's shape. | Runtime `btnet.runtime.net.ICertificatePolicy { boolean CheckValidationResult(ServicePoint, java.security.cert.X509Certificate, WebRequest, int); }` with opaque `ServicePoint`/`WebRequest`. The second top-level type is nested as `static class AcceptAllCertificatePolicy implements ICertificatePolicy` (§4). |

**Gaps A did not flag.**
- The source defects in D38 and D41, and the `bugId = TrackingIdString` send at cs:718, carry no `BUG(port)` marker. Reviewers should catch this.
- Runtime omissions A marked only as TODO: `btnet.runtime.StringComparison` (A:646), `HttpResponse.ContentEncoding(Encoding)` (A:1218), `Encoding.GetPreamble()` (A:1223).
- Inventory rows A correctly overrode (rule rows win, §5) but that still need correcting:
  - row 958/959: hand-expanded thread vs J-C25 `Threads.start`
  - `Util.context` `volatile` vs C32 plain field
  - row 970: `new String(bytes, Encoding.Default())` vs the `Encoding.Default.GetString` rule

## Amendment queue (for the human; nothing applied)

Ordered by blast radius × severity. "Rule text" is the proposed replacement or addition.

1. **J-C1 / C33: where `firstAwait.run()` goes** (D39).
   - Rule text: "Build the request object (URI, headers, body) in a local first. Insert `firstAwait.run();` immediately before the `httpClient.send(req, …)` call itself, never before an expression that can throw synchronously. .NET throws `PrepareRequestMessage`/`Uri` errors before any Task exists, so the caller is not released: `var req = HttpRequest.newBuilder(URI.create(LoginUrl))…build(); firstAwait.run(); var loginResponse = httpClient.send(req, …);`."
   - Also: "`URI.create(null)` stands in for .NET's `InvalidOperationException`; mark the site."
   - Evidence: POP3Main.cs:704; .NET 4.5 `HttpClient.SendAsync` → `PrepareRequestMessage` throws synchronously when `RequestUri` and `BaseAddress` are both null.
2. **`LoginUrl` source defect** (D38). Add inventory row `POP3Main.cs L278 settings["LoginUrl"]` → `BUG(port): LoginUrl never loaded (missing from get_settings key list :374-389) — repro: any message passing the filters → error count +1, nothing posted`.
   - **Human decision:** keep (default, per §0) or sanction **F6** (add `"LoginUrl"` to the key list).
   - If F6 is sanctioned, amendments 1, 9, 10 and D45's length limit become live behavior.
3. **`Convert.ToString(Double/Single)`** (D24). Replace the Double clause with: "Double → .NET Framework 4.5 `G` = 15 significant digits (`R` only when the source says `"R"`); Single → `G` 7 digits; scientific notation only when the exponent is ≥ 15 (or < −5), formatted `E+XX`/`E-XX` with at least two exponent digits".
   - Applies to `o.ToString()`, `Convert.ToString`, `Strings.cs`, string concatenation and `Response.Write(Object)`.
   - Evidence: `0.1+0.2` → `"0.3"` on .NET Framework vs `"0.30000000000000004"` on .NET Core 3.0+; bug_list.cs:795 and sortable_html_table.cs.
4. **Type tokens for Byte and the unsigned types** (D08, F4). Use the rule text in F4. Also amend the §2A DataSet line: "tinyint → value `Short`, `DataColumn.DataType()` = `ClrTypes.Byte.class`".
   - Evidence: util.cs:580-588; bug_list.cs:784; sortable_html_table.cs:134,218; print_bugs.aspx.cs:136.
5. **Service database connections** (D47, D48). Rule text: "In `port/service`, runtime `new SqlConnection(String)` translates a SqlClient connection string (`server`/`data source`, `database`/`initial catalog`, `user id`, `password`, `Trusted_Connection`/`Integrated Security`) into a `jdbc:sqlserver:` URL and opens an **unpooled** connection per instance. A `java.lang.ref.Cleaner` closes connections that become unreachable unclosed, mirroring SqlClient's reclaim of collected owners. Never fall back to a shared DataSource in the service."
   - Evidence: POP3Main.cs:263,767-772; exe.config:93.
6. **Async-void unhandled exceptions** (D40). Human decision: either reproduce (the runtime's uncaught handler logs at FATAL, then `Runtime.getRuntime().halt(1)`, as the .NET 4.5 thread-pool rethrow terminates the process) or log a deviation D-00x "the port keeps running".
   - Either way, record it in the rulebook instead of the runtime TODO.
   - Evidence: POP3Main.cs:566,584,589,599,659.
7. **`e.Message` of a null reference** (D35). Rule text: "`e.Message` → `Exceptions.Message(e)`. It equals `e.getMessage()` except for `NullPointerException`, which gives `"Object reference not set to an instance of an object."`, and `ClassCastException`, which gives .NET's `"Specified cast is not valid."`. `dotNetToString` uses the same text."
   - Evidence: BugSearch.cs:156; this also prevents JDK helpful-NPE text in pages that write `ex.Message`.
8. **Pin the `DateTimes.Parse` pattern set** (D06). It must accept at least:
   - the app's own outputs: `g`, `G`, JustDateFormat/DateTimeFormat values, `yyyyMMdd HH:mm:ss`
   - ISO `yyyy-MM-dd`, `yyyy-MM-dd HH:mm[:ss[.f…]]` and `yyyy-MM-ddTHH:mm:ss[.f…]`
   - en-US `M/d/yyyy[ h:mm[:ss] tt]`, with leading/trailing whitespace allowed
   - Evidence: Web.config:216,230,240; util.cs:195,360.
9. **Certificate policy** (D43, F10, F13). Use the rule text in F10/F13. Security review: the human may instead sanction "do not trust all" as a §0.1 fix.
   - Evidence: POP3Main.cs:81,777-792.
10. **HttpClient default timeout** (D44). Rule text: "Every translated `System.Net.Http.HttpClient` request gets `.timeout(Duration.ofSeconds(100))`. `HttpTimeoutException` is wrapped as runtime `TaskCanceledException` (a `DotNetException`)."
    - Evidence: .NET `HttpClient.Timeout` default; POP3Main.cs:732.
11. **Runtime `pop` contract plus inventory BUG rows** (D41, D42).
    - `Pop3Client.GetMessageUids()` returns the server's UIDL strings verbatim.
    - `Message.Headers().From()` is `null` when the header is absent; `Subject()` is `null` when absent.
    - Inventory rows with `BUG(port)` for POP3Main.cs:566 (UID used as message number) and :718 (`bugId` = `TrackingIdString`, not the parsed id).
12. **`JsonConvert.SerializeObject`** (D36). Rule text: "→ `JsonConfig.mapper().writeValueAsString(x)`. The mapper serializes `DBNull` → `null`, `DataTable` via `DataTableSerializer`, and `LocalDateTime` as the §2A JSON rule (Json.NET ISO, trimmed fraction), in every context, not only Web API."
    - Evidence: BugSearch.cs:186.
13. **NEST 1.0.2 failure semantics** (D37), runtime TODO with an explicit owner. Verify whether `Search`/`Index` throw on connection failure or return an invalid response (empty `Hits`) under default `ConnectionSettings` (BugSearchFactory.cs:19-22). Encode the answer in §3 `ElasticClient`.
14. **Timer interval arithmetic** (D49). Rule text: "`timer.Interval = <int expr>` keeps int arithmetic (wraps). A value ≤ 0 throws runtime `ArgumentException` at the assignment site (System.Timers semantics)." Fix inventory row L306 (`60L`).
15. **Decimal and culture formatting** (F2, F3). Use the rule text in F2/F3.
16. **`Split(string[], options)` and `NumberFormatInfo`** (F1).
17. **AppDomain configuration path** (F5): `AppPaths.ConfigurationFile()`; translate literally.
18. **TimeSpan constructors and components** (F6, F7).
19. **DataColumnCollection iteration, `DataRow.Table()`, `DataRow.get(DataColumn)`** (F8), and **`DataSet.Tables.Add()`** (F9).
20. **`Console.WriteLine`** (F11) and **`ElapsedEventArgs`** (F12): runtime classes.
21. **§3 runtime list omissions:** `StringComparison` (`Ordinal`, `OrdinalIgnoreCase`, …), `HttpResponse.ContentEncoding(Encoding)`, `Encoding.GetPreamble()`.
22. **Interrupts.** Rule text: "`Exceptions.wrap(InterruptedException)` re-sets the thread's interrupt flag before wrapping." Evidence: A POP3:755,784 lose the flag that C19's `isInterrupted()` checks rely on.
23. **Inventory corrections** (rule rows already win):
    - rows 958/959 → `Threads.start` (J-C25 a)
    - `Util.context` → plain field (C32)
    - row 970 → `Encoding.Default.GetString(…)`

**Regeneration note (standing rule 6).**
- Amendments 1, 3, 4 and 7 change rules that every unit may touch.
- After they are applied, re-run the bakeoff on POP3Main (1), and grep translated units for `Convert.ToString(`/`.ToString()` over float columns (3), `DataType() ==` (4) and `getMessage()` in user-visible output (7).

## Applied (round 1)

Applied on 2026-11-04 under the standing mandate (`migration/PROGRESS.md`), together with the pilot gaps of `pilot-r1.md`. Tags `[S1-1]`…`[S1-48]` are in `migration/RULEBOOK.md` and in the evidence column of `migration/inventory.tsv`. Human decisions (all "preserve the original"): amendment 2 / D38 keep the defect (S1-3); amendment 6 / D40 reproduce the crash (S1-2); amendment 9 / D43 trust-all kept (S1-4); amendment 10 / D44 100 s + HTTP/1.1 (S1-5). D39, D47 and D48 were adopted as native-right.

**Sections changed. The re-round should be weighted toward these:**

| Section | What changed | Tags |
|---|---|---|
| §0 | UI posture: `.ascx`, masters and includes are plain classes, not `@Controller`s; gate log entry "stress-test round-1 amendments applied 2026-11-04 (S1-*)" | S1-26 |
| §1 | DB access (the service runs unpooled), HTTP client (`HttpClients`/`HttpRequests`), async row (`Threads.startTask`, unhandled-exception policy) | S1-2, S1-4, S1-5, S1-9 |
| §2A General conventions | local `const`, ternary diamond, `GetType()==typeof`, Dictionary null key / `ContainsKey` / `Add` / initializers / `IDictionary` / `Contains`, accepted lazy-`Where` ordering | S1-28, S1-29, S1-39, S1-40, S1-44, S1-45 |
| §2A Strings | concat rule vs "unchanged" rows, `Split(string[])`, `Strings.ToUpper/ToLower`, `StringComparison`, `GetPreamble`, `Console.WriteLine`, decimal `ToString` | S1-8, S1-15, S1-16, S1-20, S1-21, S1-46 |
| §2A Conversions | `Convert.ToString(Double)` = G15 / Float G7; `DateTime.Parse` pattern list pinned | S1-6, S1-11 |
| §2A Dates, numbers, culture | `InvariantCulture`, `NumberFormatInfo`, TimeSpan constructors and components, numeric `G` | S1-6, S1-15, S1-18 |
| §2A Exceptions | `Exceptions.Message(e)` replaces `e.getMessage()`; wrap re-sets interrupt; new exception classes | S1-3, S1-5, S1-10, S1-22 |
| §2A Database | tinyint `DataType` = `ClrTypes.Byte`; the service gets per-website unpooled connections plus a Cleaner | S1-7, S1-9 |
| §2A DataSet / EF6 | `typeof` token table, DataRow/DataColumn/Tables members, JSON in every context, J-C11 TODO has no site marker, EF projection accepted | S1-7, S1-12, S1-19, S1-38, S1-42, S1-43 |
| §2A Web intrinsics | `Request.Url()` returns runtime `Uri`; `AppDomain…ConfigurationFile` | S1-17, S1-21 |
| §2A Logging/mail/search/POP3 | OpenPop contract and BUG sites, NEST conditionless accepted, J-C2 HttpClient row (request built before the release, `LoginUrl` BUG, ALWAYS redirect), new `ServicePointManager` row | S1-1, S1-3, S1-4, S1-5, S1-13, S1-29, S1-48 |
| §2A Threads, timers, async | `Threads.create/start/startTask`, J-C25 citations, C17 int interval + `Timers.Interval/task`, J-C1 release point at every await, async-void crash | S1-1, S1-2, S1-14, S1-47 |
| §2B B1, B5, B6 | fluent setters, declared `style`, undeclared user-control attributes, non-field `<%= %>` regions, decoded `<%:` attribute values, nested-region naming and `__scriptN` numbering, `.ascx` constructor | S1-26, S1-27, S1-31, S1-32, S1-33, S1-35, S1-37 |
| §3 | package table; `Threads` bullets; new block "Exact semantics of the members added at stress-test round 1"; template marker forms | S1-2…S1-34 (runtime parts) |
| §4 | a second top-level type becomes a nested static class | S1-4 |
| §5 | element-content regions numbered by J-U3 alone; the list of round-1 inventory corrections | S1-23, S1-36 |
| §6 | 1 header path, 2/3 `(source TODO)`, 5 `// inventory: <file> L<line>` | S1-24, S1-25, S1-41 |
| §7 | D-006: accepted latent divergences | S1-13, S1-18, S1-42, S1-44 |

**Inventory changes.**
- Rows rewritten to the rule:
  - `POP3Main.cs`: L86, L91, L223, L306, L403, L707, L732
  - `util.cs`: L32, L1117
  - `admin.aspx.cs`: L43
  - `BugList.ascx`: L128 (×2)
  - `Mime.cs`: L90 note
- 10 rows changed `e.getMessage()` to `Exceptions.Message(e)`.
- New `POP3Main.cs` rows: L81, L173, L278 (BUG, D38), L566 (BUG, D41), L589, L704, L718 (BUG), L768.

**Deliberate departures from the proposed rule text.**
- `Split(string[])`: the separator chosen at each position is the first one in argument order, as .NET documents. Longest-first, as F1 proposed, is not what .NET does.
- G15 fixed-point range: fixed iff −5 < e < 15, so `0.00001` → `1E-05`. Amendment 3 had the lower bound wrong.
- AppDomain: F5's literal `AppPaths.ConfigurationFile()` won over the pilot's `MapPath(null)`.
- `ContainsKey(null)`: reproduces the .NET throw, per the decision, instead of the pilot's "accept".
- The D40 crash policy covers every `new Thread` thread too (.NET 2.0+ semantics), with `Task.Run` → `Threads.startTask`, which drops the exception. This goes beyond the decision, which named only async void.

**Not applied (not rulebook items).**
- Pilot P1–P3 and BugSearch G9 are pipeline-prompt fixes. They were already made in `migration/pipeline/`.

**Regeneration (standing rule 6).**
- Re-run the bakeoff on POP3Main (S1-1, S1-2, S1-3, S1-4, S1-5, S1-9, S1-14).
- Grep translated units for:
  - `Convert.ToString(`/`.ToString()` over float columns (S1-6)
  - `DataType() ==` / `typeof` (S1-7)
  - `getMessage()` (S1-10)
  - `toUpperCase`/`toLowerCase` (S1-8)
  - `Threads.start(` for `Task.Run` (S1-2)
