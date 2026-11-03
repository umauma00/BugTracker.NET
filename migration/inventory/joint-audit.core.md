# Joint audit: RULEBOOK (§0–§6) against the core rows of inventory.tsv

Auditor: adversarial joint auditor, core layer (`btnet/`, `Models/`, `Security/`,
`Mail/`, `Search/`, `Controllers/`, `App_Start/`, `BasePage.cs`, `Startup.cs`,
`src/btnet_service/`). Date 2026-11-03. Inputs: `migration/RULEBOOK.md` (amended
C1–C35), `migration/inventory.tsv` (232 core rows plus the 334 EF map rows),
`migration/inventory/rulebook-conflicts.md` N1–N8, and the source files cited
below. I changed nothing except this file.

Row numbers are `inventory.tsv` line numbers (the header is line 1). "Side" says
where the fix goes. **Inventory-side** means the row is wrong and the rule is
right. **Rulebook-side** means the rule is wrong, ambiguous or missing, and any
rows that follow it change with it.

Totals: **26 conflicts**. 7 are inventory-side, 19 are rulebook-side. Seven of
the rulebook-side items also need rows rewritten or added (marked "+rows").

The EF map rows (`Models/Mapping/*`, all `EF_MAPPING_REPLACED`) were checked
against the §2A EF6 rule. All 23 maps use single-property `HasKey`, there are no
composite keys and no `HasRequired/WithMany` chains, and no conflict was found.

---

## Conflicts

### J-C1: C33 "rest of the body after the first await" cannot be applied to the only async site (rulebook-side, +row 866), HIGH
- **Rule:** §2A Threads/timers/async, C33: "`async void M(...)` → `void M(...)`: statements before the first `await` run on the caller's thread; the rest of the body is wrapped in `Threads.runAsync(() -> { … })`".
- **Row:** 866 `POP3Main.cs` `fetch_messages` L732 `(Exception e)` follows C33.
- **What goes wrong:** the first `await` (`:704 httpClient.PostAsync`) is nested inside `using (Pop3Client client)` → `for` over messages → `try` → `using (httpClient)`. "The rest of the body" is not a lexical suffix. It is the rest of iteration 1, every later iteration, the `QUIT` at :748 and the dispose of `client`. A translator can do only one of two wrong things:
  - wrap the inner `try` alone, which starts one thread per message, runs the loop on the caller thread and disposes `client` while the threads still use it;
  - or wrap the whole body, which makes the connect/auth/list phase asynchronous, although .NET runs it synchronously before `fetch_messages_for_projects` moves to the next project.
- **Fix:** replace the C33 text with this. The whole body runs inside `Threads.runAsync`. The caller blocks on a one-shot `java.util.concurrent.CountDownLatch firstAwait`. The latch is released immediately before the first blocking `client.send(...)` that stands for an `await`, and also in a `finally`, so a body that never reaches an await (connect failure, zero messages) still releases the caller. This reproduces "caller continues at the first await" exactly. Runtime: `Threads.runAsync(Consumer<Runnable> body)`, where the argument is the release callback. Row 866 is rewritten to match.

### J-C2: §2A's "HttpWebRequest" row describes a construct the source does not use, and the real one is unmapped (rulebook-side), HIGH
- **Rule:** §2A Logging…POP3 table: "`HttpWebRequest` (`POP3Main.cs:734-800`) → `java.net.http.HttpClient` … `GetResponseAsync`/`GetRequestStreamAsync` → blocking `client.send`".
- **Source:** `POP3Main.cs:689-724` uses `System.Net.Http`: `HttpClientHandler { AllowAutoRedirect = true, UseCookies = true, CookieContainer = new CookieContainer() }`, `new HttpClient(handler)`, `FormUrlEncodedContent(Dictionary<string,string>)`, `PostAsync`, `EnsureSuccessStatusCode()`. Line 707 has `Encoding.Default.GetString(mimeMessage.RawMessage)`. The only "HttpWebRequest" in the file is a log string at :734. No `GetResponseAsync` or `GetRequestStreamAsync` exists.
- **What goes wrong:** nothing tells the translator:
  - how the form body is encoded (.NET: `Uri.EscapeDataString` per key and value, `%20`→`+`, `application/x-www-form-urlencoded`);
  - what `EnsureSuccessStatusCode` throws (`HttpRequestException("Response status code does not indicate success: <code> (<reason>).")`), which the :732 catch counts in `total_error_count`;
  - what `Encoding.Default` is (Windows ANSI, cp1252 on en-US). Writing UTF-8 changes every non-ASCII byte of the posted `message`.
- **Fix:** rewrite the row as "`System.Net.Http.HttpClient`/`HttpClientHandler`/`FormUrlEncodedContent` (`POP3Main.cs:689-724`)". Map it to a `java.net.http.HttpClient` built with `CookieManager` and `Redirect.NORMAL` (C33 already says this). The body is built by a runtime `FormUrlEncodedContent.encode(Map)` with the .NET encoding. `EnsureSuccessStatusCode` becomes a runtime helper that throws `HttpRequestException`, a `DotNetException` subclass, for non-2xx. `Encoding.Default` becomes the runtime constant `windows-1252`. Add `TODO(port): verify .NET 4.5 FormUrlEncodedContent length limit (Uri.EscapeDataString may throw "Invalid URI: The Uri string is too long." on large raw messages; caught at :732 as an error)`.

### J-C3: §0.1 says `Security.cs:73` "overwrites" a claim. It does not, and a runtime built to that description is wrong (rulebook-side), HIGH
- **Rule:** §0.1 "Not fixed (preserved): `Security.cs:73` overwriting the `CanOnlySeeOwnReportedBugs` claim from `us_enable_bug_list_popups`" (repeated in `judge/specs/S9.md:105`). §3 lists `ClaimsIdentity`/`Claim` without semantics.
- **Source:** `Security.cs:49` adds `CanOnlySeeOwnReportedBugs` from `og_can_only_see_own_reported`. `:73` adds a **second** claim of the same type. `ClaimsIdentityExtensions.cs:178` reads with `claimsIdentity.FindFirst(claimType)`, which in .NET returns the **first** claim in insertion order. Line 73 therefore has no effect. The org setting wins.
- **What goes wrong:** the rulebook calls this an overwrite, which tells the runtime author to treat claims as last-wins. A runtime `ClaimsIdentity` backed by a `Map<type,value>`, or a `FindFirst` that searches backwards, makes every user with bug-list popups enabled "can only see own reported bugs". That changes `alter_sql_per_project_permissions` results for non-admins.
- **Fix:** §3 states that `ClaimsIdentity` keeps an ordered `List<Claim>` (duplicates kept), `FindFirst` returns the first match in insertion order, and `HasClaim`/`IsInRole` match any claim. Correct §0.1 and S9 to say "duplicate claim at :73 is shadowed by :49 (FindFirst); preserved".

### J-C4: `StringDictionary` → "HashMap with lower-cased keys" is ambiguous, and `XmlTextReader` lacks the indexer `get_settings` uses (rulebook-side, +rows), MEDIUM-HIGH
- **Rule:** §2A General: "`StringDictionary` → `HashMap` with lower-cased keys". C35/§3: `XmlTextReader` members "`Read()`, `NodeType`, `Name`, `Value`, `Close()`".
- **Source:** `POP3Main.cs:394,405-420,251-272`: `settings[key] = tr["value"]`, `settings["MessageInputFile"] = ""`, `MessageInputFile = settings["MessageInputFile"]`. There are no inventory rows.
- **What goes wrong:** "lower-cased keys" read literally gives `settings.put("messageinputfile", "")` and `settings.get("MessageInputFile")`, which returns `null`. Then `MessageOutputFile != ""` becomes `!Objects.equals(null, "")`, which is true, so `File.WriteAllBytes(null, …)` runs and every website's POP3 settings come back null. Separately, `tr["value"]` (attribute read, :370, :391, :394) and `XmlNodeType.Element` (:403) have no runtime member, so the translation does not compile without a `TODO(port)`.
- **Fix:** add a runtime `btnet.runtime.StringDictionary`. `get` and `set` lower-case the key with `Locale.ROOT` internally, and `get(absent)` returns `null`. Translators write `settings.get("MessageInputFile")` verbatim. Add `XmlTextReader.get(String attr)` (= `GetAttribute`, `null` if absent) and a runtime enum `XmlNodeType` to C35/§3. Add rows for `POP3Main.cs:251-272,370-420`.

### J-C5: .NET `[Flags]` enums have no Java representation, so `a | b` does not compile (rulebook-side), MEDIUM
- **Rule:** §3 lists `CommandBehavior`, `RegexOptions`, `NumberStyles`, `StringSplitOptions` as runtime types without saying what they are. §2A shows `new DotNetRegex(p, RegexOptions.IgnorePatternWhitespace)`.
- **Rows:** 475 and 476 `dbutil.cs:101,120` keep `cmd.ExecuteReader(behavior | CommandBehavior.CloseConnection)`. Rows 480–483 `print_bug.cs:19,28` write `RegexOptions.IgnoreCase | RegexOptions.IgnorePatternWhitespace`. Row 514 `util.cs:162` uses `NumberStyles.Float`.
- **What goes wrong:** if the runtime author makes these idiomatic Java `enum`s, `|` is a compile error at every one of those sites, and all of them are core.
- **Fix:** §3 says these four are `public final class X { public static final int A = …; }` with the .NET numeric values (`CommandBehavior.CloseConnection = 32`, `RegexOptions.IgnoreCase = 1`, `Multiline = 2`, `Singleline = 16`, `IgnorePatternWhitespace = 32`, `Compiled = 8`, `CultureInvariant = 512`), and that every parameter of these types is `int`. Plain non-flags .NET enums (`SqlDbType`, `MailPriority`) stay Java enums.

### J-C6: The Stream family is missing from §3, but rows cite it (rulebook-side), MEDIUM
- **Rule:** §3 `btnet.runtime.io` lists only `File`, `Directory`, `Path`, `StreamReader`, `XmlTextReader`. §2A `using` requires an `AutoCloseable` runtime type.
- **Row:** 445 `bug.cs:522` "`content.Dispose()` (Stream.Dispose -> runtime Stream.Dispose()/close wrapper, RULEBOOK 2A IO)". No such row exists in §2A.
- **Sources without a mapping:**
  - `bug.cs:230,257,278,436,445,464` (`Stream` parameters and fields);
  - `:499` `new MemoryStream(byte[])`;
  - `:385,:512` `new FileStream(path, FileMode.CreateNew|Open, FileAccess.Write|Read, FileShare.Read)`;
  - `email.cs:90`;
  - `Mime.cs:206-222` (`Position = 0`, `(int)Length`);
  - `BugFromEmailController.cs:156,222`;
  - `bug.cs:1158` `System.IO.StringWriter`.
- **What goes wrong:** each translator invents its own mapping (`InputStream`, `ByteArrayInputStream`, `java.io.FileOutputStream`). The signatures of `Bug.insert_post_attachment` and `BugPostAttachment.content` then disagree across units, which breaks the 1-file-per-unit compile. `FileMode.CreateNew` must fail if the file exists; `new FileOutputStream` silently truncates.
- **Fix:** add runtime `btnet.runtime.io.Stream` (abstract, `AutoCloseable`; `Read`, `Write`, `CopyTo`, `Position()`/`Position(long)`, `Length()`, `Dispose()`, `Close()`), `MemoryStream`, `FileStream`, and `FileMode`/`FileAccess`/`FileShare`. `CreateNew` on an existing file throws runtime `IOException` ("The file '<p>' already exists."). `StringWriter` maps to `java.io.StringWriter` (the `new HttpResponse(stringWriter)` constructor takes a `java.io.Writer`).

### J-C7: `a ?? b` → `Nulls.coalesce(a, b)` evaluates `b` eagerly; C# short-circuits (rulebook-side, +rows 7–11), MEDIUM
- **Rule:** §2A General: "`a ?? b` (12, `BugFromEmailController.cs:115-130`) → `btnet.runtime.Nulls.coalesce(a, b)`".
- **Rows:** 7 (`:115 bugFromEmail.OrganizationId ?? User.Identity.GetOrganizationId()`) and 8–11 (`?? (int)defaults["ct"]` …).
- **What goes wrong:** `Nulls.coalesce(bugFromEmail.OrganizationId, ClaimsIdentityExtensions.GetOrganizationId(User.Identity()))` always calls `GetOrganizationId`. That call throws `SecurityException("Identity is not a valid Claims Identity")` or "missing value for claim type" (`ClaimsIdentityExtensions.cs:180-189`) even when the client supplied `OrganizationId`. C# never evaluates the right-hand side in that case. The same applies to `(int) defaults.get("ct")` (unboxing a DBNull → ClassCastException). It is harmless today only because `get_bug_defaults` always returns ints.
- **Fix:** add the runtime overload `Nulls.coalesce(T a, Supplier<T> b)`. The rule becomes: if the right-hand side is a literal, a local or a field, use `coalesce(a, b)`; otherwise use `coalesce(a, () -> b)`. Rows 7–11 change to the supplier form; 12–18 (`?? 0`, `?? string.Empty`) stay.

### J-C8: C30 bare catch: whether `rethrowIfEnd` applies when the body ends with `throw;` is undecided, and rows disagree (rulebook-side, +row 522), MEDIUM
- **Rule:** §2A Exceptions C30: "Bare `catch { … }` → `catch (Throwable t) { Exceptions.rethrowIfEnd(t); … }` … a bare catch whose body is or ends with `throw;` → `throw t;`".
- **Rows:** 445 (`bug.cs:522`), 446 (`:400`), 475 and 476 (`dbutil.cs:104,122`) **omit** `rethrowIfEnd`. 522 (`util.cs:566 sanitize_integer`, body `throw new Exception(...)`, not `throw;`) also omits it.
- **What goes wrong:** two agents can produce different code (meta-rule violation). If `rethrowIfEnd` is inserted before cleanup-then-rethrow bodies, `conn.Close()` (dbutil) and `content.Dispose()` (bug.cs) are skipped on an abort, although .NET runs the body before re-raising (the same reasoning as C7). For row 522 the omission is the opposite error: an abort would be turned into `DotNetException("Expected integer…")`.
- **Fix:** amend C30 to say that when the body ends with `throw;`, `rethrowIfEnd` is **omitted** (the body rethrows `t` anyway, and running the cleanup matches .NET). Every other bare catch gets it as the first statement. Rows 445/446/475/476 then conform. Row 522 must add `Exceptions.rethrowIfEnd(t);`.

### J-C9: BugSearch `Hit`: nested-vs-top-level class and field-vs-method usage contradict §3 C29 (inventory-side, +§3 for N1), MEDIUM
- **Rule:** §2A NEST row and §3 C29: top-level `btnet.runtime.search.Hit { String Id; double Score; Map<String,Object> Source; }` (public fields); `SearchResponse.Hits` = `List<Hit>`.
- **Rows:**
  - 402 (`BugSearch.cs:110`) declares `List<SearchResponse.Hit>`, a nested type that §3 does not define. It will not compile.
  - 412 uses the field `hit.Highlights`.
  - 413 uses the method `hit.Highlights()`.
  - 414 uses `highlight.getValue().Highlights()` (a method) next to 412's `.Field` (a field).
- **What goes wrong:** the three rows of one statement disagree, so whichever shape the runtime ships, at least one of them fails to compile.
- **Fix:** rows 402 and 412–414 use `List<Hit>` and public fields throughout (`hit.Highlights`, `highlight.getValue().Field`, `highlight.getValue().Highlights`), per the N1 decision below.

### J-C10: `ElasticClient.Index` is not in §3, and row 405 invents `Convert.ToLong` (rulebook-side, +row 405), MEDIUM
- **Rule:** §3 `btnet.runtime.search`: "`ElasticClient` (thin HTTP wrapper; `Search`)". C29 defines only `Search`. §2A says `BugSearch` issues "index `PUT {uri}/btnet/bug/{id}`" but names no member. The §2A Convert table has no `ToInt64`.
- **Row:** 405 `BugSearch.cs:164` writes `PUT …/{Convert.ToLong(bugRow.get("bg_id"))}` and `TODO(port): runtime ElasticClient.Index(String json, long id)`.
- **What goes wrong:** `Convert.ToLong` does not exist (the C# member is `ToInt64`; §3 keeps .NET names). The `Index` signature is left to each translator.
- **Fix:** §3 adds `ElasticClient.Index(String jsonBody, String type, long id)` → `PUT {uri}/{defaultIndex}/{type}/{id}`, with failures thrown as `DotNetException`. They are caught by `IndexBug`'s catch, as §2A requires. Add `Convert.ToInt64(Object)` to the Convert table (ToInt32 semantics, `long` range). Row 405 becomes `_client.Index(bugJson, "bug", Convert.ToInt64(bugRow.get("bg_id")));`.

### J-C11: Writing a value into a typed DataColumn is unspecified, and BugSearch depends on .NET's coercion (rulebook-side, +new rows), MEDIUM
- **Rule:** §2A DataSet: "`dr["col"] = v` → `dr.set("col", v)`". There is no coercion rule. `Convert.ToDecimal` is listed without `Double` semantics.
- **Source:** `BugSearch.cs:198-205` creates typed columns (`date` is `DateTime`, `#SCORE` is `decimal`, `id` is `int`). `:86` stores `bug["bg_reported_date"].ToString()` (a **string**) into the `DateTime` column. In .NET, Json.NET parsed the ISO date into a `DateTime`, `ToString()` produced en-US `G`, and the `DataColumn` converted it back. `:88` stores `Convert.ToDecimal(hit.Score)` (a double). `:83` may store `null`. There are no rows for :86 or :88.
- **What goes wrong:** in Java, `Hit.Source` holds the JSON string `"2026-11-03T14:25:01.123"`. `dr.set("date", thatString)` either stores a `String` in a `LocalDateTime` column (then `DotNetFormat` or casts fail when search.aspx renders the date) or throws, and either way the whole search results page breaks. `Convert.ToDecimal(double)` implemented as `new BigDecimal(d)` gives 50-digit binary expansions; .NET rounds to 15 significant digits.
- **Fix:** §3 `DataRow.set(col, v)`: `null` → `DBNull.Value`; a value whose class differs from `DataType()` is converted by `Convert.ChangeType(v, DataType, en-US)`. String → `LocalDateTime` goes through `DateTimes.Parse`, whose pattern list must include ISO 8601 `yyyy-MM-ddTHH:mm:ss[.fffffff]`. Failure → `ArgumentException("… Couldn't store <v> in <col> Column. Expected type is DateTime.")`. `Convert.ToDecimal(Double)` = .NET (`new BigDecimal(Double.toString(d))` rounded to 15 significant digits, half-even). Add inventory rows for `BugSearch.cs:72-88`.

### J-C12: Duplicate column names in a result set are unspecified (rulebook-side), MEDIUM
- **Rule:** §2A DataSet: "Column lookup is case-insensitive (exact match first)". Nothing covers duplicate names returned by `Fill`.
- **Source:** `Security.cs:22-25` (`GetIdentity`, every login, F4 site) selects `u.us_forced_project` **and** `isnull(u.us_forced_project, 0) us_forced_project`, plus `org.*`. .NET `DataAdapter.Fill` renames the second to `us_forced_project1`, and `dr["us_forced_project"]` reads the first (nullable) one (`:87` checks DBNull for exactly that reason). There is no inventory row (Security.cs has none at all).
- **What goes wrong:** depending on the runtime author's choice, the result is one of these:
  - a `DataColumnCollection.Add` that rejects duplicates (as .NET's own `Columns.Add` does) makes every login a 500;
  - a name→index map that overwrites keeps the wrong column and changes `Columns().Count()` and the DataTable JSON key set of any `select *` query.
- **Fix:** §3 states that `SqlDataAdapter.Fill` and `SqlDataReader` schema mapping give a duplicate (case-insensitive) name the suffix `1`, `2`, … (the first free one, as .NET `DataColumnCollection` naming does). Lookup by the bare name finds the first. Add a Security.cs:22 row citing this.

### J-C13: C20 `WorkingDirectory → .directory(new File(dir))` is unconditional, and `configure_startinfo` has no mapping (rulebook-side), MEDIUM
- **Rule:** §2A Processes C20: "`WorkingDirectory` → `.directory(new java.io.File(dir))`; … `UseShellExecute=false`/`CreateNoWindow` → nothing".
- **Row:** 555 (`version_control.cs:32`) adds `if (!Strings.IsNullOrEmpty(working_dir))`. That is correct but goes beyond the rule. It also changes `configure_startinfo(ProcessStartInfo)` (`:20-26`) to take a `ProcessBuilder`, which the rule does not authorize (core: "same members").
- **What goes wrong:** `new java.io.File(null)` throws an NPE where .NET treats a null or empty `WorkingDirectory` as the current directory. Under the rule as written, `run_svn` (no working dir) or a null `working_dir` breaks. Translators following the rule disagree with row 555.
- **Fix:** amend C20 to say: set `.directory(...)` only when the value is non-empty. `configure_startinfo` keeps its name and takes `ProcessBuilder startInfo`. Its body becomes one comment line per assignment (`WindowStyle`/`UseShellExecute` dropped, `Redirect*` = pipes are the default, `StandardOutputEncoding = UTF8` → stdout read as UTF-8). Row 555 then conforms.

### J-C14: Startup / OWIN pipeline types are unmapped (rulebook-side), MEDIUM
- **Rule:** §2A General "`Startup.cs` + `App_Start/Startup.Auth.cs` → one `btnet/Startup.java`". §3 security lists `SecurityConfig`, `IOwinContext`, `AuthenticationManager` only. The manifest unit has no inventory rows.
- **Source:** `Startup.cs:7` `[assembly: OwinStartup(typeof(btnet.Startup))]`, `Configuration(IAppBuilder app)`. `Startup.Auth.cs` `app.UseCookieAuthentication(new CookieAuthenticationOptions { AuthenticationType = "ApplicationCookie", LoginPath = new PathString("/default.aspx") })`.
- **What goes wrong:** core posture requires both methods with the same members, but `IAppBuilder`, `CookieAuthenticationOptions` and `PathString` do not exist. Translators either emit `TODO(port)` stubs that do not compile, or drop the methods (a completeness violation, §6.2). Nothing ties `LoginPath` to the §2B B7 API 302 target.
- **Fix:** add these to §3 security:
  - `IAppBuilder` with `UseCookieAuthentication(CookieAuthenticationOptions)`, recording the options;
  - `CookieAuthenticationOptions` with public fields `AuthenticationType` and `LoginPath`;
  - `PathString(String)`.

  `SecurityConfig` calls `new Startup().Configuration(builder)` at boot and takes the B7 redirect path from `LoginPath`. The assembly attribute becomes the comment `// [assembly: OwinStartup] → invoked by SecurityConfig`.

### J-C15: `MailPriority` name clash: app enum and runtime enum in one file (rulebook-side), LOW-MEDIUM
- **Rule:** §3 mail: "`MailPriority`-compatible enum" (unnamed). §4: explicit single-type imports.
- **Source:** `Mail/email.cs:124-147` compares the app enum `btnet.Mail.MailPriority` (`Mail/MailPriority.cs`) and assigns `System.Net.Mail.MailPriority.Normal/High` to `msg.Priority`.
- **What goes wrong:** importing `btnet.runtime.mail.MailPriority` shadows the same-package `btnet.Mail.MailPriority`, so `priority == MailPriority.Normal` (`:142`) no longer compiles, or compares the wrong type. Not importing it leaves `msg.Priority(MailPriority.Normal)` passing the app enum to the runtime.
- **Fix:** §3 names it `btnet.runtime.mail.MailPriority`. Add a §4 rule: when a runtime type's simple name equals an application type's name, the runtime type is written fully qualified and never imported (`msg.Priority(btnet.runtime.mail.MailPriority.Normal)`).

### J-C16: C28 changes `OnAuthorize(IPrincipal)` to `OnAuthorize(ClaimsPrincipal)`, and row 3 hedges with "cast if" (rulebook-side), LOW-MEDIUM
- **Rule:** §2A attribute row C28: "`public boolean OnAuthorize(ClaimsPrincipal user)`", `Logic(String... roles)` only.
- **Source / rows:** `PageAuthorizeAttribute.cs:22` takes `IPrincipal` and has **two** constructors (no-arg and `params`). Row 3 (`BasePage.cs:30`): "(cast Page.User to ClaimsPrincipal if the intrinsic is typed IPrincipal)".
- **What goes wrong:** core posture is "same members". Whether a cast is inserted depends on how the runtime types `User`, so two translators produce different code, and a non-`ClaimsPrincipal` user would throw `ClassCastException` instead of being evaluated. The no-arg constructor is dropped (§6.2 completeness).
- **Fix:** C28 becomes `public Logic()` (empty `_roles`), `public Logic(String... roles)`, `public boolean OnAuthorize(IPrincipal principal)` (runtime `IPrincipal` has `Identity()` and `IsInRole()`). Drop the "cast if" from row 3.

### J-C17: `o.ToString()` → `Convert.ToString(o)` turns a NullReferenceException into `""`, which contradicts the Error-recovery rule (rulebook-side), LOW
- **Rule:** §2A Strings: "`o.ToString()` on non-String `o` … → `Convert.ToString(o)` (null→`""`)". §2A Error-recovery: "never turn … an exception into a `null`".
- **Source:** `BugSearch.cs:78,86,87` `bug["desc"].ToString()`. A `JObject` indexer returns C# `null` for a missing property, so .NET throws NRE (search 500). With `Map.get` the port renders `""` and continues. `ToString()` on DataRow values is unaffected, because they are never null.
- **Fix:** split the rule. For a receiver that cannot be null (DataRow/DataRowView values, literals, boxed primitives), use `Convert.ToString(o)`. Otherwise use `Strings.ToStringStrict(o)`, a runtime method that throws `NullPointerException` on null and else returns `Convert.ToString(o)`.

### J-C18: `foreach (string x in <non-generic ArrayList/Hashtable>)` does not compile under the mapping (rulebook-side), LOW
- **Rule:** §2A: "`Hashtable` / `ArrayList` (non-generic) → `HashMap<Object,Object>` / `ArrayList<Object>`". There is no foreach-with-cast rule.
- **Source:** `email.cs:289` `foreach (string directory in stuff_to_delete.directories_to_delete)` (an `ArrayList`, `:313`). The same pattern applies to `websites` in POP3Main.
- **What goes wrong:** `for (String directory : stuff_to_delete.directories_to_delete)` over `ArrayList<Object>` is a compile error. C# inserts an implicit cast that can fail with InvalidCastException.
- **Fix:** add "foreach with a typed variable over a non-generic collection → `for (Object o_ : c) { String directory = (String) o_; … }`".

### J-C19: `Convert.ToString(Throwable)` and `ArgumentException(message, paramName)` are unspecified (rulebook-side), LOW
- **Rule:** §2A Exceptions maps `e.ToString()`/`"" + e` to `Exceptions.dotNetToString`. The Convert table does not say what `Convert.ToString(Throwable)` returns. §3 lists `ArgumentException` without constructor semantics.
- **Source / rows:**
  - `POP3Main.cs:430,469,736` `write_line(e)` → `write_line(object o)` → `Convert.ToString(o)` (row 867 `write_line(e)`);
  - `BugQueryExecutor.cs:38,115` `new ArgumentException("Invalid … {0}", orderBy)`. The two-argument constructor is (message, **paramName**), not a format; the .NET message is `"Invalid order by column specified: {0}\r\nParameter name: <value>"`.
- **What goes wrong:** the service log shows Java `toString()` instead of the .NET form. A translator may call `Strings.Format` on the message, which "fixes" the source's misuse.
- **Fix:** add to §3:
  - `Convert.ToString(Throwable)` = `Exceptions.dotNetToString`;
  - `ArgumentException(String message, String paramName)`, where `getMessage()` = message + `"\r\nParameter name: "` + paramName (.NET 4.5).

### J-C20: The `LoggingConfig` line layout is not pinned (rulebook-side), LOW
- **Rule:** §2A "programmatic Logback … rolling file `<log folder>/btnet_log.txt`, daily, level TRACE…". No pattern is given.
- **Source:** `App_Start/LoggingConfig.cs` `FileTarget` with no Layout, so NLog's default `${longdate}|${level:uppercase=true}|${logger}|${message}` applies (`2026-11-03 14:25:01.1234|DEBUG|btnet.Util|sql=…`).
- **What goes wrong:** Logback's default pattern differs, and the logs are an operator-facing artifact (`ErrorEmailEnabled` mail and memory log are separate). Two runtime authors could choose differently.
- **Fix:** pin `%d{yyyy-MM-dd HH:mm:ss.SSSS}|%level|%logger|%msg%n`. Logback emits 3 fraction digits padded with 0, so add `TODO(port)` for exact 4-digit ticks. Archive naming per NLog `ArchiveNumbering=Date` is `TODO(port)`. It is not judged.

### J-C21: Rows prescribe Java `catch (Exception …)`, which C11 bans (inventory-side), LOW
- **Rule:** §2A Exceptions C11: "as a declared variable/parameter/catch type → `RuntimeException`. Never `java.lang.Exception`".
- **Rows:**
  - 465, 467, 471 (`bug_list.cs:205,227,273`) and 513 (`util.cs:151`) say "C# `catch (Exception)` -> `catch (Exception ignored)`";
  - 496 (`user.cs:137`) says "`catch (Exception ex)` stays";
  - 498 and 523 (`util.cs:33/58`) say "Java `catch (Exception e)`".

  Each of these rows also quotes the C11 mapping, so each contradicts itself.
- **What goes wrong:** the result compiles and behaves the same today. However, reviewers following the rule must flag every such site. Catching checked `Exception` also hides any future unwrapped checked call, which the "no checked exceptions" contract relies on.
- **Fix:** rows read `catch (RuntimeException ignored)` / `catch (RuntimeException ex)` / `catch (RuntimeException e)`.

### J-C22: Mime rows use `String.join`, which C3 forbids (inventory-side), LOW
- **Rule:** §2A Strings C3: "`string.Join(sep, xs)` → `Strings.join(sep, xs)` … `java.lang.String.join` is not used".
- **Rows:** 60–63 (`Mime.cs:90,96`): `String.join("; ", message.Headers().Cc().stream().map(c -> Strings.cs(c.Address())).collect(Collectors.toList()))`.
- **What goes wrong:** it produces the same output only because of the hand-added `Strings.cs`. It is a banned form and gives two canonical translations for one construct.
- **Fix:** `Strings.join("; ", message.Headers().Cc().stream().map(c -> c.Address()).toList())` (and the same for `To()`).

### J-C23: print_bug rows use `m.Value` as a field, but C26 says `m.Value()` (inventory-side), LOW
- **Rule:** §2A Regex C26: "`m.ToString()` → `m.Value()` (runtime property convention)". §2A General: runtime facade properties are methods.
- **Rows:** 484 (`print_bug.cs:986`), 485 (`:1004`), 486 (`:948`) write `m.Value`.
- **What goes wrong:** compile error against a runtime `Match` that follows §2A.
- **Fix:** `m.Value()` in all three rows.

### J-C24: dbutil row assigns to a runtime property (inventory-side), LOW
- **Rule:** §2A General: "Property of a runtime facade type … write `x.Value(v)`".
- **Row:** 476 (`dbutil.cs:119,129`) writes `cmd.Connection = conn; … finally { cmd.Connection = null; }`. The same pattern at `dbutil.cs:75,82` has no row.
- **What goes wrong:** this does not compile if `Connection` is a method (the convention). If the runtime author exposes a public field to make the row compile, it breaks the convention for every other unit.
- **Fix:** `cmd.Connection(conn)` / `cmd.Connection(null)`. Add rows for `:75,:82`.

### J-C25: Thread rows hand-expand `Threads.start`, and split construct/start sites are not covered by the rule (inventory-side, +rule clarification), LOW
- **Rule:** §1 / §2A C2, C22: "`new Thread(proc).Start()` → … written via `Threads.start(proc)`".
- **Rows:**
  - 64, 448, 490, 491, 433 write `new Thread(...); thread.setDaemon(false); thread.start();`.
  - 859, 860 and 872 (`POP3Main.cs:86,91,223`) write `new Thread(...)` + `start()` with no `setDaemon`, arguing that the creating thread is non-daemon.
- **What goes wrong:** there are three forms for one construct. In addition, `POP3Main.cs:223-226` constructs, logs, and then starts. `Threads.start` cannot reproduce that order without moving the log line, so the rule as written is unusable there.
- **Fix:** where `Start()` directly follows construction, the rows use `Threads.start(...)`. Amend C22 so that when construction and `Start()` are separate statements, the translation is `new Thread(p)` + `setDaemon(false)` at construction and `start()` at the `Start()` site. That covers 859, 860 and 872, and they gain `setDaemon(false)`.

### J-C26: C27 two-argument `IndexOf` sites have no rows (inventory-side), LOW
- **Rule:** §2A Strings C27: bare `indexOf(v, start)` is BANNED, use `Strings.IndexOf`.
- **Sites without rows:**
  - `version_control.cs:465` (N4) and `:589`, `:590` (`pos1 = line.IndexOf("+")` may be -1);
  - `bug_list.cs:321` (`pos` may exceed the length);
  - `Mime.cs:39,43`;
  - `POP3Main.cs:664,667`.

  Row 59 (`Mime.cs:27`) mentions only `subject.value.indexOf(bugidString)`.
- **What goes wrong:** §5 says that a site missing from the inventory is an inventory bug, and the reviewers grep rows. Where `start` is -1, Java clamps and .NET throws `ArgumentOutOfRangeException`.
- **Fix:** add one row per site with `Strings.IndexOf(s, v, start)` (see N4).

---

## Decisions for N1–N8 and unknowns

The recommended option is stated as the decision (pre-approved by the human).

**N1 / unknown row 412 (`Search/BugSearch.cs:79` Hit.Highlights shape).** Decision: extend C29 and §3 `btnet.runtime.search` as follows.
- `Hit` gains `public Map<String, Highlight> Highlights` (a `LinkedHashMap` in the JSON object order of `hits.hits[i].highlight`; **empty, never null** when the response has no `highlight`, as NEST 1.0.2 `HighlightFieldDictionary`).
- A new top-level class `Highlight { public String DocumentId; public String Field; public List<String> Highlights; }`, filled from `_id`, the field name, and the fragment array.
- Every member is a public field (C29 style). `Hit` is top level, not nested in `SearchResponse`.

Translation of `:79-84`:
```java
if (!hit.Highlights.isEmpty()) {                                   // .Any()
    var highlight = hit.Highlights.entrySet().iterator().next();   // .First()
    resultRow.set(ResultColumns.Source, highlight.getValue().Field);
    resultRow.set(ResultColumns.Text,
        highlight.getValue().Highlights.isEmpty() ? null : highlight.getValue().Highlights.get(0)); // FirstOrDefault
}
```
`null` → DBNull is done by `DataRow.set` (J-C11), so drop row 414's explicit ternary to `DBNull.Value` and its TODO. Rows 412–414 change to `confirmed` with field syntax, and row 402 becomes `List<Hit>` (J-C9). Keep `TODO(port): verify NEST 1.0.2 wire format`.

**N2 / unknown row 519 (`btnet/util.cs:1103-1117` UrlHelper.IsLocalUrl).** Decision: add runtime `btnet.runtime.web.UrlHelper` with constructor `UrlHelper(RequestContext ctx)`, and `HttpRequest.RequestContext()` returning an opaque runtime `RequestContext`. The semantics are exactly those of MVC 5.2.3 `UrlHelper.IsLocalUrl` → `System.Web.WebPages 3.2.x RequestExtensions.IsUrlLocalToHost` (string-only, no `Uri` parsing, no trimming, no decoding):
```java
public boolean IsLocalUrl(String url) {
    if (url == null || url.isEmpty()) return false;                 // url.IsEmpty()
    if (url.charAt(0) == '/') {
        if (url.length() == 1) return true;                         // "/"
        return url.charAt(1) != '/' && url.charAt(1) != '\\';       // "/x" ok; "//x", "/\x" rejected
    }
    if (url.charAt(0) == '~') return url.length() > 1 && url.charAt(1) == '/';  // "~/x"
    return false;                                                   // relative ("bugs.aspx"), "http://…", "\\x" all false
}
```
Consequence, preserved: a relative `url=edit_bug.aspx` is **not** local and goes to `bugs.aspx`/`mbugs.aspx`. Translators mark `// BUG(port): relative returnUrl rejected by IsLocalUrl — repro: default.aspx?url=edit_bug.aspx`. Row 519 becomes `confirmed`: `UrlHelper urlHelper = new UrlHelper(Request.RequestContext()); if (Strings.IsNullOrEmpty(url) || !urlHelper.IsLocalUrl(url)) {…}`, and the TODO is removed.

**N3 (`dbutil.cs:98-104` reader outliving `using (SqlCommand)`).** Decision: this is a §3 runtime requirement.
- If `SqlCommand.close()` runs while a `SqlDataReader` it produced is open, it transfers ownership of the JDBC `Statement` to that reader and does not close it.
- `SqlDataReader.Close()/close()` closes the `ResultSet` and `Statement`, and the `SqlConnection` when `CommandBehavior.CloseConnection` is set.
- `SqlCommand.close()`, `SqlConnection.Close()/close()` and `SqlDataReader.close()` are idempotent. This also covers the source's "redundant" `conn.Close()` inside `using`.

Rows 475 and 476 stand as written, apart from J-C24.

**N4 (`version_control.cs:465`).** Decision: add the row `int comma_pos = Strings.IndexOf(line, ",", pos1);` (C27; pos1 = -1 must throw `ArgumentOutOfRangeException`). Also add the rows listed in J-C26 (`version_control.cs:589,590`, `bug_list.cs:321`, `Mime.cs:39,43`, `POP3Main.cs:664,667`).

**N5 (interrupt-based Abort, `POP3Main.cs:186-194,222`).** Decision: amend C19. In `fetching_thread_proc`, immediately after each `Threads.Sleep(2000)`, add `if (Thread.currentThread().isInterrupted()) { return; }`, with no "exiting fetching thread" log, as for an abort. Reason: `Threads.Sleep` re-sets the flag and returns at once, so without the check an interrupted thread busy-spins forever and every respawn leaks a polling thread. Row 872 already does this and stays. Add to C19's TODO that an interrupt does not stop blocking socket I/O, so an aborted thread caught inside `do_work` may finish its fetch concurrently with the respawned one: `TODO(port): Abort semantics`.

**N6 (comparator name).** Decision: closed. `Strings.CULTURE_COMPARER` is the only name; §2A LINQ and §3 already withdraw `CURRENT_CULTURE`. The one remaining inventory mention (row 492, evidence text) is evidence only; change it to `CULTURE_COMPARER` when next touched.

**N7 (`File.AppendText`/`StreamWriter`, `POP3Main.cs:153`).** Decision: add to §3 `btnet.runtime.io`:
- `File.AppendText(String path)` returns a runtime `StreamWriter` (`AutoCloseable`). It opens in append mode and creates the file if absent. Encoding is UTF-8 **without BOM** (.NET `File.AppendText`).
- `WriteLine(String)` writes the value + `Environment.NewLine` (`"\r\n"`). `Write(String)` writes it as is.
- `Close()`/`close()` flushes and is idempotent.
- A missing directory → runtime `DirectoryNotFoundException` (add to the exception list).

Row 873 drops its `TODO(port)`.

**N8 (C17 text).** Decision: closed. §2A C17 [CORRECTED] already reads one-shot `timer.schedule(task, interval)`, re-armed by `resume()` from `do_work`, with `scheduleAtFixedRate`/periodic `schedule` BANNED. Row 871 conforms (`Enabled = true` → `schedule`, `Stop()+Dispose()` / `Enabled = false` → `cancel()`). No further action.
