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
- **Gate log.** 2026-11-03, gate 02: amendments C1–C35 from
  `migration/inventory/rulebook-conflicts.md` applied under the standing
  mandate (D-002; applied by the orchestrator, D-005). Each amended rule cites
  its amendment id `[Cn]`; C13 = C7, C23 = C21, C31 refines C20, and C17 is
  applied in its CORRECTED form (one-shot timer re-armed in `do_work`).
  2026-11-03, gate 02: joint audit fixes applied 2026-11-03 — the
  rulebook-side items of `migration/inventory/joint-audit.core.md` (J-C1…J-C26
  and decisions N1–N8) and `migration/inventory/joint-audit.ui.md`
  (J-U1…J-U18), each verified against the cited source and tagged
  `[J-Cn]`/`[J-Un]`/`[Nn]` at the rule it changes. Inventory-side items
  (J-C9 rows, J-C21–J-C24, J-C26, J-U15–J-U17) change rows only; N6 and N8
  needed no rule change. Under the standing mandate (D-002, D-005).

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

Not fixed (preserved, examples): `Security.cs:73` adding a **second**
`CanOnlySeeOwnReportedBugs` claim from `us_enable_bug_list_popups` — it is
shadowed by the first one (`:49`, from `og_can_only_see_own_reported`)
because `ClaimsIdentityExtensions.cs:178` reads with `FindFirst`, so line 73
has no effect and the org setting wins; preserved by the runtime's ordered
claim list (§3 `ClaimsIdentity`) [J-C3]; lockout
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
| HTTP client | `java.net.http.HttpClient` | `POP3Main.cs:689-724` `System.Net.Http.HttpClient` [J-C2]; ES |
| Async/concurrency | BANNED: `@Async`, executors, `CompletableFuture`, virtual threads, Spring `@Scheduled`. `new Thread(proc).Start()` → `new Thread(proc)` + `setDaemon(false)` + `start()` (platform, **non-daemon**: .NET threads are foreground, Tomcat-spawned Java threads inherit daemon=true [C22]); written via `Threads.start(proc)` (runtime, does exactly that). `Task.Run(() => x())` → same rule: `Threads.start(() -> x())` [C2]. `async void` → `Threads.runAsync` (§2A "Threads, timers, async") [C33]. The **one** sanctioned scheduler is `java.util.Timer` (non-daemon) in `port/service` `POP3Main` (§2A) [C17] | 4 thread sites (`bug.cs:1201`, `tags.cs:119,125`, `email.cs:273`, `admin.aspx.cs:43` [C2]) + `POP3Main.cs:306,511` |
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
| `partial class` (148, pages+designer, `Startup`) | one Java class; `Startup.cs` + `App_Start/Startup.Auth.cs` → one `btnet/Startup.java`, one batch. Both methods are kept over the runtime OWIN types (§3 security) [J-C14]: `public void Configuration(IAppBuilder app) { ConfigureAuth(app); }`, `public void ConfigureAuth(IAppBuilder app)` with the options built per C6 (`var o = new CookieAuthenticationOptions(); o.AuthenticationType = "ApplicationCookie"; o.LoginPath = new PathString("/default.aspx"); app.UseCookieAuthentication(o);`). `[assembly: OwinStartup(typeof(btnet.Startup))]` → the comment `// [assembly: OwinStartup] → invoked by SecurityConfig` | depmap cycle 2 |
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
| Attribute class + usage (`[PageAuthorize]`, `[PageAllowAnonymous]`) | Java annotation with `@Retention(RUNTIME) @Target(TYPE)`; logic of the C# attribute moves to a nested `public static final class Logic` in the same file. `PageAuthorizeAttribute`: `String[] value() default {}`, `@Repeatable` container `PageAuthorizeAttributes`. Usage `[PageAuthorize(BtnetRoles.Admin)]` → `@PageAuthorizeAttribute(BtnetRoles.Admin)`. **`Logic` signature [C28, J-C16]:** both C# constructors are kept — `public Logic()` (empty `_roles`) and `public Logic(String... roles)` — and `public boolean OnAuthorize(IPrincipal principal)` (runtime `IPrincipal`: `Identity()`, `IsInRole(String)`; the parameter type is the source's, no cast at the call: `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))`); `BasePage` builds one `Logic` per annotation (`new PageAuthorizeAttribute.Logic(a.value())`) | `BasePage.cs:7-30`, `PageAuthorizeAttribute.cs:15-27` |
| Object initializer `new T { A = x, B = y }` [C6] | construct, then assign each member **in the source's textual order**, as a block lambda/local: `p -> { var i = new SelectListItem(); i.Value = p.Name; i.Text = p.Name; return i; }`. ASP.NET MVC `SelectListItem` → runtime `btnet.runtime.web.SelectListItem` (§3) | `Controls/BugList.ascx.cs:67-132` |
| Declared type `IEnumerable<T>` / `ICollection<T>` / `IList<T>` [C1] | `java.util.List<T>`; a LINQ result assigned to it is materialized with `.collect(Collectors.toCollection(ArrayList::new))` (EF6: `.ToList()`). A Java array is used **only** where the C# declared type is `T[]` | `priorities.aspx.cs:28` `IEnumerable<Priority> Priorities = ….ToArray()` → `Priorities = context.Priorities.OrderBy("Name").ToList();` |
| `Attribute.GetCustomAttributes(GetType(), typeof(A))` (inherit=true) | walk `getClass()` and every superclass, collecting `getDeclaredAnnotationsByType(A.class)` (Java `@Inherited` drops the superclass annotation when the subclass has one; C# returns both — admin pages need User **and** Admin) | `BasePage.cs:23,29-30` |
| `ref`/`out` parameter (43 sites/13 files) | runtime `btnet.runtime.Ref<T>` (public field `value`); caller: `Ref<String> sv = new Ref<>(sort_val); f(sv); sort_val = sv.value;` — write back immediately after the call | `bug_list.cs:187-190`, `Mime.cs:27`, `version_control.cs:175,393` |
| `T?` / `Nullable<T>` (7) | boxed type (`Integer`, `LocalDateTime`); `.HasValue` → `!= null`; `.Value` → unboxing | `Models/BugFromEmail.cs:11-30` |
| `a ?? b` (12, `BugFromEmailController.cs:115-130`) [J-C7] | right-hand side a literal, a local or a field → `btnet.runtime.Nulls.coalesce(a, b)`; any other right-hand side (a call, cast, indexer, unboxing) → `Nulls.coalesce(a, () -> b)` (C# evaluates `b` only when `a` is null) | `:115` `bugFromEmail.OrganizationId ?? User.Identity.GetOrganizationId()` → `Nulls.coalesce(bugFromEmail.OrganizationId, () -> ClaimsIdentityExtensions.GetOrganizationId(User.Identity()))`; `:116` `?? (int)defaults["ct"]` → `() -> (int) defaults.get("ct")`; `?? 0`, `?? string.Empty` → two-argument form |
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
| `foreach (T x in c)` with a typed variable over a non-generic collection (`ArrayList`, `Hashtable.Keys`) [J-C18] | `for (Object o_ : c) { T x = (T) o_; … }` (the C# implicit cast; a wrong element type fails with ClassCastException ≈ InvalidCastException) | `email.cs:289` `foreach (string directory in stuff_to_delete.directories_to_delete)` → `for (Object o_ : stuff_to_delete.directories_to_delete) { String directory = (String) o_; … }` |
| `NameValueCollection` / `StringDictionary` [J-C4] | runtime `NameValueCollection` (case-insensitive keys, multi-value, comma-joined `get`) / runtime `btnet.runtime.StringDictionary` (§3: keys lower-cased with `Locale.ROOT` **inside** `get`/`set`; `get` of an absent key → `null`). Translators write the source's key verbatim: `settings["MessageInputFile"] = ""` → `settings.set("MessageInputFile", "")`; never lower-case keys at the call site and never use a bare `HashMap` | `POP3Main.cs:259-261,394,405-406` |
| .NET `[Flags]` enums `CommandBehavior`, `RegexOptions`, `NumberStyles`, `StringSplitOptions` [J-C5] | runtime `public final class X { public static final int A = …; }` with the .NET numeric values (§3); every parameter of such a type is `int`, so `a \| b` translates unchanged. Plain (non-flags) .NET enums (`SqlDbType`, `MailPriority`) stay Java `enum`s | `dbutil.cs:101,120` `behavior \| CommandBehavior.CloseConnection`; `print_bug.cs:22-34`; `util.cs:162` `NumberStyles.Float` |
| `SortedDictionary<K,V>` [C20, C31] | `TreeMap<K,V>`; **string keys** → `new TreeMap<>(Strings.CULTURE_COMPARER)` (.NET `Comparer<string>.Default` is culture-sensitive, not ordinal); other keys → natural order | `tags.cs:108` `new SortedDictionary<string, List<int>>()` → `new TreeMap<String, List<Integer>>(Strings.CULTURE_COMPARER)` |
| LINQ-to-objects (33/15) | `java.util.stream`: `Where`→`filter`, `Select`→`map`, `Any`→`anyMatch`, `All(p)`→`allMatch(p)` (empty → true, as .NET) [C4], `ToList`→`collect(toCollection(ArrayList::new))`, `First()`→`findFirst().orElseThrow()` (throws like C#), `FirstOrDefault()`→`findFirst().orElse(null)`, `OrderBy(k)`→`sorted(comparing(k))` stable; **string keys** use `btnet.runtime.Strings.CULTURE_COMPARER` (en-US collation; the single culture comparer — the earlier name `CURRENT_CULTURE` is withdrawn), never natural order | `query.cs:24` `Except` → filter preserving order, distinct; `BasePage.cs:30` `attributes.All(a => a.OnAuthorize(Page.User))` → `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))` |

**Strings**

| Source construct | Canonical Java translation | Notes |
|---|---|---|
| `a == b`, `a != b` on strings (340/48) | `Objects.equals(a, b)` / `!Objects.equals(a, b)` (ordinal) | `Util.get_setting("LogSqlEnabled","1") == "1"` |
| String concatenation `x + y` | every operand that is not a string literal, `char`, or an `int`/`long` expression is wrapped in `Strings.cs(o)` = C# concat semantics (null/DBNull → `""`, else `Convert.ToString(o)`) | Java prints `null`/`true`/`2020-01-02T03:04:05`; C# prints ``/`True`/culture date |
| `Convert.ToString(o)` (356/50); `o.ToString()` on non-String `o` whose receiver **cannot be null** (DataRow/DataRowView values — DBNull, never null —, literals, boxed primitives, `int`/`long` expressions) | `Convert.ToString(o)` (runtime): null→`""`... see Convert table | |
| `o.ToString()` on any other non-String receiver (a `JObject`/`Map` lookup, a field, a method result) [J-C17] | `Strings.ToStringStrict(o)` — throws `NullPointerException` (≈ NullReferenceException) on `null`, else `Convert.ToString(o)`. Never turns the NRE into `""` (Error-recovery rule) | `BugSearch.cs:78,86,87` `bug["desc"].ToString()` → `Strings.ToStringStrict(bug.get("desc"))` (missing property → NRE → search 500, as .NET). `TODO(port)`: a JSON `null` value is `JValue(null)` in .NET (`ToString()` → `""`) but Java `null` here — no indexed field is ever null (`BugSearch.cs:126-131` uses `isnull`), so not reproduced |
| `string.Format` (24/10), `StringBuilder.AppendFormat` | `Strings.Format(fmt, args...)` — .NET composite formatting (`{0}`, `{0:00}`, `{0:X2}`, `{{`/`}}`). `java.lang.String.format` BANNED | `BugQueryExecutor.cs:43`, `edit_task.aspx.cs:210` |
| `string.IsNullOrEmpty` (40/18) | `Strings.IsNullOrEmpty(s)` | |
| `s.Length`, `s[i]` | `s.length()`, `s.charAt(i)` | |
| `s.Substring(a)` / `s.Substring(a, n)` (32/12) | `s.substring(a)` / `s.substring(a, a + n)` | length → end index |
| `s.Split(c)` / `s.Split(chars, RemoveEmptyEntries)` (16/9) | `Strings.Split(s, c...)` / `Strings.Split(s, StringSplitOptions.RemoveEmptyEntries, c...)` (keeps trailing empties; never regex) | `String.split` BANNED |
| `s.Replace(a, b)` (139/21) | `s.replace(a, b)` (literal) | |
| `s.Trim()`/`TrimStart`/`TrimEnd` | `Strings.Trim(s)` etc. (.NET `Char.IsWhiteSpace` set, includes U+00A0) | `String.trim()` BANNED |
| `ToLower()`/`ToUpper()` | `toLowerCase(Locale.US)` / `toUpperCase(Locale.US)` | |
| `IndexOf(string)`/`StartsWith`/`EndsWith`/`Contains` | Java ordinal equivalents; `IndexOf(s, StringComparison.X)` → `Strings.IndexOf` | .NET culture-sensitive variants differ only on ignorable chars — accepted |
| `s.IndexOf(v, start)` (string or char `v`) [C27] | `Strings.IndexOf(s, v, start)` — throws `ArgumentOutOfRangeException` when `start < 0` or `start > s.length()` (.NET); otherwise = `s.indexOf(v, start)`. Bare `indexOf(v, start)` is BANNED (Java clamps) | `version_control.cs:470` `line.IndexOf(" ", pos1)` → `Strings.IndexOf(line, " ", pos1)` (pos1 = -1 must throw) |
| `string.Join(sep, xs)` [C3] | `Strings.join(sep, xs)` — accepts `String[]`, `Object[]`, `Iterable<?>`; each element → `Convert.ToString` semantics with **`null` → `""`** (.NET), never `"null"`. One form for every element type: `java.lang.String.join` is not used (it prints `"null"`), and non-string elements need no `map(String::valueOf)` | `Controls/BugList.ascx:128`, `Mime.cs:90,96`: `string.Join(", ", cols.Select(c => string.Format("'{0}'", c)).ToArray())` → `Strings.join(", ", cols.stream().map(c -> Strings.Format("'{0}'", c)).toList())` |
| `@"verbatim"` (238/57) [C24] | **always** an ordinary string literal: newlines → `\n` (or `\r\n` where the source file has CRLF line ends inside the literal — copy the bytes), tabs → `\t`, `\` → `\\`, `""` → `\"`; long literals may be split by `+` at line ends. **Java text blocks `"""` are BANNED** (they strip common indentation and trailing spaces, so SQL is not byte-identical). **SQL text must be byte-identical** (except §0.1) | `edit_user.aspx.cs:35` `@"select pu_project` + tab-indented lines → `"select pu_project\n\t\t\t\tfrom ..."`; `bug.cs:1178-1183` |
| `Environment.NewLine` | `Environment.NewLine` (runtime constant `"\r\n"`) | |
| `Encoding.UTF8.GetBytes/GetString` | `Encoding.UTF8.GetBytes/GetString` (runtime; lossy decode like .NET, §3) | `util.cs:496-498` |

**Conversions and parsing (runtime `btnet.runtime.Convert`, same method names)**

| C# | Java | Semantics the runtime implements |
|---|---|---|
| `Convert.ToInt32(object)` (134/41) | `Convert.ToInt32(o)` | null → 0; DBNull → `InvalidCastException`; String → `Int32.Parse`; Integer/Short/Byte/Long → narrowing with `OverflowException`; Double/BigDecimal → round-half-even then range check; Boolean → 1/0 |
| `int.Parse(s)` / `Int32.Parse` (2) | `Int32.Parse(s)` | null → `ArgumentNullException`; allows leading/trailing whitespace and one leading sign; else `FormatException`; overflow → `OverflowException` |
| `Convert.ToBoolean(o)` (81/13) | `Convert.ToBoolean(o)` | null → false; String: trimmed, case-insensitive `"True"`/`"False"` else `FormatException`; numbers: ≠0 |
| `Convert.ToString(o)` | `Convert.ToString(o)` | null/DBNull → `""`; Boolean → `"True"`/`"False"`; LocalDateTime → en-US `"G"` (`M/d/yyyy h:mm:ss tt`); BigDecimal → `toPlainString()`; Double → .NET `"R"`-compatible shortest round-trip (`TODO(port)` verify vs .NET 4.5 "G15" if a double ever reaches output) |
| `Convert.ToString(Exception e)` [J-C19] | `Convert.ToString(e)` | a `Throwable` → `Exceptions.dotNetToString(e)` (.NET calls `e.ToString()`); covers `write_line(object o)` (`POP3Main.cs:164-168`) called with an exception (`:430,469,736`) |
| `Convert.ToInt64(object)` [J-C10] | `Convert.ToInt64(o)` (never `ToLong`; .NET names) | `ToInt32` semantics over the `long` range |
| `Convert.ToDateTime`, `Convert.ToDecimal`, `decimal.Parse`, `decimal.TryParse(..., out x)` | same-named runtime methods (`Decimal.TryParse(s, styles, culture, Ref<BigDecimal>)`) | culture en-US. `Convert.ToDecimal(Double d)` [J-C11] = .NET `new Decimal(double)`: `new BigDecimal(Double.toString(d))` rounded to 15 significant digits, `HALF_EVEN`, trailing zeros stripped (never `new BigDecimal(d)`) |
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
  `.Year/.Hour` → `getYear()/getHour()`; `.Month` → `getMonthValue()`,
  `.Day` → `getDayOfMonth()` [C26] (never `getMonth()`, an enum).
- `dt.Ticks` [C20] → `DateTimes.ticks(dt)` (runtime: `long` count of 100 ns
  intervals since 0001-01-01T00:00, proleptic Gregorian, = .NET `Ticks`):
  `DateTime.Now.Ticks` → `DateTimes.ticks(LocalDateTime.now())`.
- `ts.TotalSeconds` (`TimeSpan`) [C20] → `d.toNanos() / 1e9` (a `double`, as
  .NET); `(dt2 - dt1)` → `Duration.between(dt1, dt2)`.
- `new Random()` / `r.Next(a, b)` [C20] → `new java.util.Random()` /
  `a + r.nextInt(b - a)`. The sequence differs from .NET and is **never
  compared** (judge must not pin random output).
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
- `RegexOptions` [C21, C23]: `Compiled`, `CultureInvariant` → no flag (dropped
  silently; the options argument keeps the remaining flags).
  `IgnorePatternWhitespace` → `DotNetRegex` adds `Pattern.COMMENTS`, after first
  rewriting the pattern so that, **inside character classes**, `#` and
  whitespace are escaped (.NET keeps them literal there; Java COMMENTS does
  not). Translators pass the option through unchanged:
  `new Regex(p, RegexOptions.IgnorePatternWhitespace | RegexOptions.Compiled)`
  → `new DotNetRegex(p, RegexOptions.IgnorePatternWhitespace)` (`print_bug.cs:28`).
- `regex.Matches(s)` [C26] → `List<Match>` (iterate with `for (Match m : …)`);
  `m.ToString()` → `m.Value()` (runtime property convention, §2A).
- **Sanctioned pattern rewrite (the only one)** [C25]: `<(.|\n)*?>`
  (`util.cs:216`) → `<[^>]*>` — equivalent (lazy any-char to the first `>`),
  and avoids `StackOverflowError` from java.util.regex's recursive
  alternation on long input. Comment the site `// sanctioned regex rewrite — RULEBOOK §2A C25` (plain comment, not a marker).
  Any other pattern rewrite needs an inventory row.

**Exceptions**

- Translated methods declare **no checked exceptions**. Checked exceptions
  from JDK APIs are caught at the call and rethrown via
  `throw Exceptions.wrap(e)`; `SQLException` becomes runtime `SqlException`
  (unchecked, message = server message text, like .NET `SqlException.Message`).
- .NET exception types used in `catch`/`throw` map to runtime classes of the
  same name in `btnet.runtime` (`FormatException`, `OverflowException`,
  `InvalidCastException extends ClassCastException`, `ArgumentException`,
  `ArgumentNullException`, `ArgumentOutOfRangeException` [C27],
  `IndexOutOfRangeException` [C14], `KeyNotFoundException`, `HttpException`,
  `ConfigurationErrorsException`, `SecurityException` [C26], `NullReferenceException` → use
  `NullPointerException`); all extend `RuntimeException` (via
  `DotNetException` where they have no JDK parent).
- **`System.Exception` [C11] (replaces "`catch (Exception e)` stays").**
  As a thrown/constructed type → runtime `btnet.runtime.DotNetException
  extends RuntimeException` (constructors `(String)`, `(String, Throwable)`);
  as a declared variable/parameter/catch type → `RuntimeException`. Never
  `java.lang.Exception` (checked). Every checked JDK exception is already
  wrapped unchecked at its call (`Exceptions.wrap`), so `catch
  (RuntimeException e)` catches everything a C# `catch (Exception e)` does
  except the control-flow `Error`s below.
  `bug.cs:391` `throw new Exception("x")` → `throw new DotNetException("x");`;
  `catch (Exception ex) { … throw ex; }` (`bug.cs:420`) → `catch (RuntimeException ex) { … throw ex; }`.
- `throw;` → `throw e;` (same variable). `e.Message` → `e.getMessage()`;
  `e.StackTrace` → `Exceptions.StackTrace(e)`; `e.InnerException` →
  `e.getCause()`; `e.GetBaseException()` → `Exceptions.baseOf(e)` (innermost
  cause, `e` itself if none) [C14]; `e.ToString()` / `"" + e` →
  `Exceptions.dotNetToString(e)` (`"<.NET type name>: <message>"` + stack
  text; the type name maps runtime/JDK classes to the .NET FQN where known —
  `DotNetException` → `System.Exception`, `SqlException` →
  `System.Data.SqlClient.SqlException`, `NullPointerException` →
  `System.NullReferenceException` — else the Java FQN; stack-trace text is
  never compared) [C14].
- Bare `catch { … }` [C30, J-C8] → `catch (Throwable t) { Exceptions.rethrowIfEnd(t); … }`
  (`rethrowIfEnd` rethrows any `ResponseEndException`, incl. its Redirect/
  Transfer subclasses, so a bare catch never swallows page control flow). A
  bare catch whose body is or **ends with** `throw;` → that `throw;` becomes
  `throw t;` (precise rethrow compiles: translated try bodies throw no checked
  exceptions) and `rethrowIfEnd` is **omitted** — the body rethrows `t`
  anyway, and running the cleanup before the rethrow is what .NET does on an
  abort (`dbutil.cs:104-108,122-126` `conn.Close(); throw;`, `bug.cs:400-410`,
  `:522`). Every other bare catch — including one ending in
  `throw new Exception(…)` (`util.cs:566` `sanitize_integer`) — gets
  `Exceptions.rethrowIfEnd(t);` as its first statement. If the try
  lexically contains `Response.End/Redirect`/`Server.Transfer`, the C7 clause
  below precedes it.
- `catch (ThreadAbortException …) { … }` [C12] (`email.cs:303`,
  `bug.cs:1285`): no Java equivalent — the catch clause is dropped and replaced
  by the comment `// ThreadAbortException catch removed (RULEBOOK §2A)`; the
  guarded `try` body (and any `finally`) is kept unchanged. A `try` left with
  no catch/finally becomes a plain block.
- `Response.End()`, `Response.Redirect(url)` and `Server.Transfer(p)` throw
  runtime `ResponseEndException extends java.lang.Error` (ThreadAbortException
  analogue) or its subclasses `RedirectException` (Redirect) and
  `TransferException` (Transfer), so `catch (RuntimeException)` does not
  swallow them and one `catch (ResponseEndException ex)` covers all three.
- **Catch bodies that .NET runs on ThreadAbort [C7, C13] (replaces the old
  "`TODO(port): catch body skipped`" text).** .NET runs a `catch (Exception
  ex)` body with `ex.Message == "Thread was being aborted."` and re-throws
  the abort at its end. So every `catch (Exception ex)` (or bare catch) whose
  `try` **lexically** contains `Response.End()`, `Response.Redirect(url)` /
  `Redirect(url, true)` or `Server.Transfer` gets, immediately before the
  general clause, a generated clause running the same body with `ex` bound to
  the abort, then re-throwing it:
  ```java
  } catch (ResponseEndException ex) {   // .NET runs this body on ThreadAbort (RULEBOOK §2A C7)
      try { Response.Write("<font color=red><b>" + Strings.cs(ex.getMessage()) + "</b></font>"); }
      finally { throw ex; }
  } catch (RuntimeException ex) { /* original body */ }
  ```
  (`install.aspx:32` End inside the try, catch at `:46`.) The runtime gives
  `ResponseEndException`/`TransferException` the message
  `"Thread was being aborted."`. The `finally { throw ex; }` is required: in
  .NET the abort is re-raised at the end of the catch even if the body
  returns or throws. End reached only through a callee is not handled
  (`TODO(port)` only if the inventory flags the site).

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
- **Duplicate result-set column names** [J-C12]: `SqlDataAdapter.Fill` (and
  `SqlDataReader` schema mapping) give a later column whose name equals
  (case-insensitively) an earlier one the suffix `1`, `2`, … (first free name,
  .NET `DataColumnCollection` naming); a lookup by the bare name finds the
  **first**. `Security.cs:22-25` selects `u.us_forced_project` and
  `isnull(u.us_forced_project, 0) us_forced_project` (+ `org.*`):
  `dr.get("us_forced_project")` is the nullable first one (`:87` checks DBNull
  for that reason), the second is `us_forced_project1`.
- **Writing a value into a typed column** [J-C11]: `dr.set(col, v)` with
  `v == null` → stores `DBNull.Value`; a value whose class differs from
  `DataType()` is converted as .NET `DataColumn` does
  (`Convert.ChangeType(v, DataType, en-US)`); String → `LocalDateTime` goes
  through `DateTimes.Parse`, whose pattern list includes ISO 8601
  `yyyy-MM-ddTHH:mm:ss[.fffffff]`; failure → `ArgumentException("<v> … Couldn't
  store <<v>> in <col> Column.  Expected type is DateTime.")` (type name per
  column). Sites: `BugSearch.cs:72-88` (`:86` stores
  `bug["bg_reported_date"].ToString()` into the `DateTime` column, `:88`
  `Convert.ToDecimal(hit.Score)` into the `decimal` column, `:83` may store
  `null`). `TODO(port)`: in .NET `:86` round-trips through `JValue.ToString()`
  (en-US `G`, whole seconds), so the stored date has no fractional seconds;
  the port parses the ISO string and keeps them — runtime author confirms
  `search.aspx` never renders sub-second digits.
  `DataView.Find` without a `Sort` → `ArgumentException` (inherited quirk at
  `edit_user.aspx.cs:233`). `dv.Sort(s)` naming an unknown column →
  `IndexOutOfRangeException("Cannot find column <x>.")` (.NET
  `DataTable.ParseSortString`); a malformed sort direction →
  `FormatException` [C14]. `RowFilter` supports only the expression forms
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
  `.ToArray()` [C1] → `.ToList()` when the C# target's declared type is
  `IEnumerable<T>`/`ICollection<T>`/`IList<T>` (`priorities.aspx.cs:28`), and
  `.ToList().toArray(new T[0])` **only** when it is `T[]`; `Where(lambda)` → `.Where(String
  sqlPredicate, SqlParameter...)` written over mapped column names with EF6
  null semantics (`q.User == null || q.User == userId` →
  `"(qu_user is null or qu_user = @userId)"`) — `Context.cs:46-49` is the
  only site. Chains compose on the runtime query object in source order
  [C26]: `db.Queries.Where(q => …).OrderBy(q => q.Name).ToList()` →
  `db.Queries.Where("(qu_user is null or qu_user = @userId)", p).OrderBy("Name").ToList()`
  (one SELECT: WHERE then ORDER BY).

**Web intrinsics in core code**

| C# | Java (runtime `btnet.runtime.web`) |
|---|---|
| `HttpContext.Current` (38/13) | `HttpContext.Current()` — ThreadLocal set by the request filter; `null` on background threads (as in .NET); bound to the startup pseudo-context for the duration of `Application_OnStart` (B6), so `Util.set_context(HttpContext.Current)` (`Global.asax.cs:46`) gets a non-null context whose `Request()` throws `HttpException` (caught at `util.cs:54-61`) [J-U13] |
| `new UrlHelper(Request.RequestContext)` + `urlHelper.IsLocalUrl(url)` (`util.cs:1103-1105`) [N2] | `new UrlHelper(Request.RequestContext())` (runtime `btnet.runtime.web.UrlHelper`, opaque runtime `RequestContext`) + `urlHelper.IsLocalUrl(url)` — exact MVC 5.2.3 semantics (§3). A relative `url` (`edit_bug.aspx`) is **not** local and goes to `bugs.aspx`/`mbugs.aspx`; preserved, marked `// BUG(port): relative returnUrl rejected by IsLocalUrl — repro: default.aspx?url=edit_bug.aspx` |
| Request state read inside a **static core method** [C34] (e.g. 11 rows in `btnet/bug_list.cs`) | through the method's own `HttpContext`/`Page`/`HttpRequest` parameter if it has one (`page.Session.get("k")`), else `HttpContext.Current().Session().get("k")`. A bare `Session`/`Request`/`Response` identifier exists only in page/control classes (inherited fields) — never in core statics |
| `Util.context` (static `HttpContext`, set at `Global.asax.cs:46`) [C32] | field kept (`public static HttpContext context`, assigned the startup pseudo-context). `Util.context.Application[k]` (`MainMenu.ascx:4,15`, `util.cs:918,964`) → `ApplicationState.get().get(k)` — the `HttpApplicationState` singleton, available from startup, independent of any request; `Util.context.Server.MapPath(p)` → `AppPaths.MapPath(p)` (same result as `Server.MapPath`) |
| `HttpResponse Response` parameter (`print_bug.cs:38,305`, `bug_list.cs:427`, `util.cs:119,1163,1343`) | keep the parameter, type `btnet.runtime.web.HttpResponse`. `new HttpResponse(stringWriter)` (`bug.cs:1159`, `send_email.aspx.cs:484`) is supported by the runtime — **do not** change signatures to `Writer` |
| `HttpRequest Request` parameter | `btnet.runtime.web.HttpRequest` |
| `Request["k"]` (83/31) | `Request.get("k")`: QueryString → Form → Cookies → ServerVariables, first hit, multi-values comma-joined, `null` if absent |
| `Request.QueryString["k"]`, `Request.Form["k"]`, `.GetValues` | `Request.QueryString().get("k")`, `Request.Form().get("k")`, `.GetValues("k")` |
| `Request.RawUrl`, `Url`, `Path`, `UserHostAddress`, `Cookies`, `Files`, `ServerVariables` | same-named methods |
| `Session["k"]` (24/8), `Session.SessionID`, `Session.Abandon()` | `Session.get("k")` / `.set` / `.SessionID()` / `.Abandon()` over `HttpSession` (timeout 120 min, `Web.config:30`). `Session.set(k, null)` = remove; `get` of an absent key = `null` [C10]. `Session["session_cookie"]` is read but never written in source — preserved, no fix [C16] |
| `Application["k"]` (11/5) | `Application.get/set` on the `HttpApplicationState` singleton (= `ApplicationState.get()`, C32) (ConcurrentHashMap; `Lock()/UnLock()` = a ReentrantLock). `set(k, null)` = remove; `get` of absent = `null` [C10]: `Application["custom_columns_dataset"] = null` (`add_customfield.aspx.cs:285`) → `Application.set("custom_columns_dataset", null);` |
| `HttpRuntime.Cache[k]` (7/3) | `HttpRuntime.Cache().get/set` — ConcurrentHashMap, no expiry (`Authenticate.cs:84-157`); `set(k, null)` = remove (C10). `Cache.Add(k, v, deps, NoAbsoluteExpiration, …)` [C14] → `Cache().addIfAbsent(k, v)` (stores only if absent; returns the existing entry, or `null` if it stored — .NET `Cache.Add`); expiry/priority/callback arguments are dropped (every site passes `NoAbsoluteExpiration`). `Cache.Insert` = `set`, `Cache.Remove` = remove. Cache keys share one namespace (`Cache[username]` vs `Cache["Application"]`) — preserved, `// BUG(port): user named "Application" collides` [C15] |
| `Server.MapPath(p)` (4/3), `Util.GetAbsolutePath` | `Server.MapPath(p)` → path under `btnet.webroot` (default `port/web/webroot`); `MapPath(null)` → webroot |
| `HttpUtility.HtmlEncode` (16/8), `Server.HtmlEncode` | `HttpUtility.HtmlEncode`: null→null; encodes `< > & " '`(`&#39;`) and U+00A0–U+00FF as `&#NNN;` (.NET 4.x) |
| `HttpUtility.HtmlDecode` | HTML 4 named + numeric entities |
| `HttpUtility.UrlEncode` (5/4) | UTF-8, space→`+`, unreserved `A-Za-z0-9-_.!*()`, **lower-case** hex (`%2f`) |
| `HttpUtility.UrlDecode` | `+`→space, invalid `%` sequences kept literally |
| `Uri.EscapeDataString` | RFC 3986 unreserved kept, upper-case hex |
| `File.*`, `Directory.*`, `Path.*`, `StreamReader`/`File.OpenText` (35/13) | runtime `btnet.runtime.io.File/Directory/Path` with the same static API; they normalize `\` → `/` (source builds `"App_Data\\uploads"`, `Global.asax.cs`) |
| `File.AppendText(path)` → `StreamWriter` (`POP3Main.cs:153`) [N7] | runtime `File.AppendText(path)` returning runtime `btnet.runtime.io.StreamWriter` (§3: append, create if absent, UTF-8 without BOM, `WriteLine` appends `"\r\n"`) |
| `Stream`, `MemoryStream`, `FileStream` + `FileMode`/`FileAccess`/`FileShare` (`bug.cs:230,257,278,385,436,445,464,499,512`, `email.cs:90`, `Mime.cs:206-222`, `BugFromEmailController.cs:156,222`) [J-C6] | runtime `btnet.runtime.io.Stream` (abstract, `AutoCloseable`), `MemoryStream`, `FileStream`, `FileMode`, `FileAccess`, `FileShare` with the .NET members (§3). Declared type `Stream` stays `Stream` in every signature and field (`Bug.insert_post_attachment(…, Stream content, …)`, `BugPostAttachment.content`); `x.Position = 0` → `x.Position(0)`, `(int)x.Length` → `(int) x.Length()`, `x.Dispose()` → `x.Dispose()`. `java.io` streams are never used for these. `System.IO.StringWriter` (`bug.cs:1158`) → `java.io.StringWriter` (`new HttpResponse(Writer)` accepts it) |
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
- `ConfigurationManager.AppSettings[k]` [C26] → `Settings.get(k)` (runtime
  `btnet.runtime.config.Settings`; reads the same `NameValueCollection` as
  `GetSection("appSettings")`, so overrides apply; absent → `null`, no
  default logic). `btnet_service` reads its own `<exe>.config` (C18), not this.

**Logging, mail, search, processes, LDAP, SOAP, POP3**

| C# | Java |
|---|---|
| `LogManager.GetCurrentClassLogger()` + `log.Debug/Info/Fatal(x)` | `LoggerFactory.getLogger(<EnclosingClass>.class)` + `debug/info`; `Fatal` → `error(MarkerFactory.getMarker("FATAL"), …)` |
| `App_Start/LoggingConfig.cs` (NLog file + mail targets) | programmatic Logback in `LoggingConfig.Configure()`: rolling file `<log folder>/btnet_log.txt`, daily, level TRACE if `LogEnabled=1` else OFF. Line pattern pinned to NLog's default layout `${longdate}\|${level:uppercase=true}\|${logger}\|${message}` [J-C20]: `%d{yyyy-MM-dd HH:mm:ss.SSSS}\|%level\|%logger\|%msg%n` (`TODO(port)`: exact 4-digit fraction of NLog `longdate`; archive naming per NLog `ArchiveNumbering=Date`). Not judged. Mail target → `TODO(port): FATAL mail appender` |
| `SmtpClient`/`MailMessage`/`Attachment` (14/3) | runtime `btnet.runtime.mail.SmtpClient/MailMessage/Attachment/MailAddress` (same members) over Jakarta Mail; `deliveryMethod=specifiedPickupDirectory` writes `<dir>/<uuid>.eml` (active setting, `Web.config:52-56`). `ServicePointManager.ServerCertificateValidationCallback = …true` (`email.cs:242`) → runtime **static** `SmtpClient.TrustAllCertificates(true)`: process-wide and sticky for every later `SmtpClient`, like the `ServicePointManager` callback [C5] |
| OpenPop `Message`, `MessagePart`, `Pop3Client` (`Mime.cs`, `POP3Main.cs`) | runtime `btnet.runtime.mail.pop.Message/MessagePart/Pop3Client` (OpenPop member names) over Jakarta Mail `MimeMessage` / POP3 store |
| NEST `ElasticClient` (`Search/*`) | `BugSearchFactory` builds runtime `ElasticClient(uri, "btnet")`; search call [C29] `SearchResponse Search(String index, String jsonBody)` with `SearchResponse.Hits` = `List<Hit>`, `Hit { String Id; double Score; Map<String,Object> Source; Map<String,Highlight> Highlights; }` and `Highlight { String DocumentId; String Field; List<String> Highlights; }` — **top-level** classes in `btnet.runtime.search` (never `SearchResponse.Hit`), every member a public field, `Highlights` empty (never null) when the hit has no highlight [N1]. `:79-84` → `if (!hit.Highlights.isEmpty()) { var highlight = hit.Highlights.entrySet().iterator().next(); resultRow.set(ResultColumns.Source, highlight.getValue().Field); resultRow.set(ResultColumns.Text, highlight.getValue().Highlights.isEmpty() ? null : highlight.getValue().Highlights.get(0)); }` (`Any()`/`First()`/`FirstOrDefault()`; `null` → DBNull by `DataRow.set`, J-C11). Body built by `BugSearch` as below (`BugSearch.cs:55`); `BugSearch` issues: index [J-C10] `_client.Index(bugJson, i => i.Type("bug").Id(Convert.ToInt64(bugRow["bg_id"])))` (`:164`) → `_client.Index(bugJson, "bug", Convert.ToInt64(bugRow.get("bg_id")));` = `PUT {uri}/btnet/bug/{id}` with the same document fields; search `POST {uri}/btnet/bug/_search` `{"size":500,"query":{"query_string":{"query":…}},"highlight":{"pre_tags":["<span class='highlighted'>"],"post_tags":["</span>"],"fields":{"*":{}}}}`. `TODO(port): verify NEST 1.0.2 wire format`. Keep `IndexBug`'s try/catch: connection failures are logged, never thrown |
| `System.Diagnostics.Process` + `ProcessStartInfo` (`version_control.cs:30-70`) [C20] | `ProcessStartInfo` fields map onto one `ProcessBuilder`: `FileName` + `Arguments` → `new ProcessBuilder(Processes.command(FileName, Arguments))` (args split by `Processes.SplitArgs`, Windows `CommandLineToArgvW` rules); `WorkingDirectory = dir` → `if (!Strings.IsNullOrEmpty(dir)) { pb.directory(new java.io.File(dir)); }` (.NET treats null/empty as the current directory; `new File(null)` throws) [J-C13]; `RedirectStandardOutput/Error = true` → pipes (default); `UseShellExecute=false`/`CreateNoWindow` → nothing; read stdout then stderr fully as UTF-8, `waitFor()`. `configure_startinfo(ProcessStartInfo startInfo)` (`version_control.cs:20-26`) keeps its name and takes `ProcessBuilder startInfo`; its body is one comment per source assignment (`// WindowStyle = Hidden: dropped`, `// UseShellExecute = false: dropped`, `// RedirectStandardOutput/Error = true: pipes are the default`, `// StandardOutputEncoding = UTF8: stdout read as UTF-8`) [J-C13]. `Process.GetCurrentProcess().MainModule.FileName` → `AppPaths.executable()` [C18] |
| `Stopwatch` | runtime `Stopwatch` (`Start/Stop/ElapsedMilliseconds()`) |
| `LdapConnection.Bind` (`Authenticate.cs:176-207`) | JNDI `InitialLdapContext` per DN; `LdapAuthType=Basic` → `simple`; other values → `TODO(port)`; failure → exception → caught as in source |
| `TranslationService` (wsdl SOAP client, `btnet/TranslationService.cs`) | same class and members; every remote call throws `UnsupportedOperationException("TODO(port): SOAP translation service")`. `translate.aspx` keeps its error path |
| `HashAlgorithm.Create("MD5")` (`UpdatePassword.aspx.cs:64`) | `MessageDigest.getInstance("MD5")` |
| `ServiceBase` (`btnet_service.cs`) | `service` keeps `OnStart/OnStop` and gets `public static void main`; installer/controller members → `TODO(port): Windows service install` stubs |
| `XmlTextReader` (`POP3Main.cs:316+`) | runtime `btnet.runtime.io.XmlTextReader` (same members: `Read()`, `NodeType`, `Name`, `Value`, `Close()`, plus the attribute indexer `tr["value"]` → `tr.get("value")` = `GetAttribute`, `null` if absent [J-C4]; `XmlNodeType.Element` → runtime enum `btnet.runtime.io.XmlNodeType` (`POP3Main.cs:335-403`)) over StAX `XMLStreamReader`, **opened lazily** [C35]: the constructor only stores the path; the first `Read()` opens the file, so a missing config surfaces there as `FileNotFoundException` (runtime class) exactly as in .NET (`POP3Main.cs:427` catch) |
| `System.Net.Http.HttpClient` / `HttpClientHandler` / `FormUrlEncodedContent` (`POP3Main.cs:689-724`; the only "HttpWebRequest" in the file is the log string at `:734`) [J-C2, replaces the HttpWebRequest row] | `java.net.http.HttpClient`, same URLs and form fields; `new HttpClientHandler { AllowAutoRedirect = true, UseCookies = true, CookieContainer = new CookieContainer() }` + `using (var httpClient = new HttpClient(handler))` → one client per `using`, built with `.cookieHandler(new CookieManager())` + `.followRedirects(HttpClient.Redirect.NORMAL)` [C33]. `new FormUrlEncodedContent(dict)` → body `FormUrlEncodedContent.encode(dict)` (runtime `btnet.runtime.net`, §3: .NET encoding, `Content-Type: application/x-www-form-urlencoded`); `await httpClient.PostAsync(url, content)` → blocking `httpClient.send(…)` (POST, `BodyPublishers.ofString(body)`) on the async thread (below); `resp.EnsureSuccessStatusCode()` → `HttpResponses.EnsureSuccessStatusCode(resp)` (runtime: non-2xx → `HttpRequestException`, caught by `:732` and counted in `total_error_count`). `Encoding.Default.GetString(bytes)` (`:707`) → `Encoding.Default.GetString(bytes)` with runtime `Encoding.Default` = `windows-1252` (Windows ANSI, en-US), never UTF-8. Mark the `:721` content site `// TODO(port): verify .NET 4.5 FormUrlEncodedContent length limit (Uri.EscapeDataString throws "Invalid URI: The Uri string is too long." on large raw messages; caught at :732 as an error)` |

**Threads, timers, async, service paths**

| C# | Java | Site |
|---|---|---|
| `new Thread(p).Start()`, `Task.Run(() => x())` [C2, C22] | `Threads.start(p)` / `Threads.start(() -> x())` — runtime: `new Thread(p)`, `setDaemon(false)`, `start()`, returns the `Thread`. Hand-expanding it is not allowed where `Threads.start` fits [J-C25]. **Construction and `Start()` as separate statements:** (a) `t = new Thread(p);` immediately followed by `t.Start();` → `t = Threads.start(p);` (`POP3Main.cs:85-86,90-91`); (b) other statements in between → `t = new Thread(p); t.setDaemon(false);` at the construction site and `t.start();` at the `Start()` site, keeping the order (`POP3Main.cs:223-225`: construct, log, start) | `bug.cs:1201`, `tags.cs:119,125`, `email.cs:273`, `admin.aspx.cs:43` `Task.Run(() => search.IndexAll())` → `Threads.start(() -> search.IndexAll());` |
| `t.Abort()` [C19, N5] | `t.interrupt(); // TODO(port): Abort semantics` — the thread's loop checks `Thread.interrupted()` (and `Threads.Sleep` returns early) where the source loops. In `fetching_thread_proc` (`POP3Main.cs:186-194`), immediately after each `Threads.Sleep(2000)`: `if (Thread.currentThread().isInterrupted()) { return; }` — no "exiting fetching thread" log, as for an abort (without it the re-set flag makes `Sleep` return at once and the thread busy-spins; every respawn would leak one). The TODO also covers: an interrupt does not stop blocking socket I/O, so an aborted thread caught inside `do_work` may finish its fetch concurrently with the respawned one | `POP3Main.cs:222` `fetching_thread.Abort()` |
| `System.Timers.Timer` with `AutoReset = false`, re-armed by `resume()` from `do_work` [C17 CORRECTED] | `java.util.Timer` created **non-daemon** (`new Timer(false)`); `Start()` → `timer.schedule(new TimerTask(){ public void run(){ do_work(null, null); } }, interval)` — **one-shot**, re-armed by the translated `resume()` exactly where the source calls it; `Stop()`+`Dispose()` → `timer.cancel()`. `scheduleAtFixedRate`/`schedule(task, delay, period)` are BANNED. The only sanctioned scheduler (§1) | `POP3Main.cs:291,300-310` |
| `async void M(...)` with `await` [C33, J-C1 — replaces "statements before the first await run on the caller's thread; the rest is wrapped"] | `void M(...) { Threads.runAsync(firstAwait -> { <whole body> }); }`: the **whole** body, unchanged in structure (usings, loops, try/catch), runs on the async thread; the caller blocks until the body calls `firstAwait.run()`. The translator inserts `firstAwait.run();` immediately before the blocking call that stands for the **first `await` in execution order** (the lexically first await; calling it again later is a no-op), and each `await x` becomes that blocking call (`httpClient.send`). The runtime also releases the caller in a `finally` when the body ends or throws without reaching an await (connect failure, zero messages). This reproduces .NET exactly: everything up to the first await runs before the caller continues (connect/auth/list of `fetch_messages` finish before `fetch_messages_for_projects` moves to the next project), and everything after it — the rest of that iteration, later iterations, `QUIT`, the dispose of `client` — runs concurrently with the caller. Never wrap only a lexical suffix or an inner block (the first await at `:704` is nested in `using (client)` → `for` → `try` → `using (httpClient)`) | `POP3Main.cs:511` `fetch_messages` (awaits at :704, :722): `Threads.runAsync(firstAwait -> { … firstAwait.run(); HttpResponse<String> loginResponse = httpClient.send(…); … });` |
| `Process.GetCurrentProcess().MainModule.FileName` [C18] | `AppPaths.executable()` = system property `btnet.service.home` (default `user.dir`) + `"/btnet_service.exe"` — a virtual path, so `<exe> + ".config"` and log paths resolve beside it; the config **file name** `btnet_service.exe.config` is kept | `POP3Main.cs:75`, `btnet_service.cs:36` |

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
  `form`→`HtmlForm`, `head`→`HtmlHead`, `title`→`HtmlGenericControl`
  (`<title id="titl" runat="server">`, 20 pages, code sets `titl.InnerText`)
  [J-U10]; `asp:X`→runtime `X` (`DropDownList`,
  `ListBox`, `CheckBox`, `CheckBoxList`, `RadioButton`, `RadioButtonList`
  [J-U10] (`edit_org.aspx:48-189`), `Button`, `Label`,
  `DataGrid`, `Panel`, `HyperLink`, `LinkButton`). `asp:` tag names and
  attribute names are matched **case-insensitively** (`<asp:checkbox>`,
  `edit_attachment.aspx:26`) [J-U10]. Declarative attributes that
  code reads (`Text=`, `GroupName=`, `SelectionMode=`, `<asp:ListItem>`s) are
  set in the field initializer: `control(new CheckBox("x")).Text("Admin")`.
- **ID-less server elements** [J-U9]: a `runat="server"` element without an
  `ID` that code never references and that has no server event is literal
  markup (no field, no `bt:control`). Otherwise (`backup_db.aspx:27`
  `<input type="submit" … runat="server" onserverclick="on_backup">`) the
  inventory assigns the synthetic ID `_ctl<N>` (N = 1-based order of the
  ID-less server controls in the file), used for the field, `id` and `name`.
  ID-less `<form runat>`/`<head runat>` stay covered by B5/B6.
- Every page member referenced by the template is `public`.

**B2. Request lifecycle** (runtime `Page.ProcessRequest`, written once):

1. Bind intrinsics (`Request`, `Response`, `Server`, `Session`,
   `Application`, `Context`, `Cache`, `User`, `Page`=this, `Header`).
2. **Postback collection** [J-U5] = `Request.Form` for POST, else
   `Request.QueryString` (GET/HEAD; ASP.NET `DeterminePostBackMode`).
   `IsPostBack` = that collection contains `__VIEWSTATE` or `__EVENTTARGET`
   **and** the request is not a `Server.Transfer` target. So a GET server form
   (`mbugs.aspx:102` `method="get"`, `only_mine` submitted by `onchange`) is a
   postback and loads its posted values from the query string (B4).
3. `OnPreInit(EventArgs)` → `BasePage` authorization (B6).
4. `Page_Init`; then, on postback, load view state (B4), verify its MAC if
   `ViewStateUserKey` was set, then load posted values into controls.
5. `Page_Load` of the page, then of its master, then of user controls in
   markup order.
6. Postback events: the control named by `__EVENTTARGET`, or the submit
   button whose name is in the form, raises its handler — `OnClick="X"`
   (`admin.aspx:96`) → `reindexLink.OnClick(this::X)` in the constructor;
   `OnItemCommand="my_button_click"` (`backup_db.aspx:14`, `manage_logs.aspx:11`)
   → `MyDataGrid.OnItemCommand(this::my_button_click)`;
   `onserverclick="X"` on an `HtmlInputSubmit`/`HtmlInputButton`
   (`backup_db.aspx:27`) → `_ctl1.OnServerClick(this::X)`, raised when the
   button's name is in the postback collection [J-U9].
7. Save view state, render the template with model `page` (this) and
   `master`, and write: **Response buffer first, then the rendered template**
   (a `Response.Write` in `Page_Load` appears before `<!DOCTYPE>` exactly as in
   WebForms). The template is rendered **into the same Response buffer**
   (Thymeleaf's `Writer` is the buffer's writer, appending after what code
   already wrote), so render regions (B5, C8) and template text interleave in
   document order. Default content type `text/html; charset=utf-8`.
8. `ResponseEndException` (also when thrown during render; the runtime unwraps
   it from Thymeleaf's `TemplateProcessingException` cause chain) → send
   exactly what the buffer holds: code output, template output so far, and
   everything a render region wrote before `End` — .NET flushes it
   (`print_bugs2.aspx:81`) [C8]. Runtime author verifies Thymeleaf writes
   through without its own buffering (`TODO(port)` in the runtime only, never
   in translated units). `RedirectException` → 302. 
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
| `ValidateRequest="false"` [J-U4, replaces the closed list of 11] | class annotation `@ValidateRequest(false)` **iff** the `@Page` directive has `ValidateRequest="false"`, attribute name and value matched case-insensitively. Today that is 16 pages: backup_db, change_password, default, edit_bug, edit_comment, edit_custom_html, edit_dashboard, git_hook, hg_hook, manage_logs, mbug, search, send_email, svn_hook, translate, tropo_recording. All other pages validate (`Web.config:38,46`, `requestValidationMode="2.0"`): a value that contains `<` followed by a letter, `!`, `/` or `?`, or `&#`, throws `HttpRequestValidationException` → 500. **Granularity (2.0 mode) [J-U14]:** the first access to `Form`, `QueryString` or `Cookies` — including the runtime's own postback read (B2 step 2) and `Request.get(k)` — validates **every** value of that collection, except keys starting with `__`; later accesses do not re-validate. `TODO(port)` (runtime only): confirm against .NET 4.5 when the original runs (D-001) |
| `x.ClientID` (`search.aspx:129-164`, `bugs.aspx:67`, `send_email.aspx:70-76`, `add_attachment.aspx:12,18`, `edit_priority.aspx:11`, `default.aspx:81`, `forgot.aspx:60`) [J-U7] | `x.ClientID()` — returns exactly the `id` the template renders for that control: the `ID` for every control, and for the server form the value B5 renders (`frm` on `bugs.aspx`, `aspnetForm` on `search.aspx`), so `getElementById(asp_form_id)` (`bug_list.js:134-424`) finds it |
| Static/`<%@ Import %>` directives | nothing (Java imports) |

**B4. View state and control state.** Controls behave like their WebForms
counterparts because the runtime persists them: `__VIEWSTATE` = base64 JSON
of (a) list items of `DropDownList/ListBox/CheckBoxList/RadioButtonList/HtmlSelect`, (b) any
control property set by code (`InnerText`, `InnerHtml`, `Visible`,
`Disabled`, `Attributes`, `Text`, `Checked`, `Value` of non-input
controls, and explicitly `AutoPostBack`, `Enabled`, `Style` [J-U8]),
(c) DataGrid items [J-U1]: per item the cell texts (including
`Visible=false` columns) and the state of template child controls, so on
postback `Items`, `dgi.FindControl(id)` and `dgi.Cells[i].Text` work although
the grid is bound only on GET (`edit_user.aspx.cs:237-239`, read at
`:724-753`); items are recreated **before** posted values load,
(d) which checkboxes/radios were rendered visible and enabled [J-U11],
HMAC-SHA256 signed (key = app secret + `ViewStateUserKey`). On
postback, state is restored, then posted values are applied, read from the
postback collection (B2 step 2: `Form` for POST, `QueryString` for GET)
[J-U5]. **Posted values apply only to** [J-U11] (1) controls whose key is in
the postback collection, and (2) checkboxes/radios recorded in (d); for
those, an absent name means unchecked. Every other control keeps its
restored state (ASP.NET calls `LoadPostData` only for controls that
registered for postback or whose key was posted): an invisible checkbox keeps
its view-state `Checked` (`edit_self.aspx.cs:108-112,247` `use_fckeditor`
with `DisableFCKEditor=1`), an absent hidden field keeps its `Value`
(`edit_bug.aspx.cs:1302-1304`). Applied: inputs → `Value`; `HtmlTextArea` →
its one content state (`Value`/`InnerText`/`InnerHtml` are views of it, §3)
[J-U6], so `signature.InnerText` reads the posted text
(`edit_user.aspx.cs:511`, `edit_self.aspx.cs:259`); checkbox → `Checked` =
name present; radio → `Checked` = posted group value equals its value
attribute (B5); `CheckBoxList` item → selected = its name `x$<i>` present;
`RadioButtonList` → `SelectedValue`; select → selected item(s) by
value. A posted value not among the restored items of a select-type control →
500 (`enableEventValidation` default). Templates emit `__VIEWSTATE`,
`__EVENTTARGET`, `__EVENTARGUMENT` and the `__doPostBack` script inside every
server form via the runtime fragment `~{runtime/form :: hidden}`.

**B5. Templates (Thymeleaf).** Location `port/web/src/main/resources/templates/`
(§4). Translation of markup:

| Markup | Template |
|---|---|
| Literal HTML | copied unchanged (including `class`, `style`, inline JS) |
| `<form runat="server" …>` | `<form method="post" th:action="${page.FormAction()}" id="aspnetForm" name="aspnetForm" …>` + `<th:block th:replace="~{runtime/form :: hidden}"/>`. `method` = the declared `method`, lower-cased, default `post` (`mbugs.aspx:102` → `method="get"`) [J-U5]. `FormAction()` = current path **plus query string** (judge S8:41). On master pages `name="aspnetForm"` always (`EditBug.js:30,37` selects `form[name=aspnetForm]`); `id` = the declared `ID` when the content placeholder has `ClientIDMode="Static"` (`bugs.aspx:11` → `id="frm"`), else `aspnetForm` [J-U7]. Pages without a master: `id`/`name` = the form's declared `id`, else `ctl00` (`Util.get_form_name()`, `Web.config:853`). Declared `action`/`target`/`enctype`/`onsubmit` kept |
| Server control `<span runat="server" id="msg" class="err">` | `<span id="msg" class="err" bt:control="${page.msg}"></span>` — the runtime dialect processor applies `Visible` (false → element removed), `InnerText`/`InnerHtml`, `Value`, `Checked`, `Disabled`, `Attributes`, select options, and [J-U8] `Text` (Button `value`, Label inner HTML, CheckBox/RadioButton label text), `Enabled=false` → `disabled="disabled"`, `Style` → `style` (`search.aspx.cs:63-71` `Style["display"]`; `admin.aspx.cs:44-45` `reindexLink.Enabled`/`.Text`). Markup values of these properties are the field initializer's (B1), code changes persist (B4) |
| `name` of a form control | **logical name only** = the control `ID` (judge contract 2): `name="short_desc"`. A declared `name` on a server `input`/`select`/`textarea` other than a radio is **dropped** (ASP.NET renders the `UniqueID`): `edit_bug.aspx:113,239` `name="submit"` → `name="submit_button2"`/`name="submit_button"` (a field named `submit` would shadow `form.submit()`, `EditBug.js:37`) [J-U12]. Radio: `asp:RadioButton` → `name` = `GroupName`, `value` = the declared `value` attribute if present, else `ID` (.NET `RadioButton.ValueAttribute`) [J-U1]; `HtmlInputRadioButton` → declared `name`. Checkbox value `on`. **Inside a DataGrid item** [J-U1]: `name="<GridID>$ctl<NN>$<GroupName or ID>"`, NN = ASP.NET item numbering, two digits, header `ctl01`, first item `ctl02` — the logical name after the last `$` stays `permissions`, and each row is its own radio group (judge `Form` keeps one value list per real name; `S6.savingReporterUserDowngradesReporterPermission` reads the first row's `permissions` = `"3"`). `CheckBoxList` item `i` → `name="x$<i>"` (logical name `<i>`, as the original's `ctl00$body$lstAttachments$<i>`) [J-U10] |
| `id` of any element | exactly the source `ID`/`id` (no `ctl00_body_` prefix, judge N4). Inside a DataGrid item: `id="<GridID>_ctl<NN>_<ID>"` [J-U1]; list items: `x_<i>` |
| `asp:DropDownList` / `asp:ListBox` / any list control with `AutoPostBack` [J-U8, replaces the DropDownList-only literal row] | `<select id="x" name="x" bt:control="${page.x}">` — **no literal `onchange` and no literal `__doPostBack` in the template**; the declared `onchange` is a field-initializer attribute and `bt:control` renders `onchange` from the current `AutoPostBack` property: declared script first, then `__doPostBack('x','')` (`<declared>;__doPostBack('x','')`, or `__doPostBack('x','')` alone). `search.aspx:246-247` + `search.aspx.cs:86` (`project.AutoPostBack = false` on GET, persisted, read at `:144`) |
| `asp:RadioButtonList ID=x` [J-U10] | per item `i`: `<input type="radio" id="x_<i>" name="x" value="<item value>">` + `<label for="x_<i>">text</label>`, rendered by `bt:control` (layout per `RepeatDirection`); posted value → `SelectedValue` |
| `asp:CheckBoxList ID=x` [J-U10] | per item `i`: `<input type="checkbox" id="x_<i>" name="x$<i>">` + label, rendered by `bt:control`; checked = name present (`send_email.aspx:160`) |
| `asp:Label ID=x` [J-U10] | `<span id="x" bt:control="${page.x}"></span>`; `Text` → inner HTML (`translate.aspx:165`) |
| `asp:CheckBox ID=x Text=T` | `<input type="checkbox" id="x" name="x" bt:control="${page.x}"><label for="x" th:text="${page.x.Text()}">T</label>` |
| `asp:Button ID=b Text=T OnClick=…` | `<input type="submit" id="b" name="b" bt:control="${page.b}" value="T">` |
| `asp:DataGrid` (6 sites/3 files) | `<table id="…" bt:control="${page.MyDataGrid}"></table>`; runtime `DataGrid` renders BoundColumn/HyperLinkColumn/ButtonColumn/**TemplateColumn** and posts commands via `__doPostBack` |
| `asp:TemplateColumn` `<ItemTemplate>` (`edit_user.aspx:386-401`, `edit_user_permissions2.aspx:163-178`) [J-U1] | runtime `TemplateColumn` (§3) built in the field initializer with an **item factory** lambda that creates the template's controls for one row (`item -> { var none = new RadioButton("none"); none.GroupName("permissions"); none.Text("none"); none.Attributes().set("value", "0"); … item.Add(none); … }`); each `<%# expr %>` property binding becomes a binding lambda over the row's `DataRowView`, run at `DataBind`: `Checked='<%# ((int)((DataRowView)Container.DataItem)["pu_permission_level"] == 0) %>'` → `(item, drv) -> ((RadioButton) item.FindControl("none")).Checked((int) drv.get("pu_permission_level") == 0)`. These `<%# %>` are **not** template expressions; nothing of the ItemTemplate appears in the Thymeleaf file |
| `<%= expr %>` / `<% Response.Write(expr); %>` where expr is a `String` field or no-arg method of the page, **in element content** (not inside `<script>`/`<style>`, a start tag or an attribute value — those follow the non-element row below) [J-U2] | `<th:block th:utext="${page.expr}"/>` |
| `<%: expr %>` or `<% Response.Write(HttpUtility.HtmlEncode(expr)); %>`, same condition | `th:text` (decoded text is identical; markup bytes are not judged) |
| Any other code region (`<% … %>` span from an opening `<%` to its brace-balanced end, incl. the literal markup between its blocks) **without** runat=server elements inside | one method `public String __renderN()` (N = 1-based order of the region in the source file) that writes the literal markup and `Response.Write` output **directly to the page's `Response` buffer** (no local `StringWriter`) and returns `""` [C8, replaces the local-writer rule]; template `<th:block th:utext="${page.__renderN()}"/>`. A `Response.End()` inside the region therefore keeps everything written so far (B2 step 8). `<%= e %>` inside → raw write; `<%: e %>` → `HttpUtility.HtmlEncode`. Example: `<% foreach (var r in rows) { %><tr>…</tr><% } %>` → `public String __render3() { for (…) { Response.Write("<tr>…</tr>"); } return ""; }`. Applies to element-content regions only [J-U2] |
| A top-level code region in a **non-element position** [J-U2] (45 regions/22 files today, inventory tag `TEXT_REGION`): 33 inside `<script>` content (`search.aspx:19-366`, `bugs.aspx:66-67`, `BugList.ascx:128-129`, `MainMenu.ascx:123`, `default.aspx:81`, …), 12 inside a start tag or attribute value (`edit_bug.aspx:19-22,27,50`, `edit_dashboard.aspx:71`, …) | the region becomes `public String __textN()` that **returns** the text it would write — literal text, `Strings.cs(x)` for `<%= x %>`/`Response.Write(x)`, `HttpUtility.HtmlEncode(x)` for `<%: x %>`, built in a local `StringBuilder` — with **no** Response writes (C8's direct write does not apply: Thymeleaf is still evaluating the tag/text node). Placement: (a) inside `<script>`/`<style>`: the **whole element body** (literal JS plus every region, in order) is one method `public String __scriptN()` and the template is `<script …declared attrs… th:utext="${page.__scriptN()}"></script>` (no `th:inline` — it would also rewrite literal `[[`/`[(` in the JS); (b) inside an attribute value: `th:attr="<name>=${page.__textN()}"`, the method returning the **complete** attribute value (literal parts included): `class="custom-collapse <%=id > 0 ? "…" : "" %>"` → `th:attr="class=${page.__textN()}"`. Because `th:attr` HTML-escapes what it is given, a method used in (b)/(c) returns the **unescaped** value: `<%: x %>` and `<%= x %>` both contribute `Strings.cs(x)` and literal parts are copied with their entities decoded (the decoded attribute value equals the original's; only escaping bytes differ, which are not judged); (c) a start-tag region that emits a whole attribute (`edit_bug.aspx:22` `<%: … ? "data-use-fck-editor=''" : "" %>`) → one `th:attr` entry per attribute it can emit, the method returning the value or `null` (= attribute absent). A `Response.End()` in such a region → `TODO(port)` (none today) Scope [J-U2a]: only **top-level** non-element regions get `__textN`/`__scriptN`; a non-element region nested inside an enclosing `foreach`/`if` region is part of that enclosing `__renderN()` and is written directly by it (C8) — 19 such sites today, e.g. `tasks.aspx:161` (inventory rows say which) |
| A code region whose blocks enclose runat=server elements (`edit_bug.aspx:101-115`) | `th:if="${page.__condN()}"` / `th:each="item : ${page.__itemsN()}"` on the enclosing element; `__condN()` returns the C# condition |
| `<%# Eval("x") %>` (8 sites/2 files) outside a DataGrid `TemplateColumn` (inside one: the TemplateColumn row) | `th:text`/`th:utext` over the bound row inside `th:each` |

**Host class and numbering of region methods** [J-U3]: `__renderN`, `__textN`,
`__scriptN`, `__condN` and `__itemsN` live on the class compiled from the
**markup file that contains them**, and N counts per file — one counter for
all kinds, the 1-based order of top-level regions in that file (`__scriptN`
takes the N of the first region in its `<script>`). Page template →
`${page.__renderN()}`; master layout → `${master.__renderN()}`; `.ascx`
fragment → `${uc.__renderN()}`. So `LoggedIn.Master:36`, `MainMenu.ascx:4`
and a content page's first region never collide.
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
- `LoggedOut.Master` → layout `templates/layouts/LoggedOut.html` [J-U3,
  replaces "dead code"]. Its Java unit `btnet.pages.LoggedOut` mirrors
  ASP.NET's generated `ASP.loggedout_master : btnet.LoggedIn`: it is declared
  `public class LoggedOut extends LoggedIn` (keep
  `// BUG(port): markup Inherits btnet.LoggedIn` on the class), keeps the
  members of `LoggedOut.Master.cs`/`.designer.cs` (its empty `Page_Load`
  overrides LoggedIn's, which is also empty), and holds LoggedOut.Master's
  render regions (`LoggedOut.Master:21-25`
  `Response.Write(Application["custom_logo"])` → `__render1()`, called as
  `${master.__render1()}`). Pages using LoggedOut.Master (`default.aspx`,
  `forgot.aspx`) declare `public final LoggedIn Master = master(new LoggedOut())`
  and use the LoggedOut layout. The inherited `MainMenu` field is never
  rendered by the LoggedOut layout (the original's is null): mark
  `// BUG(port): MainMenu exists but is not rendered`.
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
  Inside a fragment, controls are `${uc.<ID>}` and the `.ascx`'s own region
  methods are `${uc.__renderN()}` (B5 host rule) [J-U3].
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
  throws `HttpException` (as in .NET, caught at `util.cs:54-61`) and to which
  `HttpContext.Current()` is bound for the duration of the call [J-U13], and
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
  default, judge S5:41). `SecurityConfig` calls
  `new Startup().Configuration(builder)` at boot and takes that redirect
  path from the recorded `CookieAuthenticationOptions.LoginPath` [J-C14].
- Windows/NTLM auth (`loginnt.aspx`) is out of scope: `Response.StatusCode = 401; Response.End();`
  (`loginnt.aspx:191`) translates literally and **no** `WWW-Authenticate:
  NTLM` challenge is produced — keep `// TODO(port): NTLM challenge (Windows auth out of scope)` [C9].
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
| `btnet.runtime` | `Convert`, `Int32`, `Decimal`, `DateTimes` (`Parse`, `ticks`), `Strings` (`cs`, `Format`, `IsNullOrEmpty`, `Split`, `Trim*`, `IndexOf`, `join`, `CULTURE_COMPARER`, `ToStringStrict` [J-C17]), `StringSplitOptions` (int constants, J-C5), `DotNetFormat`, `CultureInfo`, `NumberStyles` (int constants, J-C5), `DotNetRegex` (+`RegexOptions` (int constants, J-C5), `Match`, `MatchEvaluator`), `Ref<T>`, `Nulls` (`coalesce` ×2, J-C7), `DBNull`, `Environment`, `Encoding` (`UTF8`, `Default` = windows-1252, J-C2), `Threads` (`Sleep`, `start`, `runAsync`), `Stopwatch`, `Collections2`, `NameValueCollection`, `StringDictionary` [J-C4], `Processes` (`SplitArgs`, `command`), `Exceptions` (`wrap`, `StackTrace`, `baseOf`, `dotNetToString`, `rethrowIfEnd`), `AppPaths` (`executable`, `MapPath`), `EventArgs`, `DotNetException`, exception classes (`FormatException`, `OverflowException`, `InvalidCastException`, `ArgumentException`, `ArgumentNullException`, `ArgumentOutOfRangeException`, `IndexOutOfRangeException`, `KeyNotFoundException`, `ConfigurationErrorsException`, `SecurityException`, `FileNotFoundException`, `DirectoryNotFoundException` [N7], `IOException` [J-C6]) |
| `btnet.runtime.data` | `DataSet`, `DataTable`, `DataColumn`, `DataColumnCollection`, `DataRow`, `DataRowCollection`, `DataView`, `DataRowView`, `DataTableSerializer`, `SqlConnection`, `SqlCommand`, `SqlParameter`, `SqlParameterCollection`, `SqlDataAdapter`, `SqlDataReader`, `SqlDbType`, `CommandBehavior`, `SqlException`, `SqlBatch`, `DbContext`, `DbSet<T>`, `EntityTypeConfiguration<T>`, `DbModelBuilder` |
| `btnet.runtime.config` | `ConfigurationManager`, `Settings`, `WebConfigToProperties` (one-time generator), `BtnetApplication` (Spring Boot main, MVC config: case-insensitive paths, resource resolver, dialect, filters), `JsonConfig` |
| `btnet.runtime.io` | `File` (+`AppendText` [N7]), `Directory`, `Path`, `StreamReader`, `StreamWriter` [N7], `Stream`, `MemoryStream`, `FileStream`, `FileMode`, `FileAccess`, `FileShare` [J-C6], `XmlTextReader` (lazy; +`get(attr)` [J-C4]), `XmlNodeType` [J-C4] |
| `btnet.runtime.net` [J-C2] | `FormUrlEncodedContent` (`encode(Map)`), `HttpResponses` (`EnsureSuccessStatusCode`), `HttpRequestException` |
| `btnet.runtime.web` | `HttpContext` (`Current()`), `HttpRequest`, `HttpResponse`, `HttpServerUtility`, `HttpSessionState`, `HttpApplicationState`, `ApplicationState` (`get()`), `SelectListItem`, `UrlHelper`, `RequestContext` [N2], `HttpRuntime` (`Cache()`), `HttpCookie`, `HttpPostedFile`, `HttpUtility`, `HttpApplication`, `HttpException`, `HttpRequestValidationException`, `Page`, `MasterPage`, `UserControl`, `ValidateRequest` (annotation), `ResponseEndException`, `TransferException`, `RedirectException`, `ViewStateCodec`, `RequestContextFilter`, `LowercaseResourceResolver`, `Scripts`, `BundleCollection`, `ScriptBundle`, `ApiController`, `ApiBody`, `FromUri`, `Required` (annotation), `ModelStateDictionary` |
| `btnet.runtime.web.ui` | `Control`, `HtmlControl`, `HtmlGenericControl`, `HtmlAnchor`, `HtmlForm`, `HtmlHead`, `HtmlImage`, `HtmlInputText`, `HtmlInputPassword`, `HtmlInputHidden`, `HtmlInputCheckBox`, `HtmlInputRadioButton`, `HtmlInputSubmit`, `HtmlInputButton`, `HtmlInputFile`, `HtmlSelect`, `HtmlTextArea`, `DropDownList`, `ListBox`, `CheckBox`, `CheckBoxList`, `RadioButton`, `Button`, `Label`, `Panel`, `HyperLink`, `LinkButton`, `RadioButtonList` [J-U10], `DataGrid` (+ columns incl. `TemplateColumn` [J-U1], `DataGridItem`, `DataGridCommandEventArgs`), `ListControl` (base of the list controls, J-U18), `ListItem`, `ListItemCollection`, `AttributeCollection`, `CssStyleCollection`, `BtnetDialect` (`bt:control`); templates `templates/runtime/form.html` |
| `btnet.runtime.security` | `SecurityConfig`, `ClaimsIdentity`, `ClaimsPrincipal`, `Claim`, `ClaimTypes`, `IIdentity`, `IPrincipal`, `IOwinContext`, `AuthenticationManager`, `IAppBuilder`, `CookieAuthenticationOptions`, `PathString` [J-C14], `Rfc2898DeriveBytes` |
| `btnet.runtime.mail` (+`.pop`) | `SmtpClient`, `MailMessage`, `MailAddress`, `Attachment`, `btnet.runtime.mail.MailPriority` (enum `Normal`, `Low`, `High`; always written fully qualified, §4 [J-C15]); `pop.Pop3Client`, `pop.Message`, `pop.MessagePart` |
| `btnet.runtime.search` | `ElasticClient` (thin HTTP wrapper; `Search`, `Index` [J-C10]), `SearchResponse`, `Hit`, `Highlight` [N1] (all top-level) |

**Exact semantics of the members added at gate 02** (amendment ids in brackets;
the runtime author implements exactly this, translators rely on it):

- `Strings.join(String sep, Object[] | Iterable<?> xs)` [C3]: elements joined
  with `sep`; each element `null`/`DBNull` → `""`, else `Convert.ToString(e)`;
  empty input → `""`; `sep == null` → `""` separator (.NET).
- `Strings.IndexOf(String s, String|char v, int start)` [C27]: `start < 0 ||
  start > s.length()` → `ArgumentOutOfRangeException("startIndex")`; else
  `s.indexOf(v, start)` (ordinal). Existing `IndexOf(s, v, StringComparison)`
  overload unchanged.
- `Strings.CULTURE_COMPARER` [C20, C31]: `Comparator<String>` =
  `java.text.Collator.getInstance(Locale.US)` at `TERTIARY` strength (case-
  and accent-sensitive, lower-case before upper-case on otherwise-equal
  strings, like .NET `StringComparer.CurrentCulture` en-US); `null` sorts
  first. Replaces the name `CURRENT_CULTURE` (same object).
- `DateTimes.ticks(LocalDateTime dt)` [C20]: `long` = 100 ns intervals since
  0001-01-01T00:00:00 (= .NET `DateTime.Ticks`, `Kind` ignored).
  `DateTimes.Parse` failure → `FormatException("String was not recognized as a valid DateTime.")` [C14].
- `Exceptions.baseOf(Throwable e)` [C14]: follow `getCause()` to the innermost
  non-null cause; `e` if none. `Exceptions.dotNetToString(Throwable e)`:
  `<.NET type name>: <message>` (message omitted with its `": "` when null),
  inner exceptions as ` ---> <dotNetToString(cause)>`, then stack lines; the
  type-name map is a fixed table in the runtime (unknown → Java FQN). Stack
  text is never compared by the judge. `Exceptions.rethrowIfEnd(Throwable t)`
  [C30]: `if (t instanceof ResponseEndException r) throw r;`.
- `DotNetException extends RuntimeException` [C11]: `.NET System.Exception`;
  constructors `()` (message `"Exception of type 'System.Exception' was thrown."`),
  `(String)`, `(String, Throwable)`. `SecurityException` [C26],
  `ArgumentOutOfRangeException`, `IndexOutOfRangeException`,
  `FileNotFoundException`, `FormatException` extend it (or the JDK parent
  named in §2A); all unchecked.
- `ResponseEndException extends Error`, subclasses `RedirectException`,
  `TransferException`; message `"Thread was being aborted."` [C7].
- `SelectListItem` (`btnet.runtime.web`) [C6]: public fields `String Text`,
  `String Value`, `boolean Selected` (default `false`); no-arg constructor
  and `(String text, String value)`; Jackson/Thymeleaf see the fields.
- `ApplicationState.get()` [C32]: returns the one `HttpApplicationState`,
  created at class-load, before `Application_OnStart`; the same object the
  pages' `Application` field points to. `HttpApplicationState` /
  `HttpSessionState` / `HttpRuntime.Cache()` [C10]: `set(k, null)` removes,
  `get(absent)` → `null`; `Cache().addIfAbsent(k, v)` [C14] = `putIfAbsent`,
  returns the previous value or `null`.
- `HttpContext.Current()` [C34, J-U13]: request-bound `ThreadLocal`, `null` off a
  request thread, except that the runtime also binds it to the startup
  pseudo-context (B6) for the duration of `Application_OnStart`; exposes `Request()`, `Response()`, `Session()`,
  `Application()`, `Server()`, `User()`, `Cache()`.
- `AppPaths` [C18, C32]: `executable()` = `System.getProperty("btnet.service.home",
  System.getProperty("user.dir")) + "/btnet_service.exe"` (never needs to
  exist); `MapPath(p)` = `Server.MapPath(p)` without a request.
- `Threads.start(Runnable)` [C2, C22]: `new Thread(r)`, `setDaemon(false)`,
  `start()`, returns it. `Threads.runAsync(Consumer<Runnable> body)` [C33,
  J-C1]: creates a one-shot `java.util.concurrent.CountDownLatch(1)` and a
  release callback `firstAwait` (= `countDown()`, idempotent); starts one
  non-daemon platform thread running `try { body.accept(firstAwait); } finally
  { firstAwait.run(); }` with an uncaught-exception handler that logs at error
  (`TODO(port)` in the runtime: .NET would crash the process on an unhandled
  `async void` exception); the **calling** thread then waits on the latch and
  returns once it is released. If the caller is interrupted while waiting, it
  re-sets its interrupt flag and returns (as `Threads.Sleep`). `Threads.Sleep(ms)` on
  interrupt: re-sets the flag and returns early (C19).
- `XmlTextReader(String path)` [C35]: stores the path; first `Read()` opens
  it (missing → `FileNotFoundException("Could not find file '<path>'.")`);
  `Read()` returns `false` at end; `Close()` idempotent. `get(String attr)`
  [J-C4] = `GetAttribute(attr)` on the current element, `null` if absent;
  `NodeType()` returns runtime enum `XmlNodeType` (`Element`, `EndElement`,
  `Text`, `Whitespace`, `XmlDeclaration`, `Comment`, `None`, …, .NET names).
- `SmtpClient.TrustAllCertificates(boolean)` [C5]: **static**; sets a
  process-wide flag read by every later `SmtpClient.Send` (trust-all
  `SSLSocketFactory`, `mail.smtp.ssl.trust=*`).
- `Settings.get(String key)` [C26]: `ConfigurationManager.GetSection("appSettings").get(key)`.
- `ElasticClient.Search(String index, String jsonBody)` [C29]: `POST
  {uri}/{index}/bug/_search`, returns `SearchResponse { List<Hit> Hits; }`,
  `Hit { String Id; double Score; Map<String,Object> Source; Map<String,Highlight> Highlights; }` from
  `hits.hits[]._id/_score/_source/highlight`; HTTP/connection failure → `DotNetException`.
  [N1] `Hit.Highlights` is a `LinkedHashMap` keyed by field name in the JSON
  object order of `hits.hits[i].highlight`, **empty, never null**, when there
  is no `highlight` (NEST 1.0.2 `HighlightFieldDictionary`); `Highlight {
  String DocumentId; String Field; List<String> Highlights; }` filled from
  `_id`, the field name and the fragment array. `Hit`, `Highlight`,
  `SearchResponse` are top-level classes; every member is a public field.
  `TODO(port): verify NEST 1.0.2 wire format` stays.
- `ElasticClient.Index(String jsonBody, String type, long id)` [J-C10]: `PUT
  {uri}/{defaultIndex}/{type}/{id}` (`btnet` from the constructor); HTTP or
  connection failure → `DotNetException` (caught by `IndexBug`'s catch).
  `TODO(port)` in the runtime: whether NEST 1.0.2 throws on a non-2xx index
  response or only on connection failure.
- `DotNetRegex` [C21, C26]: `RegexOptions.Compiled`/`CultureInvariant` are
  accepted no-ops; `IgnorePatternWhitespace` → escape `#` and whitespace
  inside `[...]`, then `Pattern.COMMENTS`; `Matches` → `List<Match>`.

**Exact semantics of the members added by the joint audit** (2026-11-03; ids
in brackets):

- `ClaimsIdentity` [J-C3]: keeps an ordered `List<Claim>` in insertion order,
  **duplicates kept** (never a map keyed by type). `FindFirst(type)` returns
  the **first** match in insertion order (`null` if none); `HasClaim(type,
  value)` and `ClaimsPrincipal.IsInRole(role)` match **any** claim. So the
  duplicate `CanOnlySeeOwnReportedBugs` claim of `Security.cs:73` is shadowed
  by `:49` (§0.1).
- `IPrincipal` [J-C16]: `Identity()` → `IIdentity`, `IsInRole(String)`;
  `ClaimsPrincipal implements IPrincipal`; the page `User` field is typed
  `IPrincipal`-compatible so `Logic.OnAuthorize(IPrincipal)` needs no cast.
- `.NET [Flags]` enums [J-C5] are `public final class X { private X() {}
  public static final int … }` with the .NET values, and every parameter of
  these types is `int`: `CommandBehavior` (`Default = 0`, `SingleResult = 1`,
  `SchemaOnly = 2`, `KeyInfo = 4`, `SingleRow = 8`, `SequentialAccess = 16`,
  `CloseConnection = 32`); `RegexOptions` (`None = 0`, `IgnoreCase = 1`,
  `Multiline = 2`, `ExplicitCapture = 4`, `Compiled = 8`, `Singleline = 16`,
  `IgnorePatternWhitespace = 32`, `RightToLeft = 64`, `ECMAScript = 256`,
  `CultureInvariant = 512`); `NumberStyles` (`None = 0`, `AllowLeadingWhite =
  1`, `AllowTrailingWhite = 2`, `AllowLeadingSign = 4`, `AllowDecimalPoint =
  32`, `AllowThousands = 64`, `AllowExponent = 128`, `Integer = 7`, `Number =
  111`, `Float = 167`, `Any = 511`); `StringSplitOptions` (`None = 0`,
  `RemoveEmptyEntries = 1`). `SqlDbType`, `MailPriority` stay Java enums.
- `Stream` family (`btnet.runtime.io`) [J-C6]: abstract `Stream implements
  AutoCloseable` with `int Read(byte[] buf, int off, int count)`, `void
  Write(byte[] buf, int off, int count)`, `void CopyTo(Stream dest)`, `long
  Position()`/`void Position(long)`, `long Length()`, `void Dispose()`, `void
  Close()`, `close()` (= `Dispose()`; all idempotent). `MemoryStream(byte[])`
  (read/write over a copy-on-grow buffer, `ToArray()`); `FileStream(String
  path, int/enum FileMode, FileAccess[, FileShare])`: `FileMode.CreateNew` on an
  existing file → runtime `IOException("The file '<path>' already exists.")`
  (never truncates), `Open` on a missing file → `FileNotFoundException("Could
  not find file '<path>'.")`, `Create` truncates; `FileShare` is accepted and
  ignored. Paths normalize `\` → `/` like `File`.
- `Nulls.coalesce(T a, T b)` and `Nulls.coalesce(T a, Supplier<T> b)`
  [J-C7]: return `a` if non-null, else `b` / `b.get()` (the supplier is not
  called when `a` is non-null).
- `Strings.ToStringStrict(Object o)` [J-C17]: `o == null` →
  `throw new NullPointerException()`; else `Convert.ToString(o)`.
- `StringDictionary` (`btnet.runtime`) [J-C4]: `get(String key)` /
  `set(String key, String value)` / `ContainsKey` / `Remove` /
  `Count()` / `Keys()`, all lower-casing the key with
  `toLowerCase(Locale.ROOT)` internally; `get(absent)` → `null`;
  `set(k, null)` stores `null` (it does not remove).
- `DataRow.set` coercion and `Fill` duplicate-name suffixing [J-C11, J-C12]:
  exactly as §2A "DataSet". `Convert.ToDecimal(Double)`, `Convert.ToInt64`,
  `Convert.ToString(Throwable)` [J-C11, J-C10, J-C19]: as the §2A Convert table.
- `ArgumentException(String message, String paramName)` [J-C19]: the second
  argument is the parameter name, never a format argument; `getMessage()` =
  `message + "\r\nParameter name: " + paramName` (.NET 4.5; `paramName` null
  or empty → `message` alone). `BugQueryExecutor.cs:37,116` translate
  verbatim (`new ArgumentException("Invalid order by column specified: {0}",
  orderBy)`) — never wrapped in `Strings.Format`.
- ADO.NET ownership [N3]: if `SqlCommand.close()` runs while a
  `SqlDataReader` it produced is open, it transfers ownership of its JDBC
  `Statement` to that reader and does not close it (`dbutil.cs:98-104`,
  reader returned from inside `using (SqlCommand)`). `SqlDataReader.Close()`/
  `close()` closes its `ResultSet` and `Statement`, and the `SqlConnection`
  when it was opened with `CommandBehavior.CloseConnection`.
  `SqlCommand.close()`, `SqlConnection.Close()`/`close()` and
  `SqlDataReader.close()` are idempotent (covers the source's `conn.Close()`
  inside `using`).
- `File.AppendText(String path)` / `StreamWriter` [N7]: opens in append
  mode, creates the file if absent; missing directory → runtime
  `DirectoryNotFoundException("Could not find a part of the path '<path>'.")`;
  encoding UTF-8 **without BOM**; `WriteLine(String)` writes the value +
  `Environment.NewLine` (`"\r\n"`), `Write(String)` writes it as is;
  `Close()`/`close()` flushes and is idempotent. `StreamWriter implements
  AutoCloseable`.
- `btnet.runtime.net` [J-C2]: `FormUrlEncodedContent.encode(Map<String,String>
  m)` = .NET 4.5: for each entry in map order, `Uri.EscapeDataString(k)` +
  `"="` + `Uri.EscapeDataString(v)` with every `%20` replaced by `+`
  (null/empty → `""`), joined with `&`; sent as `application/x-www-form-urlencoded`.
  Translators build the map as `LinkedHashMap` in the source's initializer
  order (§2A `Dictionary`). `HttpResponses.EnsureSuccessStatusCode(HttpResponse<?> r)`:
  status outside 200–299 → `throw new HttpRequestException("Response status
  code does not indicate success: <code> (<reason phrase>).")`;
  `HttpRequestException extends DotNetException` (.NET name
  `System.Net.Http.HttpRequestException` in `dotNetToString`).
  `Encoding.Default` = `windows-1252`, decoding the five unassigned bytes
  `0x81 0x8D 0x8F 0x90 0x9D` to U+0081… (the same code point, as .NET) instead
  of U+FFFD.
- `UrlHelper(RequestContext ctx)` [N2] (`HttpRequest.RequestContext()` returns
  the opaque runtime `RequestContext`): `IsLocalUrl` = MVC 5.2.3 →
  System.Web.WebPages 3.2 `RequestExtensions.IsUrlLocalToHost`, string-only
  (no `Uri` parsing, trimming or decoding):
  ```java
  public boolean IsLocalUrl(String url) {
      if (url == null || url.isEmpty()) return false;                 // url.IsEmpty()
      if (url.charAt(0) == '/') {
          if (url.length() == 1) return true;                         // "/"
          return url.charAt(1) != '/' && url.charAt(1) != '\\';       // "/x" ok; "//x", "/\x" rejected
      }
      if (url.charAt(0) == '~') return url.length() > 1 && url.charAt(1) == '/';  // "~/x"
      return false;                                                   // "bugs.aspx", "http://…", "\\x"
  }
  ```
- OWIN startup types (`btnet.runtime.security`) [J-C14]: `IAppBuilder` with
  `UseCookieAuthentication(CookieAuthenticationOptions o)` (records `o`);
  `CookieAuthenticationOptions` with public fields `String
  AuthenticationType`, `PathString LoginPath`; `PathString(String value)`
  with `Value()` and `toString()` = value. `SecurityConfig` runs
  `new Startup().Configuration(builder)` at boot (B7).
- `HtmlTextArea` [J-U6]: `Value`, `InnerText` and `InnerHtml` are views of one
  content state, as in .NET (`Value` ⇄ `InnerHtml`; `InnerText` = the
  HTML-decoded content; setting `InnerText` stores it encoded). The posted
  value sets that state. `TODO(port)` in the runtime only: pin the exact
  encode/decode against .NET 4.5.
- `ListControl.SelectedValue(String v)` [J-U18]: if items exist, select the
  item with that value; none found → `ArgumentOutOfRangeException("'<ID>' has a
  SelectedValue which is invalid because it does not exist in the list of
  items.\r\nParameter name: value")`. Always cache `v`; `DataBind` (after
  rebuilding the items) applies the cached value, with the same exception if
  it is missing (`bugs.aspx.cs:67-76` sets `query.SelectedValue` before
  `query.DataBind()`: S2 `?queryId` selection works; a stale `queryId` is a
  500, as in the original).
- `DataGrid` / `TemplateColumn` [J-U1]: `TemplateColumn(String headerText,
  Consumer<DataGridItem> itemFactory, BiConsumer<DataGridItem, DataRowView>
  binding)`. At `DataBind` the grid creates one `DataGridItem` per row
  (naming container `ctl<NN>`, B5), runs every TemplateColumn's factory then
  its binding; BoundColumn cells hold `Convert.ToString(value)` (also when
  `Visible=false`; such cells are not rendered). Items (cell texts, template
  control state) persist in view state (B4) and are recreated on postback
  before posted values load. `Items()`, `dgi.FindControl(id)`,
  `dgi.Cells().get(i).Text()` work on both.
- `Control.ClientID()` [J-U7]: exactly the `id` the template renders for the
  control (B5), including grid-item and server-form ids.

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
  `@RequestMapping`. **Name clash [J-C15]:** when a runtime type's simple name
  equals an application type's simple name, the runtime type is written fully
  qualified and never imported: `Mail/email.cs:142-147` compares the app enum
  `btnet.Mail.MailPriority` and assigns
  `msg.Priority(btnet.runtime.mail.MailPriority.Normal)`;
  `priority == MailPriority.Normal` stays the same-package app enum.
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
| D-005 | 2026-11-03 | Rulebook amendments C1–C35 (`migration/inventory/rulebook-conflicts.md`) applied by the orchestrator at the 02 gate under the human's standing mandate, instead of by the human (standing rule 1: amendments are applied between batches by the human). Applied by a rulebook agent on the rulebook-tier model; C13/C23 are duplicates (C7/C21), C17 applied as corrected | Human, standing mandate (`PROGRESS.md`) |
