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
