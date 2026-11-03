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
