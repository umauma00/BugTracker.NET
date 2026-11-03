# Translation Rulebook — C# (ASP.NET WebForms / Web API, .NET 4.5) → Java 21 (Spring Boot 4.1 + Thymeleaf)

> **The meta-rule: if two agents could answer a question differently, the
> answer goes in this file.** This document is read by every implementer,
> reviewer, and fixer before they touch code. It is read-only inside any loop —
> amendments are queued for the human and applied between batches.

Read this whole document before writing any code. Sources of the binding
decisions: `migration/feasibility.md` §9 (gate 00), `migration/PROGRESS.md`
(standing mandate, gates 00b/01), `judge/README.md` "Judge contract",
`migration/judge-report.md` §5, `migration/depmap/REPORT.md`. Survey counts
below were measured on `src/` (`.cs` excluding `*.designer.cs`; `.aspx`
separately) on 2026-11-03.

## 0. Scope and posture

- **Two layers, two postures.**
  - **Core layer — structure-preserving.** Every non-page `.cs` unit
    (`btnet/`, `Security/`, `Mail/`, `Search/`, `Models/`, `Models/Mapping/`,
    `Controllers/`, `App_Start/`, `BasePage.cs`, `Startup.cs`,
    `src/btnet_service/*`) → one Java class, same type name (manifest target),
    same members, same member names, same SQL text. You are translating, not
    improving. The Step 2 bakeoff applies to these units.
  - **UI layer — redesigned.** Every `.aspx` (117), `.ascx` (3), `.Master` (2),
    `Global.asax`, `inc.aspx`, `inc_diff.inc` becomes one Spring MVC
    `@Controller` class (the manifest target, package `btnet.pages`, or
    `btnet.Controls` for `.ascx`) plus at most one Thymeleaf template. §2B is the
    design document for this layer. **The bakeoff is invalid for UI units**
    (README "If you're redesigning"; D-004); they are checked by adversarial
    review of §2B and the throwaway pilot instead. Inside a page's code
    (code-behind / `<script runat="server">`), the core rules of §2A still apply
    statement by statement: same SQL, same branches, same messages.
- Drafts do not need to compile. **Do not run the compiler or Maven** — javac
  is the only typechecker and it grades everything in Step 4 (no cheap
  referee exists for Java; `templates/settings.json` denies `mvn`/`javac`).
- First pass optimizes for **correct translation**, not performance. Known
  slow-but-faithful translations: mark `PERF(port):` and move on.
- **The old code is the spec, quirks included.** A translator who believes the
  source is wrong marks the site `BUG(port):` and reproduces the behavior.
  The only exceptions are the five fixes below.

### 0.1 Sanctioned behavior fixes (option (b), gate 00b — the complete list)

These five defects from `migration/judge-report.md` §5 are specified as
**fixed** behavior. Nothing else may be "fixed" by any agent. Each fixed site
carries the comment `// FIXED(port): F<n> — see RULEBOOK §0.1`.

| ID | Source | Original defect | Port behavior (exact) |
|---|---|---|---|
| F1 | `edit_bug.aspx.cs:100,142-146`; `Scripts/Site/EditBug.js:35-38,188-190`; `edit_bug.aspx:108-114,239-240` | Save runs only when `Request["user_hit_submit"]=="1"`, but no such element exists, so the browser never saves | (a) `did_user_hit_submit_button()` returns true iff **any** value of `user_hit_submit` in the query string or the form equals `"1"` (`Request.QueryString.GetValues` ∪ `Request.Form.GetValues`). (b) `templates/edit_bug.html` renders, inside the form, `<input type="hidden" id="user_hit_submit" name="user_hit_submit" value="1" disabled="disabled">`. (c) Both submit buttons (`submit_button`, `submit_button2`) get `onclick="document.getElementById('user_hit_submit').disabled=false;"` as an attribute (inline, so it runs before EditBug.js's jQuery click handler that disables the button and calls `form.submit()`). A project-dropdown autopostback therefore posts no `user_hit_submit` and only reloads users (:133-137). `EditBug.js` stays unchanged |
| F2 | `edit_bug.aspx.cs:1745-1748` (`AddChange`) | History INSERT lacks the closing `)` → syntax error | SQL text identical except `..., 'update')` — one `)` added, nothing else |
| F3 | `btnet/bug.cs:1181-1183` | `N''not sent'` → syntax error | `N'not sent'`; rest of the batch (delete + insert) unchanged |
| F4 | `Security/Security.cs:21-35,117` | Reads `dr["project_admin"]`, never selected → every non-admin login is a 500 | Add one select-list item after `u.us_admin` in `GetIdentity`'s SQL: `,\n       (select count(1) from project_user_xref pa where pa.pu_user = u.us_id and pa.pu_admin = 1) [project_admin]`. Line 117 unchanged (`(int)dr["project_admin"] > 0`) |
| F5 | `Security/Authenticate.cs:215` | `Util.update_user_password(1, "admin")` on every password check | Delete that one statement. Nothing else in `check_password_with_db` changes |

Not fixed (preserved, examples): `Security.cs:73` overwriting the
`CanOnlySeeOwnReportedBugs` claim from `us_enable_bug_list_popups`; lockout
`count > allowed` (`Authenticate.cs:116`); `LoggedOut.Master` inheriting
`btnet.LoggedIn`; `Rfc2898DeriveBytes` rejecting salts under 8 bytes (seeded
users with NULL salt → `isnull(us_salt,0)` → salt `"00"` → exception → 500).

## 1. Ecosystem adoption — what we use and what we ban

| Area | Decision | Why |
|---|---|---|
| Build | Maven. Parent `port/pom.xml` (packaging pom, Spring Boot 4.1.x BOM, Java 21). Modules: `port/runtime` (hand-written, §3), `port/web` (ALL translated web units, one module — the 8-package cycle in `depmap/package_cycles.txt` is legal inside one module), `port/service` (btnet_service). `web` and `service` depend on `runtime`; `runtime` depends on neither | depmap REPORT; gate 01 |
| Web framework | Spring MVC (servlet stack) + Thymeleaf. BANNED: WebFlux, Thymeleaf Layout Dialect, JSP, Spring Data, JPA/Hibernate, MyBatis, Lombok | one style; §2B |
| DB access | Plain JDBC through the runtime ADO.NET facade (`btnet.runtime.data`, §3). `mssql-jdbc` (same version as `judge/pom.xml`: 13.6.2.jre11), pooled by the Boot-managed HikariCP `DataSource`. BANNED in translated code: `JdbcTemplate`, `NamedParameterJdbcTemplate`, direct `java.sql` outside `DbUtil`/runtime | SQL must stay verbatim incl. T-SQL locals (`edit_bug.aspx.cs:747-756,1898-1903`) |
| EF6 (`Models/*`, `Models/Mapping/*`, `Context.cs`; 42 sites/13 files) | **No JPA.** Runtime `DbContext`/`DbSet<T>`/`EntityTypeConfiguration<T>` over JDBC; see §2A "EF6" | keeps 1 C# file → 1 Java file; ordering stays in SQL collation |
| JSON | Jackson as managed by the Boot 4.1 BOM (Jackson 3, `tools.jackson.*`; annotations `com.fasterxml.jackson.annotation.*`). One mapper, configured in runtime `JsonConfig` (§2A JSON) | replaces Newtonsoft 6.0.4 |
| Logging | SLF4J + Logback (Boot default). NLog `Logger`/`LogManager` → §2A | |
| Mail | Jakarta Mail via `spring-boot-starter-mail` (send) and Eclipse Angus Mail POP3 provider (service). Wrapped by runtime `btnet.runtime.mail` | System.Net.Mail, OpenPop |
| Search | `IBugSearch` kept; `BugSearch` talks to Elasticsearch over `java.net.http.HttpClient` + Jackson (no ES client library) | NEST 1.0.2 has no compatible Java client for ES 1.x |
| LDAP | JNDI (`javax.naming.ldap`), JDK only | System.DirectoryServices.Protocols |
| Processes | `java.lang.ProcessBuilder` | `version_control.cs` shell-outs |
| XML | JDK StAX (`javax.xml.stream`) | `POP3Main.cs` XmlTextReader |
| HTTP client | `java.net.http.HttpClient` | `POP3Main.cs` HttpWebRequest; ES |
| Async/concurrency | BANNED: `@Async`, executors, `CompletableFuture`, virtual threads. `new Thread(proc).Start()` → `new Thread(proc).start()` (platform thread, same structure) | 3 thread sites (`bug.cs:1201`, `tags.cs:119,125`, `email.cs:273`) |
| String policy | `java.lang.String` everywhere; text I/O UTF-8; `Environment.NewLine` = `"\r\n"` (judge S5 asserts `\n\r\n`) | |
| Third-party deps | NONE beyond: Spring Boot starters (webmvc, thymeleaf, security, jdbc, mail, validation), mssql-jdbc, angus-mail, Jackson, SLF4J/Logback. Apache Commons, Guava etc. BANNED — use runtime helpers. Exact starter artifact ids are fixed by the runtime author in `port/pom.xml`; translators never edit poms | |
| Error model | see §2A "Exceptions" | |

## 2. Constructs with no equivalent — one canonical mapping each

### 2A. Core constructs (apply in every unit, core and UI code)

**General conventions**

| Source construct | Canonical Java translation | Notes / evidence |
|---|---|---|
| Type/member names | Identical to C# (`get_setting`, `AttemptLogin`, `us_id`). No camelCase renaming. If the name is a Java keyword, append `_` (`default` → `default_`) | §4 |
| Namespace `btnet.X` | package `btnet.X` (same case: `btnet.Security`, `btnet.Models.Mapping`) | manifest |
| `static class C` (5) | `public final class C { private C() {} }` with static members | `ClaimsIdentityExtensions`, `WebApiConfig` |
| `partial class` (148, pages+designer, `Startup`) | one Java class; `Startup.cs` + `App_Start/Startup.Auth.cs` → one `btnet/Startup.java`, one batch | depmap cycle 2 |
| `const T X = v` (55) | `public static final T X = v;` (keep access modifier) | `BtnetRoles.Admin` |
| `readonly` (7) | `final` | |
| Auto-property `public T X { get; set; }` (284/31 files) | **public field** `public T X;` (same name, same initializer). `{ get; private set; }` → public field too | `LoginResult.Success` |
| Property with an accessor body | getter → method `T X()`; setter → `void X(T value)` | `Query.ColumnNames` → `String[] ColumnNames()` |
| Property of a **runtime** facade type (DataTable, controls, HttpRequest…) | same method convention: read `x.Value()`, write `x.Value(v)` | `row_id.Value = id` → `row_id.Value(id)` |
| Page/UserControl/MasterPage intrinsics `Request, Response, Server, Session, Application, Context, Cache, User, IsPostBack, ViewStateUserKey, Page, Header, Master` | **fields** of the runtime base class, used bare: `Response.Write(x)`, `IsPostBack`, `ViewStateUserKey = Session.SessionID();` | only exception to the property rule |
| Indexer read `a[k]` / write `a[k] = v` on runtime types | `a.get(k)` / `a.set(k, v)` | `dr["us_id"]` → `dr.get("us_id")`; `Session["page"] = p` → `Session.set("page", p)` |
| BCL type that maps to a JDK type (String, List, Exception…) | use the JDK API (`s.length()`, `list.size()`, `e.getMessage()`) | |
| Extension method `x.M(a)` (31, all in `ClaimsIdentityExtensions`) | class-qualified static call `ClaimsIdentityExtensions.M(x, a)`; no static imports | `User.Identity.GetUserId()` → `ClaimsIdentityExtensions.GetUserId(User.Identity())` |
| `var` (149) | Java `var` allowed for locals with an initializer | |
| `params T[]` (1) | varargs `T...` | `PageAuthorizeAttribute` |
| Attribute class + usage (`[PageAuthorize]`, `[PageAllowAnonymous]`) | Java annotation with `@Retention(RUNTIME) @Target(TYPE)`; logic of the C# attribute moves to a nested `public static final class Logic` in the same file. `PageAuthorizeAttribute`: `String[] value() default {}`, `@Repeatable` container `PageAuthorizeAttributes`. Usage `[PageAuthorize(BtnetRoles.Admin)]` → `@PageAuthorizeAttribute(BtnetRoles.Admin)` | `BasePage.cs:7-30` |
| `Attribute.GetCustomAttributes(GetType(), typeof(A))` (inherit=true) | walk `getClass()` and every superclass, collecting `getDeclaredAnnotationsByType(A.class)` (Java `@Inherited` drops the superclass annotation when the subclass has one; C# returns both — admin pages need User **and** Admin) | `BasePage.cs:23,29-30` |
| `ref`/`out` parameter (43 sites/13 files) | runtime `btnet.runtime.Ref<T>` (public field `value`); caller: `Ref<String> sv = new Ref<>(sort_val); f(sv); sort_val = sv.value;` — write back immediately after the call | `bug_list.cs:187-190`, `Mime.cs:27`, `version_control.cs:175,393` |
| `T?` / `Nullable<T>` (7) | boxed type (`Integer`, `LocalDateTime`); `.HasValue` → `!= null`; `.Value` → unboxing | `Models/BugFromEmail.cs:11-30` |
| `a ?? b` (12, `BugFromEmailController.cs:115-130`) | `btnet.runtime.Nulls.coalesce(a, b)` | |
| `cond ? a : b` | Java ternary (watch unboxing of `null` → use boxed result type) | |
| `using (var x = …) { }` (34/18) | try-with-resources; the resource type must be `AutoCloseable` (runtime facades are) | `dbutil.cs:24` |
| `lock (o) { }` (3) | `synchronized (o) { }` | `bug.cs:1276`, `whatsnew.cs:42` |
| `Thread.Sleep(ms)` | `btnet.runtime.Threads.Sleep(ms)` (wraps `InterruptedException`, re-sets interrupt flag) | `email.cs:300` |
| `delegate(...) {}` / lambda `=>` | Java lambda / method reference; event handlers `void X(Object sender, EventArgs e)` | `print_bug.cs:948` |
| `switch` on string (2) | Java `switch`, preceded by `if (s == null) { <default branch> }` (C# routes null to default; Java throws) | |
| `foreach (DataRow dr in t.Rows)` (28/19) | `for (DataRow dr : t.Rows())` | `bug.cs:165` |
| `foreach (DataRowView drv in dv)` (5/5) | `for (DataRowView drv : dv)` | |
| `int`/`long`/`bool`/`double`/`decimal`/`DateTime`/`TimeSpan`/`char`/`byte[]` | `int`/`long`/`boolean`/`double`/`java.math.BigDecimal`/`java.time.LocalDateTime`/`java.time.Duration`/`char`/`byte[]` | integer overflow wraps in both (C# unchecked default) |
| `List<T>` / `Dictionary<K,V>` / `HashSet<T>` / `LinkedList<T>` | `ArrayList` / **`LinkedHashMap`** / **`LinkedHashSet`** / `java.util.LinkedList` (insertion order = .NET's observable order without removals) | `Authenticate.cs:80` LinkedList |
| `Dictionary` read indexer `d[k]` | `btnet.runtime.Collections2.indexer(d, k)` — throws `KeyNotFoundException` when absent (C# semantics); `TryGetValue` → `containsKey`+`get` | |
| `Hashtable` / `ArrayList` (non-generic) | `HashMap<Object,Object>` (`get` returns null when absent, like C#) / `ArrayList<Object>` | iteration order of Hashtable differs: if output depends on it, mark `BUG(port):` |
| `NameValueCollection` / `StringDictionary` | runtime `NameValueCollection` (case-insensitive keys, multi-value, comma-joined `get`) / `HashMap` with lower-cased keys | |
| LINQ-to-objects (33/15) | `java.util.stream`: `Where`→`filter`, `Select`→`map`, `Any`→`anyMatch`, `ToList`→`collect(toCollection(ArrayList::new))`, `First()`→`findFirst().orElseThrow()` (throws like C#), `FirstOrDefault()`→`findFirst().orElse(null)`, `OrderBy(k)`→`sorted(comparing(k))` stable; **string keys** use `btnet.runtime.Strings.CURRENT_CULTURE` comparator (en-US collation), never natural order | `query.cs:24` `Except` → filter preserving order, distinct |

**Strings**

| Source construct | Canonical Java translation | Notes |
|---|---|---|
| `a == b`, `a != b` on strings (340/48) | `Objects.equals(a, b)` / `!Objects.equals(a, b)` (ordinal) | `Util.get_setting("LogSqlEnabled","1") == "1"` |
| String concatenation `x + y` | every operand that is not a string literal, `char`, or an `int`/`long` expression is wrapped in `Strings.cs(o)` = C# concat semantics (null/DBNull → `""`, else `Convert.ToString(o)`) | Java prints `null`/`true`/`2020-01-02T03:04:05`; C# prints ``/`True`/culture date |
| `o.ToString()` on non-String `o`; `Convert.ToString(o)` (356/50) | `Convert.ToString(o)` (runtime): null→`""`... see Convert table | |
| `string.Format` (24/10), `StringBuilder.AppendFormat` | `Strings.Format(fmt, args...)` — .NET composite formatting (`{0}`, `{0:00}`, `{0:X2}`, `{{`/`}}`). `java.lang.String.format` BANNED | `BugQueryExecutor.cs:43`, `edit_task.aspx.cs:210` |
| `string.IsNullOrEmpty` (40/18) | `Strings.IsNullOrEmpty(s)` | |
| `s.Length`, `s[i]` | `s.length()`, `s.charAt(i)` | |
| `s.Substring(a)` / `s.Substring(a, n)` (32/12) | `s.substring(a)` / `s.substring(a, a + n)` | length → end index |
| `s.Split(c)` / `s.Split(chars, RemoveEmptyEntries)` (16/9) | `Strings.Split(s, c...)` / `Strings.Split(s, StringSplitOptions.RemoveEmptyEntries, c...)` (keeps trailing empties; never regex) | `String.split` BANNED |
| `s.Replace(a, b)` (139/21) | `s.replace(a, b)` (literal) | |
| `s.Trim()`/`TrimStart`/`TrimEnd` | `Strings.Trim(s)` etc. (.NET `Char.IsWhiteSpace` set, includes U+00A0) | `String.trim()` BANNED |
| `ToLower()`/`ToUpper()` | `toLowerCase(Locale.US)` / `toUpperCase(Locale.US)` | |
| `IndexOf(string)`/`StartsWith`/`EndsWith`/`Contains` | Java ordinal equivalents; `IndexOf(s, StringComparison.X)` → `Strings.IndexOf` | .NET culture-sensitive variants differ only on ignorable chars — accepted |
| `@"verbatim"` (238/57) | Java text block `"""` only if the content has no trailing-space or `\` hazards; otherwise ordinary literal with `\n` escapes. **SQL text must be byte-identical** (except §0.1); `""` inside verbatim → `"` | `bug.cs:1178-1183` |
| `Environment.NewLine` | `Environment.NewLine` (runtime constant `"\r\n"`) | |
| `Encoding.UTF8.GetBytes/GetString` | `Encoding.UTF8.GetBytes/GetString` (runtime; lossy decode like .NET, §3) | `util.cs:496-498` |

**Conversions and parsing (runtime `btnet.runtime.Convert`, same method names)**

| C# | Java | Semantics the runtime implements |
|---|---|---|
| `Convert.ToInt32(object)` (134/41) | `Convert.ToInt32(o)` | null → 0; DBNull → `InvalidCastException`; String → `Int32.Parse`; Integer/Short/Byte/Long → narrowing with `OverflowException`; Double/BigDecimal → round-half-even then range check; Boolean → 1/0 |
| `int.Parse(s)` / `Int32.Parse` (2) | `Int32.Parse(s)` | null → `ArgumentNullException`; allows leading/trailing whitespace and one leading sign; else `FormatException`; overflow → `OverflowException` |
| `Convert.ToBoolean(o)` (81/13) | `Convert.ToBoolean(o)` | null → false; String: trimmed, case-insensitive `"True"`/`"False"` else `FormatException`; numbers: ≠0 |
| `Convert.ToString(o)` | `Convert.ToString(o)` | null/DBNull → `""`; Boolean → `"True"`/`"False"`; LocalDateTime → en-US `"G"` (`M/d/yyyy h:mm:ss tt`); BigDecimal → `toPlainString()`; Double → .NET `"R"`-compatible shortest round-trip (`TODO(port)` verify vs .NET 4.5 "G15" if a double ever reaches output) |
| `Convert.ToDateTime`, `Convert.ToDecimal`, `decimal.Parse`, `decimal.TryParse(..., out x)` | same-named runtime methods (`Decimal.TryParse(s, styles, culture, Ref<BigDecimal>)`) | culture en-US |
| `DateTime.Parse(s, culture)` | `DateTimes.Parse(s, culture)` | `TODO(port)`: .NET's permissive parser is approximated by a fixed pattern list in the runtime |
| `(int)o` cast of boxed object | `(int) o` (Java unboxing cast; ClassCastException ≈ InvalidCastException) | DataTable values keep .NET types (§3), so `(int)dr["x"]` fails exactly where C# fails (smallint→Short, scope_identity→BigDecimal, DBNull) |
| `(string)o` | `(String) o` | DBNull → ClassCastException, as in C# |
| `Convert.IsDBNull(o)`, `o == DBNull.Value` (11/8) | `o == DBNull.Value` (runtime singleton, `toString()` → `""`) | `Security.cs:39` |

**Dates, numbers, culture**

- Culture is **en-US** everywhere (`Web.config:47` has no `<globalization>`;
  thread culture = server default, assumed en-US). `Util.get_culture_info()` and
  `CultureInfo.CurrentCulture` → `CultureInfo.EN_US` (runtime).
- `DateTime` → `LocalDateTime` (no zone). `DateTime.Now` (12/5) →
  `LocalDateTime.now()`. `AddHours/AddMinutes/AddDays` → `plusHours/...`.
  `.Year/.Hour` → `getYear()/getHour()`.
- `dt.ToString(fmt[, culture])`, `n.ToString("00")`, `{0:...}` →
  `DotNetFormat.Format(value, fmt, culture)` implementing .NET standard
  (`g`,`G`,`d`,`D`,`t`,`T`,`s`) and custom specifiers (`yyyy yyy yy MM M dd d
  HH H hh h mm m ss s fff ff f tt t : / \ 'literal'`; numeric `0 # . , X2`).
  `DateTimeFormatter`/`DecimalFormat` are BANNED in translated code.
  Evidence: `Web.config:229-230` `yyyy-MM-dd h:mm tt`; `edit_bug.aspx.cs:295`
  `yyyyMMdd HH\:mm\:ss\:fff`; `util.cs:99` `yyy-MM-dd`.

**Regex (49 sites/6 files)**

- `new Regex(p[, opts])`, `Regex.Replace/Match/IsMatch/Split` →
  `btnet.runtime.DotNetRegex` with the same method names. It compiles with
  `UNIX_LINES | UNICODE_CHARACTER_CLASS` (+ `CASE_INSENSITIVE|UNICODE_CASE` for
  `IgnoreCase`, `MULTILINE`, `DOTALL` for `Singleline`), converts .NET
  replacement syntax (`$1`, `${name}`, `$$`; `\` literal) and quotes
  `MatchEvaluator` results. `Split` keeps trailing empties and captured groups.
  Direct `java.util.regex` use and `String.replaceAll/split` are BANNED.
  Evidence: `util.cs:216` `<(.|\n)*?>`; `util.cs:228-253`; `print_bug.cs:948`.

**Exceptions**

- Translated methods declare **no checked exceptions**. Checked exceptions
  from JDK APIs are caught at the call and rethrown via
  `throw Exceptions.wrap(e)`; `SQLException` becomes runtime `SqlException`
  (unchecked, message = server message text, like .NET `SqlException.Message`).
- .NET exception types used in `catch`/`throw` map to runtime classes of the
  same name in `btnet.runtime` (`FormatException`, `OverflowException`,
  `InvalidCastException extends ClassCastException`, `ArgumentException`,
  `ArgumentNullException`, `KeyNotFoundException`, `HttpException`,
  `ConfigurationErrorsException`, `NullReferenceException` → use
  `NullPointerException`). `catch (Exception e)` stays `catch (Exception e)`.
- `throw;` → `throw e;` (same variable). `e.Message` → `e.getMessage()`;
  `e.StackTrace` → `Exceptions.StackTrace(e)`; `e.InnerException` →
  `e.getCause()`.
- `Response.End()`, `Response.Redirect(url)` and `Server.Transfer(p)` throw
  runtime `ResponseEndException`/`TransferException`, which **extend
  `java.lang.Error`** (ThreadAbortException analogue) so `catch (Exception)`
  does not swallow them. Where C# code puts statements in a `catch` that would
  have run before the ThreadAbort re-throw, mark `TODO(port): catch body
  skipped on Response.End`.

**Database (`DbUtil`, `SQLString`, ADO.NET)**

- `SQLString.cs` and `dbutil.cs` are **translated** (manifest units) on top of
  the runtime ADO.NET facade: `SqlConnection`, `SqlCommand`,
  `SqlParameter`, `SqlParameterCollection`, `SqlDataAdapter`,
  `SqlDataReader`, `SqlDbType`, `CommandBehavior` — same member names, so
  `dbutil.cs:24-31` translates line for line:
  ```java
  try (SqlConnection conn = GetConnection()) {
      SqlCommand cmd = new SqlCommand(sql.toString(), conn);
      cmd.Parameters().AddRange(sql.GetParameters().toArray(new SqlParameter[0]));
      Object returnValue = cmd.ExecuteScalar(); ...
  ```
- `override ToString()` → `@Override public String toString()`.
- **Parameter binding (runtime `SqlBatch`, decided once):** SQL text is never
  rewritten. A command with N parameters is sent as one `PreparedStatement`
  whose text is `DECLARE @p1 <type> = ?, …, @pN <type> = ?;\n` + the original
  SQL. This reproduces SqlClient's `sp_executesql` scoping exactly: a
  parameter used twice binds once, T-SQL may reassign it, locals such as
  `declare @cnt int` (`delete_priority.aspx.cs:41`) are untouched, and a
  missing parameter fails with the same "Must declare the scalar variable"
  error. Types follow SqlClient inference: String → `nvarchar(4000)` (or
  `nvarchar(max)` if longer), Integer → `int`, Long → `bigint`, Boolean →
  `bit`, BigDecimal → `decimal(38,s)`, LocalDateTime → `datetime`,
  `byte[]` → `varbinary(max)`, DBNull/null → `nvarchar(4000)` NULL; an explicit
  `SqlDbType` (the 5 `new SqlCommand` sites, `bug.cs:365`) wins. Zero
  parameters → the text is sent unmodified (`Statement.execute`), so DDL
  batches still work.
- `SQLString.AddParameterWithValue(name, null)` → DBNull (`SQLString.cs:33`);
  `@` prefix added when missing — keep both overloads.
- Results: `SqlDataAdapter.Fill(ds)` adds one `DataTable` per result set
  (`Table`, `Table1`, …), skipping update counts. Column values carry .NET
  types: int→`Integer`, smallint→`Short`, tinyint→`Byte`→ use `Short`
  (unsigned), bigint→`Long`, bit→`Boolean`, decimal/numeric/money→`BigDecimal`,
  float→`Double`, real→`Float`, date/datetime/datetime2/smalldatetime→
  `LocalDateTime`, (n)char/(n)varchar/(n)text→`String`,
  (var)binary/image→`byte[]`, uniqueidentifier→`java.util.UUID`; SQL NULL →
  `DBNull.Value` (never Java `null`).
- `ExecuteScalar` → first column of first row of first result set; no rows →
  `null`; SQL NULL → `DBNull.Value`.
- `DbUtil.GetConnection()` keeps its body; runtime `new SqlConnection(String)`
  ignores a non-`jdbc:` .NET connection string and borrows from the Spring
  `DataSource` (`spring.datasource.url`, env `SPRING_DATASOURCE_URL`); a
  `jdbc:` string is used as-is. `Open()` is a no-op after construction.
- Transactions: none in source; none in port.

**DataSet / DataTable / DataView / DataRow (≈260 sites/103 files)**

- One compatibility family, runtime package `btnet.runtime.data`:
  `DataSet`, `DataTable`, `DataColumn`, `DataColumnCollection`, `DataRow`,
  `DataRowCollection`, `DataView`, `DataRowView`. Map mechanically:

  | C# | Java |
  |---|---|
  | `ds.Tables[0]` | `ds.Tables().get(0)` |
  | `dt.Rows.Count`, `dt.Rows[i]` | `dt.Rows().Count()`, `dt.Rows().get(i)` |
  | `dr["col"]`, `dr[i]`, `dr["col"] = v` | `dr.get("col")`, `dr.get(i)`, `dr.set("col", v)` |
  | `dt.Columns[i].ColumnName`, `.DataType` | `dt.Columns().get(i).ColumnName()`, `.DataType()` (a `Class<?>`) |
  | `new DataView(dt)`, `dt.DefaultView`, `dv.Count`, `dv[i]`, `dv.Table`, `dv.Sort`, `dv.RowFilter`, `dv.Find(k)` | `new DataView(dt)`, `dt.DefaultView()`, `dv.Count()`, `dv.get(i)`, `dv.Table()`, `dv.Sort(s)`, `dv.RowFilter(s)`, `dv.Find(k)` |
  | `dt.NewRow()`, `dt.Rows.Add(dr)`, `dt.Columns.Add(new DataColumn("file", typeof(String)))` | `dt.NewRow()`, `dt.Rows().Add(dr)`, `dt.Columns().Add(new DataColumn("file", String.class))` |
  | `ds.Tables[0].AsEnumerable()` | `ds.Tables().get(0).Rows().stream()` |

- Column lookup is case-insensitive (exact match first). Unknown column →
  `ArgumentException("Column 'x' does not belong to table T.")`.
  `DataView.Find` without a `Sort` → `ArgumentException` (inherited quirk at
  `edit_user.aspx.cs:233`). `RowFilter` supports only the expression forms
  found in source; anything else → runtime throws
  `UnsupportedOperationException("TODO(port) RowFilter")`.
- `typeof(T)` → `T.class`; `Util.is_numeric_datatype(Type)` takes `Class<?>`.
- `DataTable` JSON (Web API `BugQueryResult.Data`): array of objects, one per
  row, properties = column names in column order, DBNull → `null`,
  `LocalDateTime` → `"yyyy-MM-ddTHH:mm:ss[.fffffff trimmed]"` (Json.NET
  `DateTimeKind.Unspecified`, judge S2:48-50). Runtime `DataTableSerializer`.

**EF6 (`Context.cs`, 23 entities, 23 maps)**

- Entities (`Models/*.cs`): auto-properties → public fields (§2A). Navigation
  properties (`ICollection<BugTask>`) → public fields, **never loaded**
  (EF lazy loading is not used by any call site; if a site reads one,
  `TODO(port): navigation property`).
- Maps (`Models/Mapping/*Map.cs`) extend runtime `EntityTypeConfiguration<T>`;
  lambdas become field-name strings:
  `this.Property(t => t.Id).HasColumnName("qu_id")` →
  `this.Property("Id").HasColumnName("qu_id");`
  `this.HasKey(t => t.Id)` → `this.HasKey("Id")`; `ToTable`, `IsRequired`,
  `HasMaxLength`, relationships → same-named no-op-or-metadata methods.
- `Context : DbContext` → `Context extends DbContext implements
  AutoCloseable`; `DbSet<T>` properties → public fields initialized by
  `OnModelCreating` registration. `new Context()` uses the shared DataSource.
- Query surface (only what the source uses):
  `Find(key)` → `select <all mapped columns> from <table> where <key> = @p`
  (explicit column list, so a missing column fails exactly as EF did — seed
  patch P2); `OrderBy(x => x.Name).ToList()` → `.OrderBy("Name").ToList()`
  (ORDER BY the mapped column — collation ordering, never in-memory);
  `.ToArray()` → `.ToList().toArray(...)`; `Where(lambda)` → `.Where(String
  sqlPredicate, SqlParameter...)` written over mapped column names with EF6
  null semantics (`q.User == null || q.User == userId` →
  `"(qu_user is null or qu_user = @userId)"`) — `Context.cs:46-49` is the
  only site.

**Web intrinsics in core code**

| C# | Java (runtime `btnet.runtime.web`) |
|---|---|
| `HttpContext.Current` (38/13) | `HttpContext.Current()` — ThreadLocal set by the request filter; `null` on background threads (as in .NET) |
| `HttpResponse Response` parameter (`print_bug.cs:38,305`, `bug_list.cs:427`, `util.cs:119,1163,1343`) | keep the parameter, type `btnet.runtime.web.HttpResponse`. `new HttpResponse(stringWriter)` (`bug.cs:1159`, `send_email.aspx.cs:484`) is supported by the runtime — **do not** change signatures to `Writer` |
| `HttpRequest Request` parameter | `btnet.runtime.web.HttpRequest` |
| `Request["k"]` (83/31) | `Request.get("k")`: QueryString → Form → Cookies → ServerVariables, first hit, multi-values comma-joined, `null` if absent |
| `Request.QueryString["k"]`, `Request.Form["k"]`, `.GetValues` | `Request.QueryString().get("k")`, `Request.Form().get("k")`, `.GetValues("k")` |
| `Request.RawUrl`, `Url`, `Path`, `UserHostAddress`, `Cookies`, `Files`, `ServerVariables` | same-named methods |
| `Session["k"]` (24/8), `Session.SessionID`, `Session.Abandon()` | `Session.get("k")` / `.set` / `.SessionID()` / `.Abandon()` over `HttpSession` (timeout 120 min, `Web.config:30`) |
| `Application["k"]` (11/5), `Util.context.Application` | `HttpApplicationState` singleton (ConcurrentHashMap; `Lock()/UnLock()` = a ReentrantLock) |
| `HttpRuntime.Cache[k]` (7/3) | `HttpRuntime.Cache().get/set` — ConcurrentHashMap, no expiry (`Authenticate.cs:84-157`) |
| `Server.MapPath(p)` (4/3), `Util.GetAbsolutePath` | `Server.MapPath(p)` → path under `btnet.webroot` (default `port/web/webroot`); `MapPath(null)` → webroot |
| `HttpUtility.HtmlEncode` (16/8), `Server.HtmlEncode` | `HttpUtility.HtmlEncode`: null→null; encodes `< > & " '`(`&#39;`) and U+00A0–U+00FF as `&#NNN;` (.NET 4.x) |
| `HttpUtility.HtmlDecode` | HTML 4 named + numeric entities |
| `HttpUtility.UrlEncode` (5/4) | UTF-8, space→`+`, unreserved `A-Za-z0-9-_.!*()`, **lower-case** hex (`%2f`) |
| `HttpUtility.UrlDecode` | `+`→space, invalid `%` sequences kept literally |
| `Uri.EscapeDataString` | RFC 3986 unreserved kept, upper-case hex |
| `File.*`, `Directory.*`, `Path.*`, `StreamReader`/`File.OpenText` (35/13) | runtime `btnet.runtime.io.File/Directory/Path` with the same static API; they normalize `\` → `/` (source builds `"App_Data\\uploads"`, `Global.asax.cs`) |
| `Request.Files` / `PostedFile` (6/2) | runtime `HttpPostedFile` (`FileName()`, `ContentLength()`, `ContentType()`, `InputStream()`, `SaveAs(path)`) over Spring `MultipartFile`; limits 50 MB (`maxRequestLength=51200`, `Web.config:46`) |

**Settings (`Util.get_setting`)**

- `ConfigurationManager.GetSection("appSettings")` →
  `ConfigurationManager.GetSection("appSettings")` (runtime) returning a
  case-insensitive `NameValueCollection` loaded from
  `port/web/src/main/resources/btnet-settings.properties` (UTF-8).
- That file is **generated once by the runtime author** from the
  *uncommented* `<add key value>` entries of `src/BugTracker.Web/Web.config`
  `<appSettings>` (lines 97-975, 156 entries) by runtime tool
  `btnet.runtime.config.WebConfigToProperties`; it is not hand-edited.
  Override: Spring property `btnet.setting.<Key>` (e.g. env
  `BTNET_SETTING_SearchServerURI`) wins over the file. `system.net/mailSettings`
  → `btnet.mail.*` in `application.properties` (`deliveryMethod`,
  `pickupDirectory`, host/port/user/password/ssl).
- `get_setting` keeps its own empty→default logic (`util.cs:127-141`).

**Logging, mail, search, processes, LDAP, SOAP, POP3**

| C# | Java |
|---|---|
| `LogManager.GetCurrentClassLogger()` + `log.Debug/Info/Fatal(x)` | `LoggerFactory.getLogger(<EnclosingClass>.class)` + `debug/info`; `Fatal` → `error(MarkerFactory.getMarker("FATAL"), …)` |
| `App_Start/LoggingConfig.cs` (NLog file + mail targets) | programmatic Logback in `LoggingConfig.Configure()`: rolling file `<log folder>/btnet_log.txt`, daily, level TRACE if `LogEnabled=1` else OFF. Mail target → `TODO(port): FATAL mail appender` |
| `SmtpClient`/`MailMessage`/`Attachment` (14/3) | runtime `btnet.runtime.mail.SmtpClient/MailMessage/Attachment/MailAddress` (same members) over Jakarta Mail; `deliveryMethod=specifiedPickupDirectory` writes `<dir>/<uuid>.eml` (active setting, `Web.config:52-56`). `ServicePointManager.ServerCertificateValidationCallback = …true` (`email.cs:242`) → runtime `SmtpClient.TrustAllCertificates(true)` |
| OpenPop `Message`, `MessagePart`, `Pop3Client` (`Mime.cs`, `POP3Main.cs`) | runtime `btnet.runtime.mail.pop.Message/MessagePart/Pop3Client` (OpenPop member names) over Jakarta Mail `MimeMessage` / POP3 store |
| NEST `ElasticClient` (`Search/*`) | `BugSearchFactory` builds runtime `ElasticClient(uri, "btnet")`; `BugSearch` issues: index `PUT {uri}/btnet/bug/{id}` with the same document fields; search `POST {uri}/btnet/bug/_search` `{"size":500,"query":{"query_string":{"query":…}},"highlight":{"pre_tags":["<span class='highlighted'>"],"post_tags":["</span>"],"fields":{"*":{}}}}`. `TODO(port): verify NEST 1.0.2 wire format`. Keep `IndexBug`'s try/catch: connection failures are logged, never thrown |
| `System.Diagnostics.Process` (`version_control.cs:30-70`) | `ProcessBuilder(filename + split args)`, working dir, read stdout then stderr fully (UTF-8), `waitFor()`. Argument splitting: runtime `Processes.SplitArgs` (Windows `CommandLineToArgvW` rules) |
| `Stopwatch` | runtime `Stopwatch` (`Start/Stop/ElapsedMilliseconds()`) |
| `LdapConnection.Bind` (`Authenticate.cs:176-207`) | JNDI `InitialLdapContext` per DN; `LdapAuthType=Basic` → `simple`; other values → `TODO(port)`; failure → exception → caught as in source |
| `TranslationService` (wsdl SOAP client, `btnet/TranslationService.cs`) | same class and members; every remote call throws `UnsupportedOperationException("TODO(port): SOAP translation service")`. `translate.aspx` keeps its error path |
| `HashAlgorithm.Create("MD5")` (`UpdatePassword.aspx.cs:64`) | `MessageDigest.getInstance("MD5")` |
| `ServiceBase` (`btnet_service.cs`) | `service` keeps `OnStart/OnStop` and gets `public static void main`; installer/controller members → `TODO(port): Windows service install` stubs |
| `XmlTextReader` (`POP3Main.cs:316+`) | StAX `XMLStreamReader` reading the same config file format |
| `HttpWebRequest` (`POP3Main.cs:734-800`) | `java.net.http.HttpClient`, same URL/body/headers |

**JSON / Web API (`Controllers/*.cs`, 3)**

- `ApiController` → `@RestController` extending runtime `ApiController`
  (fields `User`, `Request`, `ModelState`). Route `api/{controller}/{id}`
  (`WebApiConfig.cs:14-18`) → `@RequestMapping("/api/<Name minus
  Controller>")` (case-insensitive, §2B); action by HTTP verb (`Get` →
  `@GetMapping`, `Post` → `@PostMapping`).
- Simple parameters bind from the query string by name (case-insensitive);
  `[FromUri] T[] filters` → runtime `FromUri.bindArray(Request, "filters",
  T.class)` accepting `filters[i][Prop]` and `filters[i].Prop`; absent →
  empty array (`TODO(port): verify Web API 5.2.3 gives empty vs null`).
  Missing required simple parameter → 404 (`TODO(port)` verify).
- `[FromBody] T` → runtime `ApiBody.bind(Request, T.class)`: JSON or
  form-urlencoded, case-insensitive property names. `[Required]` →
  `@Required` (runtime annotation) checked by `ModelState`; invalid → `400`
  body `{"Message":"The request is invalid.","ModelState":{"<param>.<Prop>":["The <Prop> field is required."]}}` (judge S5:50-55).
- `Ok()` → 200 empty; `Ok(x)` → 200 JSON; `NotFound()` → 404 empty;
  `StatusCode(HttpStatusCode.Forbidden)` → 403 empty; unhandled exception →
  500. Anonymous objects → `LinkedHashMap` built in declared order
  (`BugQueryController.cs:20-25`: `recordsTotal`, `recordsFiltered`, `data`).
- Jackson config (`JsonConfig`): field visibility ANY, getter auto-detection
  NONE (public fields = C# auto-properties, names as declared, PascalCase);
  computed properties that Newtonsoft would emit get `@JsonProperty("Name")`;
  case-insensitive property binding; `byte[]` as base64; `LocalDateTime`
  as above. Response `application/json; charset=utf-8` regardless of
  `Accept` (XML negotiation → `TODO(port)` not supported).

**The BUG rule:** when the source's behavior is itself defective (and not in
§0.1), reproduce it and mark `// BUG(port): <what> — repro: <input>`. Fixes
ship only after parity, via prompt 06.

**The UNKNOWN rule:** when neither this file nor `migration/inventory.tsv`
decides a case, translate to the most conservative representation, mark
`TODO(port): <open question>`, keep moving.

**Error-recovery rule (this repo):** the source uses `null` returns for two
different things — "not found" (`DbUtil.get_datarow` returns null unless
exactly one row, `dbutil.cs:185-194`) and "failure" (exceptions). Keep that:
never turn a `null`-return into an exception or an exception into a `null`.

### 2B. UI layer design (pages, masters, user controls, includes)

This section is the design document for the redesigned layer. It decides how
each WebForms concept is expressed so that every page is translated the same
way.

**B1. Unit shape.** One page = one Java class (manifest target) + one
template. The class combines `<page>.aspx.cs`, `<page>.aspx.designer.cs` and
any `<script runat="server">` in the `.aspx`:

```java
// Source: src/BugTracker.Web/delete_priority.aspx (+ .aspx.cs, .aspx.designer.cs)
package btnet.pages;
@Controller
@RequestScope                                   // one instance per request: code-behind fields are per-request state
@PageAuthorizeAttribute(BtnetRoles.Admin)       // from [PageAuthorize(BtnetRoles.Admin)]
public class delete_priority extends BasePage {
    public final LoggedIn Master = master(new LoggedIn());          // MasterPageFile + MasterType
    public final HtmlAnchor confirm_href = control(new HtmlAnchor("confirm_href"));
    public final HtmlInputHidden row_id = control(new HtmlInputHidden("row_id"));
    SQLString sql;
    @RequestMapping(path = "/delete_priority.aspx", method = {GET, POST, HEAD})
    public void handle(HttpServletRequest req, HttpServletResponse res) {
        ProcessRequest(req, res, "delete_priority");               // template name, or null if none
    }
    @Override protected void Page_Init(Object sender, EventArgs e) { ViewStateUserKey = Session.SessionID(); }
    @Override protected void Page_Load(Object sender, EventArgs e) { ... }
}
```

- `BasePage` (translated core unit) extends runtime `btnet.runtime.web.Page`;
  pages that do not inherit `BasePage` in C# extend `Page` directly.
- Class name = file stem (manifest), even when `Inherits` differs.
  `default.aspx` → file `btnet/pages/default.java` (manifest path, unchanged)
  holding a **package-private** class `default_` (a public class must match
  its file name, and `default` is a keyword).
- Controls: every `runat="server"` element / `asp:*` control / user control
  instance becomes a `public final` field named by its `ID`, created with
  `control(new <Type>("<ID>"))`. Type from the designer file; for inline-script
  pages, from the tag: `span/div/p/td/tr/table/li/…`→`HtmlGenericControl`,
  `a`→`HtmlAnchor`, `input type=text|password|hidden|checkbox|radio|submit|button|file`
  →`HtmlInputText|HtmlInputPassword|HtmlInputHidden|HtmlInputCheckBox|HtmlInputRadioButton|HtmlInputSubmit|HtmlInputButton|HtmlInputFile`,
  `select`→`HtmlSelect`, `textarea`→`HtmlTextArea`, `img`→`HtmlImage`,
  `form`→`HtmlForm`, `head`→`HtmlHead`; `asp:X`→runtime `X` (`DropDownList`,
  `ListBox`, `CheckBox`, `CheckBoxList`, `RadioButton`, `Button`, `Label`,
  `DataGrid`, `Panel`, `HyperLink`, `LinkButton`). Declarative attributes that
  code reads (`Text=`, `GroupName=`, `SelectionMode=`, `<asp:ListItem>`s) are
  set in the field initializer: `control(new CheckBox("x")).Text("Admin")`.
- Every page member referenced by the template is `public`.

**B2. Request lifecycle** (runtime `Page.ProcessRequest`, written once):

1. Bind intrinsics (`Request`, `Response`, `Server`, `Session`,
   `Application`, `Context`, `Cache`, `User`, `Page`=this, `Header`).
2. `IsPostBack` = method is POST **and** the form contains `__VIEWSTATE` or
   `__EVENTTARGET` **and** the request is not a `Server.Transfer` target
   (ASP.NET `DeterminePostBackMode`).
3. `OnPreInit(EventArgs)` → `BasePage` authorization (B6).
4. `Page_Init`; then, on postback, load view state (B4), verify its MAC if
   `ViewStateUserKey` was set, then load posted values into controls.
5. `Page_Load` of the page, then of its master, then of user controls in
   markup order.
6. Postback events: the control named by `__EVENTTARGET`, or the submit
   button whose name is in the form, raises its handler — `OnClick="X"`
   (`admin.aspx:96`) → `reindexLink.OnClick(this::X)` in the constructor;
   `OnItemCommand="my_button_click"` (`backup_db.aspx:14`, `manage_logs.aspx:11`)
   → `MyDataGrid.OnItemCommand(this::my_button_click)`.
7. Save view state, render the template with model `page` (this) and
   `master`, and write: **Response buffer first, then the rendered template**
   (a `Response.Write` in `Page_Load` appears before `<!DOCTYPE>` exactly as in
   WebForms). Default content type `text/html; charset=utf-8`.
8. `ResponseEndException` → send only what was written so far (buffer, plus
   partial template output if thrown during render — `TODO(port)` if Thymeleaf
   buffering hides partial output). `RedirectException` → 302. 
   `TransferException` → forward (B3). Any other exception → `Global.Application_Error`
   then HTTP 500.

**B3. WebForms → Spring MVC mapping (one canonical form each)**

| WebForms | Port |
|---|---|
| `Page_Load` with `if (!IsPostBack) {A} else {B}` | **kept verbatim** in `Page_Load`; the single `handle` mapping takes GET and POST. Do not split into `@GetMapping`/`@PostMapping` |
| `Response.Write(x)` in code (399 sites/37 `.cs`) | `Response.Write(x)` on the runtime buffer (`Object` overload applies `Strings.cs`) |
| `Response.End()` (57/35) | `Response.End()` (throws `ResponseEndException`) |
| `Response.Redirect(url)` (20/16) | `Response.Redirect(url)` → 302, `Location` = url resolved against the request directory to an absolute path (`edit_bug.aspx?id=3` → `/edit_bug.aspx?id=3`), then ends. `Redirect(url, false)` does not end |
| `Server.Transfer("x.aspx")` (19/18) | `Server.Transfer("x.aspx")` → `RequestDispatcher.forward("/x.aspx")`; the target runs with `IsPostBack=false`, sees the same form/query values and appends to the same Response buffer |
| `ViewStateUserKey = Session.SessionID` (27 files) | same statement; runtime MACs `__VIEWSTATE` with it; mismatch → 500 |
| `Page.Header.Title = …` | `Header.Title(…)`; layouts render it in `<title>` (default `btnet bugs`) |
| `Master.Menu.SelectedItem = "admin"` (31) | `Master.Menu().SelectedItem = "admin";` |
| `Util.do_not_cache(Response)` | unchanged call; runtime `HttpResponse.CacheControl/AddHeader/Expires` |
| `Response.ContentType/AddHeader/BinaryWrite/WriteFile/TransmitFile/OutputStream/Clear/StatusCode/Cookies` | same-named runtime methods; the runtime buffers the whole response, so headers set after writes still apply (ASP.NET `Buffer=true`) |
| `ValidateRequest="false"` (11 pages: backup_db, change_password, default, edit_bug, edit_comment, edit_custom_html, edit_dashboard, manage_logs, mbug, search, send_email) | class annotation `@ValidateRequest(false)`; all other pages validate (`Web.config:38,46`, mode 2.0): reading a form/query/cookie value that contains `<` followed by a letter, `!`, `/` or `?`, or `&#`, throws `HttpRequestValidationException` → 500 |
| Static/`<%@ Import %>` directives | nothing (Java imports) |

**B4. View state and control state.** Controls behave like their WebForms
counterparts because the runtime persists them: `__VIEWSTATE` = base64 JSON
of (a) list items of `DropDownList/ListBox/CheckBoxList/HtmlSelect`, (b) any
control property set by code (`InnerText`, `InnerHtml`, `Visible`,
`Disabled`, `Attributes`, `Text`, `Checked`, `Value` of non-input
controls), HMAC-SHA256 signed (key = app secret + `ViewStateUserKey`). On
postback, state is restored, then posted values are applied:
inputs/textarea → `Value`; checkbox → `Checked` = name present; radio →
`Checked` = posted group value equals its value; select → selected item(s) by
value. A posted value not among the restored items of a select-type control →
500 (`enableEventValidation` default). Templates emit `__VIEWSTATE`,
`__EVENTTARGET`, `__EVENTARGUMENT` and the `__doPostBack` script inside every
server form via the runtime fragment `~{runtime/form :: hidden}`.

**B5. Templates (Thymeleaf).** Location `port/web/src/main/resources/templates/`
(§4). Translation of markup:

| Markup | Template |
|---|---|
| Literal HTML | copied unchanged (including `class`, `style`, inline JS) |
| `<form runat="server" …>` | `<form method="post" th:action="${page.FormAction()}" id="aspnetForm" name="aspnetForm" …>` + `<th:block th:replace="~{runtime/form :: hidden}"/>`. `FormAction()` = current path **plus query string** (judge S8:41). Pages without a master: `id`/`name` = the form's declared `id`, else `ctl00` (`Util.get_form_name()`, `Web.config:853`). Declared `action`/`target`/`enctype`/`onsubmit` kept |
| Server control `<span runat="server" id="msg" class="err">` | `<span id="msg" class="err" bt:control="${page.msg}"></span>` — the runtime dialect processor applies `Visible` (false → element removed), `InnerText`/`InnerHtml`, `Value`, `Checked`, `Disabled`, `Attributes`, select options |
| `name` of a form control | **logical name only** = the control `ID` (judge contract 2): `name="short_desc"`. Radio: `asp:RadioButton` → `name` = `GroupName`, `value` = `ID`; `HtmlInputRadioButton` → declared `name`. Checkbox value `on` |
| `id` of any element | exactly the source `ID`/`id` (no `ctl00_body_` prefix, judge N4) |
| `asp:DropDownList ID=x AutoPostBack=true` | `<select id="x" name="x" bt:control="${page.x}" onchange="__doPostBack('x','')">` |
| `asp:CheckBox ID=x Text=T` | `<input type="checkbox" id="x" name="x" bt:control="${page.x}"><label for="x" th:text="${page.x.Text()}">T</label>` |
| `asp:Button ID=b Text=T OnClick=…` | `<input type="submit" id="b" name="b" bt:control="${page.b}" value="T">` |
| `asp:DataGrid` (6 sites/3 files) | `<table id="…" bt:control="${page.MyDataGrid}"></table>`; runtime `DataGrid` renders BoundColumn/HyperLinkColumn/ButtonColumn and posts commands via `__doPostBack` |
| `<%= expr %>` / `<% Response.Write(expr); %>` where expr is a `String` field or no-arg method of the page | `<th:block th:utext="${page.expr}"/>` |
| `<%: expr %>` or `<% Response.Write(HttpUtility.HtmlEncode(expr)); %>`, same condition | `th:text` (decoded text is identical; markup bytes are not judged) |
| Any other code region (`<% … %>` span from an opening `<%` to its brace-balanced end, incl. the literal markup between its blocks) **without** runat=server elements inside | one method `public String __renderN()` (N = 1-based order of the region in the source file) that writes the literal markup and `Response.Write` output into a local runtime `HttpResponse` over a `StringWriter` and returns it; template `<th:block th:utext="${page.__renderN()}"/>`. `<%= e %>` inside → raw write; `<%: e %>` → `HttpUtility.HtmlEncode` |
| A code region whose blocks enclose runat=server elements (`edit_bug.aspx:101-115`) | `th:if="${page.__condN()}"` / `th:each="item : ${page.__itemsN()}"` on the enclosing element; `__condN()` returns the C# condition |
| `<%# Eval("x") %>` (8 sites/2 files) | `th:text`/`th:utext` over the bound row inside `th:each` |
| Page with no markup besides directives/whitespace | no template; `ProcessRequest(req, res, null)` sends only the Response buffer |

Thymeleaf static calls (`T(...)`) are BANNED in templates; expose a page
method instead. Templates never contain SQL or business logic.

**B6. Masters, user controls, includes.**

- `LoggedIn.Master` → class `btnet.pages.LoggedIn extends MasterPage`
  (members from `.Master.cs`/designer: `Menu()` returns the `MainMenu`
  field) + layout `templates/layouts/LoggedIn.html` declaring
  `th:fragment="layout(cph_headerScripts, cph_body, cph_footerScripts)"`.
  A content page template is
  `<html th:replace="~{layouts/LoggedIn :: layout(~{::cph_headerScripts}, ~{::cph_body}, ~{::cph_footerScripts})}">`
  with each `asp:Content ContentPlaceHolderID="X"` → `<th:block th:ref="cph_X">…</th:block>`
  (absent placeholder → pass `~{}`).
- `LoggedOut.Master` → layout `templates/layouts/LoggedOut.html`; its Java
  unit `btnet.pages.LoggedOut` is translated from `LoggedOut.Master.cs` but is
  dead code (`// BUG(port): markup Inherits btnet.LoggedIn`); pages using
  LoggedOut.Master declare `public final LoggedIn Master = master(new LoggedIn())`
  (the class the original instantiates) and the LoggedOut layout.
- `ScriptBundle("~/bundles/all")` + `Scripts.Render` (`BundleConfig.cs`,
  `LoggedIn.Master:37`): `compilation debug="true"` (`Web.config:31`) means
  unbundled — the layout renders one `<script src="Scripts/Site/<file>.js">` per
  file under `Scripts/Site` (recursive, ordinal path order) via runtime
  `Scripts.Render("~/bundles/all")`.
- `.ascx` (`BugList`, `Footer`, `MainMenu`) → class in `btnet.Controls`
  extending runtime `UserControl` (same lifecycle hooks, own controls) +
  fragment `templates/fragments/<Name>.html`,
  `th:fragment="<Name>(uc)"`; host template:
  `<th:block th:replace="~{fragments/MainMenu :: MainMenu(${master.MainMenu})}"/>`.
  Inside a fragment, controls are `${uc.<ID>}`.
- `#include file="inc.aspx"` (53 pages): imports only → `btnet/pages/inc.java`
  = `final class inc { private inc() {} }` with header comment; nothing in
  templates. `#include file="inc_diff.inc"` (3 pages) → fragment
  `templates/fragments/inc_diff.html` reading the including page's fields
  (`left_title`, `left_out`, `right_title`, `right_out`, `unified_diff_text`);
  `btnet/pages/inc_diff_inc.java` = marker class listing those required
  fields in its Javadoc.
- `Global.asax.cs` → `btnet/pages/Global.java extends HttpApplication`
  (runtime). Runtime calls `Application_OnStart(null, EventArgs.Empty)` once at
  `ApplicationReadyEvent` inside a startup pseudo-context whose `Request()`
  throws `HttpException` (as in .NET, caught at `util.cs:54-61`), and
  `Application_Error` on unhandled exceptions (`Server.GetLastError()`).

**B7. Authentication and authorization (OWIN cookie → Spring Security).**

- Spring Security (runtime `SecurityConfig`): CSRF **disabled** (view-state
  MAC is the original's protection); `/api/**` requires authentication
  except `/api/login`; every other path `permitAll` — page authorization stays
  in `BasePage.OnPreInit` exactly as in the source.
- Identity: runtime `ClaimsIdentity`, `Claim`, `ClaimTypes` (same URI
  strings as .NET), `IIdentity`, `IPrincipal`/`ClaimsPrincipal`
  (`IsInRole` over `ClaimTypes.Role` claims); stored in the
  `HttpSession` security context. Anonymous: `IsAuthenticated()` false,
  `Name()` `""`.
- `Request.GetOwinContext()` → runtime `IOwinContext`;
  `owinContext.Authentication.SignIn(identity)` → stores the context without
  changing the session id (original keeps `Session` across login);
  `SignOut()` clears only the security context.
- Page authorization: `BasePage.OnPreInit` (translated): anonymous or
  missing role → `Response.Redirect("default.aspx?returnUrl=" +
  HttpUtility.UrlEncode(Request.RawUrl))` (judge S1). `[PageAllowAnonymous]`
  (`BasePage.cs`, `default.aspx.cs`, `forgot.aspx.cs`) → `@PageAllowAnonymous`
  (`@Inherited`).
- API `[Authorize]` (BugQuery, BugFromEmail): unauthenticated → if header
  `X-Requested-With: XMLHttpRequest` → 401, else 302 to absolute
  `…/default.aspx?ReturnUrl=` + `Uri.EscapeDataString(path + query)` (Katana
  default, judge S5:41).
- Login lifetime: OWIN cookie (14-day sliding) becomes session-bound (120 min).
  `TODO(port): persistent auth cookie` in `SecurityConfig`; not judged.
- Password hash (`util.cs:494-500`): runtime `Rfc2898DeriveBytes(String,
  byte[])` = PBKDF2-HMAC-SHA1, 1000 iterations, password as UTF-8 bytes,
  `ArgumentException` if salt < 8 bytes; `GetBytes(n)` continues the output
  stream. `Encoding.UTF8.GetString` = `new String(bytes, UTF_8)` (U+FFFD per
  malformed subsequence). **Acceptance vector:** `HashString("admin",
  "PmafvZSWFYML3")` must equal the `us_password` literal of `setup.sql:168`.
  `TODO(port)`: if it does not, the .NET 4.5 replacement granularity differs —
  runtime author fixes `Encoding.UTF8.GetString`, not the callers.

**B8. URLs and static assets.**

- Every page answers at `/<file>.aspx` (context root `/`), **case-insensitive**:
  runtime MVC config sets `PathPatternParser.setCaseSensitive(false)`. Same
  query parameter names (judge contract 1). API at `/api/<name>`.
- Static assets are copied **unchanged in content** (by the runtime author,
  once) from `src/BugTracker.Web/` to `port/web/src/main/resources/static/`:
  everything except server-side source (`*.aspx`, `*.ascx`, `*.Master`,
  `*.asax`, `*.cs`, `*.inc`, `*.config`, `*.csproj`, `setup.sql`,
  `Properties/`, `App_Start/`, `Controllers/`, `Controls/`, `Mail/`,
  `Models/`, `Search/`, `Security/`, `btnet/`, `custom/`). Included:
  `Scripts/` (ckeditor, DataTables, jQuery…), `Content/`, `fonts/`,
  `datejs/`, `edit_area/`, root `*.js *.css *.png *.gif *.ico *.html *.htm *.jpeg`.
  Paths are **lower-cased** on copy and the runtime `LowercaseResourceResolver`
  lower-cases lookups (IIS served `scripts/` and `Scripts/` alike —
  `LoggedIn.Master:23-27`).
- `custom/` (`custom_header.html` etc., read by `Global.asax.cs`) is copied to
  `port/web/webroot/custom/` and also served at `/custom/**`. `App_Data/` lives
  under the webroot and is never served.

## 3. The sanctioned escape hatch — hand-written runtime (`btnet.runtime`)

Written **once, by the runtime author (largest model), before fan-out**, in
module `port/runtime`. Translators only call it. A translator who needs a
missing runtime member writes the call as if it existed, marks
`TODO(port): runtime <Class.Member>`, and keeps going; the runtime author
batches those between Step 3 batches. Contents (exhaustive):

| Package | Classes |
|---|---|
| `btnet.runtime` | `Convert`, `Int32`, `Decimal`, `DateTimes`, `Strings` (`cs`, `Format`, `IsNullOrEmpty`, `Split`, `Trim*`, `IndexOf`, `CURRENT_CULTURE`), `StringSplitOptions`, `DotNetFormat`, `CultureInfo`, `NumberStyles`, `DotNetRegex` (+`RegexOptions`, `Match`, `MatchEvaluator`), `Ref<T>`, `Nulls`, `DBNull`, `Environment`, `Encoding`, `Threads`, `Stopwatch`, `Collections2`, `NameValueCollection`, `Processes`, `Exceptions`, `EventArgs`, exception classes (`FormatException`, `OverflowException`, `InvalidCastException`, `ArgumentException`, `ArgumentNullException`, `KeyNotFoundException`, `ConfigurationErrorsException`) |
| `btnet.runtime.data` | `DataSet`, `DataTable`, `DataColumn`, `DataColumnCollection`, `DataRow`, `DataRowCollection`, `DataView`, `DataRowView`, `DataTableSerializer`, `SqlConnection`, `SqlCommand`, `SqlParameter`, `SqlParameterCollection`, `SqlDataAdapter`, `SqlDataReader`, `SqlDbType`, `CommandBehavior`, `SqlException`, `SqlBatch`, `DbContext`, `DbSet<T>`, `EntityTypeConfiguration<T>`, `DbModelBuilder` |
| `btnet.runtime.config` | `ConfigurationManager`, `WebConfigToProperties` (one-time generator), `BtnetApplication` (Spring Boot main, MVC config: case-insensitive paths, resource resolver, dialect, filters), `JsonConfig` |
| `btnet.runtime.io` | `File`, `Directory`, `Path`, `StreamReader` |
| `btnet.runtime.web` | `HttpContext`, `HttpRequest`, `HttpResponse`, `HttpServerUtility`, `HttpSessionState`, `HttpApplicationState`, `HttpRuntime` (`Cache()`), `HttpCookie`, `HttpPostedFile`, `HttpUtility`, `HttpApplication`, `HttpException`, `HttpRequestValidationException`, `Page`, `MasterPage`, `UserControl`, `ValidateRequest` (annotation), `ResponseEndException`, `TransferException`, `RedirectException`, `ViewStateCodec`, `RequestContextFilter`, `LowercaseResourceResolver`, `Scripts`, `BundleCollection`, `ScriptBundle`, `ApiController`, `ApiBody`, `FromUri`, `Required` (annotation), `ModelStateDictionary` |
| `btnet.runtime.web.ui` | `Control`, `HtmlControl`, `HtmlGenericControl`, `HtmlAnchor`, `HtmlForm`, `HtmlHead`, `HtmlImage`, `HtmlInputText`, `HtmlInputPassword`, `HtmlInputHidden`, `HtmlInputCheckBox`, `HtmlInputRadioButton`, `HtmlInputSubmit`, `HtmlInputButton`, `HtmlInputFile`, `HtmlSelect`, `HtmlTextArea`, `DropDownList`, `ListBox`, `CheckBox`, `CheckBoxList`, `RadioButton`, `Button`, `Label`, `Panel`, `HyperLink`, `LinkButton`, `DataGrid` (+ columns, `DataGridItem`, `DataGridCommandEventArgs`), `ListItem`, `ListItemCollection`, `AttributeCollection`, `CssStyleCollection`, `BtnetDialect` (`bt:control`); templates `templates/runtime/form.html` |
| `btnet.runtime.security` | `SecurityConfig`, `ClaimsIdentity`, `ClaimsPrincipal`, `Claim`, `ClaimTypes`, `IIdentity`, `IPrincipal`, `IOwinContext`, `AuthenticationManager`, `Rfc2898DeriveBytes` |
| `btnet.runtime.mail` (+`.pop`) | `SmtpClient`, `MailMessage`, `MailAddress`, `Attachment`, `MailPriority`-compatible enum; `pop.Pop3Client`, `pop.Message`, `pop.MessagePart` |
| `btnet.runtime.search` | `ElasticClient` (thin HTTP wrapper) |

Every escape-hatch use in translated code is visible: a runtime call, or one
of the markers below. Marker formats (load-bearing, one each):

```
// TODO(port): <open question>          <!-- TODO(port): ... --> in templates
// BUG(port): <defect> — repro: <input>
// PERF(port): <slow thing> — fast version: <one line>
// FIXED(port): F<n> — see RULEBOOK §0.1   (only the five §0.1 sites)
```

## 4. Naming and output paths

Done-ness = the manifest target file exists. The naming rule is
`migration/scripts/manifest_subs.py` (ratified here verbatim; its output is
`migration/depmap/manifest_subs.txt` → `migration/manifest.tsv`, 217 rows):

```
module   src/BugTracker.Web -> port/web,  src/btnet_service -> port/service
.cs      <module>/src/main/java/<namespace as path>/<PrimaryType>.java
         primary type = the top-level type whose name equals the file stem
         (case- and underscore-insensitive), else the first declared type;
         global-namespace types go to package `btnet`
markup   .aspx/.Master/.asax/.inc -> package btnet.pages, class = file stem
         (not Inherits: LoggedOut.Master inherits btnet.LoggedIn); .ascx -> its own
         namespace (btnet.Controls); .inc -> btnet/pages/<name with . -> _>.java
partial  a partial class split across units -> one shared target, one batch (Startup)
Refuses (exit 1) if two units map to the same path case-insensitively.
```

- One manifest row → one Java file; never write a second Java file for a
  unit. Helper types a unit needs are **nested** classes in that file.
- Templates (not in the manifest; written by the same unit's implementer):
  page → `port/web/src/main/resources/templates/<page file stem>.html`
  (case preserved: `UpdatePassword.html`); master → `templates/layouts/<Name>.html`;
  `.ascx` → `templates/fragments/<Name>.html`; `inc_diff.inc` →
  `templates/fragments/inc_diff.html`. Runtime-owned: `templates/runtime/*`.
- Static assets: `port/web/src/main/resources/static/<lower-cased source path>`
  (§2B B8); copied by the runtime author, never by translators.
- Java package = C# namespace verbatim (`btnet.Models.Mapping`); page classes
  in `btnet.pages`. Imports: explicit single-type imports; wildcard only for
  `btnet.runtime.web.ui.*`. No static imports except `RequestMethod.*` in page
  `@RequestMapping`.
- Members keep C# names and casing; Java-keyword collisions get a trailing
  `_`; nothing else is renamed.
- **Home of shared types:** .NET-framework emulation → `btnet.runtime.*`
  (§3). Application types → their translated unit (`btnet.SQLString`,
  `btnet.DbUtil`, `btnet.Util`, `btnet.Security.*`). No agent declares a local
  copy of either.
- Tests: none translated (the judge in `judge/` is the test suite).

## 5. The gap inventory

Per-site decisions (which `ref` parameters, which nullable columns, which
pages have postback events, which code regions become `__renderN`/`__condN`)
are looked up in `migration/inventory.tsv`, not made by implementers. Before
translating a unit, grep the inventory for its source path. A site missing
from the inventory is an inventory bug: flag it in the report, apply the
UNKNOWN rule, keep moving. Rule rows here win over inventory rows; a conflict
is a finding for the human.

## 6. Per-file obligations

1. **Header**, first lines of every Java file:
   `// Source: src/BugTracker.Web/<path>` (all source files merged into it,
   e.g. `edit_bug.aspx + edit_bug.aspx.cs + edit_bug.aspx.designer.cs`).
   Templates: `<!-- Source: src/BugTracker.Web/<path>.aspx -->` after the doctype.
2. **Completeness:** every type, field, property, method, constructor and
   constant of the source unit is translated, including private and unused
   ones (dead code stays). SQL text byte-identical except §0.1. Every literal
   user-visible message identical.
3. **Markers** only in the §3 formats; no other TODO/FIXME styles.
4. **Scope:** write only the manifest target and its template(s). Never edit
   another unit, the runtime, poms, static assets, the inventory or this file.
   Needed changes elsewhere go into the batch report.
5. **Inventory grep first** (§5); cite inventory row ids in a comment where a
   decision came from it.
6. **Judge handles:** for pages, every `id`, logical field `name`, URL and
   query parameter in the source survives (judge contract 1-3); reviewers
   check the `judge/specs/S*.md` "Handles used" lists.
7. **Status trailer** as the last line:
   ```
   // PORT STATUS: confidence=[high|medium|low] todos=[N]
   ```
   (templates: `<!-- PORT STATUS: confidence=… todos=N -->`). `N` counts the
   file's `TODO(port)` markers; an undercount is a finding.

## 7. Deviation log

| ID | Date | Deviation | Sanctioned by |
|---|---|---|---|
| D-001 | 2026-11-03 | Original app cannot run (no Windows/IIS; SQL Server package host blocked). Standing rule 7's "run the old code" is unmet: the judge's expectations are read from source with `@SourceRef`; judge validation is deferred to the port (Step 5, mutations in `judge/specs/MUTATIONS.md`); parity with the original is recorded as **unverified** at Step 6 | Human, gate 00 (`feasibility.md` §9) |
| D-002 | 2026-11-03 | The orchestrator records gates as signed off, commits at gates, and starts the build daemon itself instead of stopping for the human (standing rule 3), because the human delegated all gates to the stated recommendations | Human, standing mandate (`PROGRESS.md`) |
| D-003 | 2026-11-03 | Option (b): five original defects are specified as fixed (§0.1, F1–F5) instead of reproduced; the judge tests pinning them (`S1` admin-rewrite/non-admin-500 tests, `S3.browserPostWithoutUserHitSubmitSavesNothing` and the history-failure test, `S8` queue-failure tests) are rewritten to the fixed expectation, and mutations M-S1-2, S3-M1, S8-M1 are retired or inverted, in the same change | Human, gate 00b (`PROGRESS.md`) |
| D-004 | 2026-11-03 | UI layer is a redesign: the Step 2 dual-translation bakeoff is not used for UI units (pages, masters, `.ascx`, includes); replaced by adversarial review of §2B and a throwaway pilot | Human, gate 00 (`feasibility.md` §9) |
