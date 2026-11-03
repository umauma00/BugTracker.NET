# Rulebook conflicts and gaps found during prompt 02 (queued amendments)

Format: source (who found it) — rule § — site — concrete wrong/missing translation — proposed amendment.
Applied at the 02 gate under the standing mandate (PROGRESS.md), recorded in RULEBOOK §7.

## From classifier DELEGATE
- C1 §2A EF6 `.ToArray()` — `priorities.aspx.cs:28` (`IEnumerable<Priority>`) — `.ToList().toArray(new Priority[0])` yields a Java array, not Iterable → compile error. Amend: `IEnumerable<T>`-typed targets take `.ToList()` (List<T>); arrays only when the C# declared type is `T[]`.
- C2 §1 Async — `admin.aspx.cs:43` `Task.Run(() => search.IndexAll())` is a 4th thread site not in the sanctioned list. Amend: add it (same `new Thread(...).start()` rule).
- C3 (gap) `string.Join` — `BugList.ascx:128`, `Mime.cs:90,96` — no rule. Amend: `string.Join(sep, xs)` → `String.join(sep, xs)` for strings; non-strings via `xs.stream().map(String::valueOf)`; .NET `Join` over `object[]` uses `ToString()` (`null` → "") → `Strings.join` runtime helper.
- C4 (gap) LINQ `All` — `BasePage.cs:30` — not in §2A table. Amend: `All(p)` → `stream().allMatch(p)` (empty → true, same as .NET).
- C5 §3 `SmtpClient.TrustAllCertificates(true)` — `email.cs:242` — static vs instance unspecified. Amend: static, process-wide (matches ServicePointManager callback).
- C6 (gap) `SelectListItem` + object initializers — `Controls/BugList.ascx.cs:67-132` — no runtime class / no rule. Amend: runtime `btnet.runtime.web.SelectListItem` (Text, Value, Selected); object initializer `new T { A = x }` → construct then assign, in declaration order.

## From classifier CONTROL_FLOW
- C7 §2A Exceptions — `install.aspx:32` End inside `try` with `catch (Exception ex)` at :46 — .NET runs the catch body (writes "Thread was being aborted." in red) before re-throwing; port's `ResponseEndException extends Error` skips it. Amend: a `catch (Exception)` whose try contains End/Redirect gets an explicit `catch (ResponseEndException e) { <catch body with ex.Message = "Thread was being aborted.">; throw e; }` before the general catch.
- C8 §2B B5 vs B2 step 8 — `print_bugs2.aspx:81` End inside a `<% %>` render region — output buffered in the region's local StringWriter is discarded on End; .NET flushes it. Amend: render regions write to the page's response buffer (not a local writer), so End flushes what was written.
- C9 (minor) — `loginnt.aspx:191` `StatusCode = 401; End` — NTLM challenge not produced; keep TODO(port) (Windows auth out of scope).

## From classifier STATE/CATCH
- C10 §2A HttpApplicationState/HttpSessionState → ConcurrentHashMap rejects null — `add_customfield.aspx.cs:285` `Application[...] = null`, several `Session[...] = null`. Amend: runtime `set(k, null)` = remove; `get` of absent = null.
- C11 §2A Exception mapping — `bug.cs:391,420` `throw new Exception(..)` / `throw ex` — .NET `Exception` → checked `java.lang.Exception` contradicts "no checked exceptions declared". Amend: .NET `Exception` (thrown) → `btnet.runtime.DotNetException extends RuntimeException`; `catch (Exception e)` → `catch (RuntimeException e)` plus wrapped checked causes via runtime.
- C12 (gap) `ThreadAbortException` — `email.cs:303`, `bug.cs:1285` — no target. Amend: no Java equivalent; catch dropped, with `// ThreadAbortException catch removed (RULEBOOK §2A)`; the guarded body kept.
- C13 = C7 (install.aspx End inside catch).
- C14 (gap) `GetBaseException()`, `Exception.ToString()` (.NET format "Type: msg\n   at ..."), `HttpRuntime.Cache.Add`, failure exceptions of `DateTimes.Parse` / `DataView.Sort`. Amend: runtime `Exceptions.baseOf(e)`, `Exceptions.dotNetToString(e)` (type FQN mapped to .NET name where known + message; stack trace text not compared); `Cache.Add(k,v,...)` → `cache.addIfAbsent(k,v)` (no expiry: NoAbsoluteExpiration in every call site); parse failures throw `FormatException` (runtime class) .
- C15 (inherited quirk, keep) `Cache[username]` shares namespace with `Cache["Application"]` — preserved; noted BUG(port).
- C16 (inherited, keep) `Session["session_cookie"]` never written — preserved.
- Sweep gaps → fixed in gap_sweep.py (aliased state roots, Cache.Add/Insert/Remove, bare catch).

## From classifier REF_OUT/STATIC/THREADING/NULLABLE/PROCESS
- C17 (UNKNOWN → decided under mandate) `POP3Main.cs:306` `System.Timers.Timer` — Amend: `java.util.Timer` with `scheduleAtFixedRate` (non-daemon), allowed as the one sanctioned scheduler in port/service.
- C18 (UNKNOWN → decided) `POP3Main.cs:75`, `btnet_service.cs:36` `Process.GetCurrentProcess().MainModule.FileName` — Amend: runtime `AppPaths.executable()` = system property `btnet.service.home` (default `user.dir`) + `/btnet_service.exe`, so `<exe>.config` and log paths resolve beside it; config file name kept.
- C19 (gap) `Thread.Abort` (`POP3Main.cs:222`) — Amend: `thread.interrupt()` + `TODO(port): Abort semantics` (loop checks `Thread.interrupted()`).
- C20 (gap) no rows for `ProcessStartInfo`, `System.Random`, `DateTime.Ticks`, `TimeSpan.TotalSeconds`, `SortedDictionary`. Amend: ProcessStartInfo → ProcessBuilder (args, working dir, redirect stdout/stderr, UTF-8); `Random` → `java.util.Random` (sequence differs — never compared); `Ticks` → `DateTimes.ticks(dt)` (100 ns since 0001-01-01); `TotalSeconds` → `Duration.toNanos()/1e9`; `SortedDictionary<K,V>` → `TreeMap` with ordinal comparator for strings (`String.compareTo` ≈ .NET ordinal; NOTE .NET default string comparer is culture-sensitive → runtime `Strings.CULTURE_COMPARER` en-US).
- C21 (gap) RegexOptions `IgnorePatternWhitespace`/`CultureInvariant`/`Compiled` — Amend: Compiled/CultureInvariant → no flag; IgnorePatternWhitespace → `Pattern.COMMENTS` only after `DotNetRegex` escapes `#` and whitespace inside character classes (`print_bug.cs:28`).
- C22 §1 threads — Tomcat threads are daemon; .NET `new Thread` is foreground. Amend: request-spawned threads `setDaemon(false)`.

## From classifier FORMAT/REGEX/LINQ
- C23 = C21 (RegexOptions; `#` in class at `print_bug.cs:28`) — decision: drop Compiled/CultureInvariant; IgnorePatternWhitespace handled per C21.
- C24 §2A verbatim strings — Java text blocks strip common indentation → not byte-identical (`edit_user.aspx.cs` SQL with leading tabs). Amend: verbatim `@"..."` → ordinary string literal with `\n`/`\t` escapes (or concatenation), never a text block.
- C25 `util.cs:216` `<(.|\n)*?>` → StackOverflowError risk in java.util.regex on long input. Amend: sanctioned equivalent rewrite `<[^>]*>` (lazy-to-first-`>` ≡ negated class); other rewrites need an inventory row.
- C26 (gaps) `.Month`/`.Day` → `getMonthValue()`/`getDayOfMonth()`; `SecurityException` → runtime `btnet.runtime.SecurityException extends RuntimeException`; `SelectListItem` (=C6); `DotNetRegex.Matches` → returns `List<Match>`; `Match.ToString()` → `match.Value`; `DbSet.Where(...).OrderBy(...)` chain → `Where(sql, params).OrderBy(col)` on the runtime query object; `ConfigurationManager.AppSettings[k]` → `Settings.get(k)` (same source as `Util.get_setting`).
- C27 `version_control.cs:470` .NET `IndexOf(str, -1)` throws ArgumentOutOfRangeException, Java `indexOf` clamps. Amend: `Strings.IndexOf(s, v, start)` runtime helper that throws `ArgumentOutOfRangeException` (runtime class) for start<0 or start>length.
- C28 UNKNOWN `BasePage.cs:30` — nested `Logic` class signature unspecified → decision: `PageAuthorizeAttribute.Logic(String... roles)` with `boolean OnAuthorize(ClaimsPrincipal user)`.
- C29 UNKNOWN `BugSearch.cs:55` — runtime `ElasticClient.Search(String index, String jsonBody)` returns `SearchResponse` with `List<Hit>{ String Id; double Score; Map<String,Object> Source }`; request body fixed by §2A.

## From classifier (18 added sites)
- C30 (gap) bare `catch { }` — Amend: `catch (Throwable t)`; a bare catch that rethrows → `throw t;` (precise rethrow); bare catch must not swallow `ResponseEndException` (rethrow it first, cf. C7).
- C31 = C20 `SortedDictionary` comparer — `tags.cs:108`: keys are tag strings → use `Strings.CULTURE_COMPARER` (en-US, case-insensitive-first like .NET `StringComparer.CurrentCulture`), not ordinal.
- C32 (UNKNOWN → decided) `Util.context` static HttpContext set at `Global.asax.cs:46`; `Util.context.Application[...]` at `MainMenu.ascx:4,15`, `util.cs:918,964` — Amend: `Util.context.Application` → `ApplicationState.get()` (runtime singleton available from startup, independent of any request); `Util.context` itself kept only for `MapPath` via `AppPaths`.
