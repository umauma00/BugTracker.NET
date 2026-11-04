# Review R1: src/BugTracker.Web/btnet/dbutil.cs (+ util.cs)

Reviewer: R1 (adversarial, read-only). Targets:
`port/web/src/main/java/btnet/DbUtil.java` (249 lines),
`port/web/src/main/java/btnet/Util.java` (1412 lines). No templates: these are core units (§0), so RULEBOOK §4 places no template, and the judge contract does not apply because neither unit is a page or control.

## What I checked

- I read the RULEBOOK in full: §0, §0.1, §1, §2A, §3 (including §3.1), §4, §5, §6 and §7. I read §2B only where core code touches it (B6, the startup pseudo-context).
- I checked every inventory row for both sources: `dbutil.cs` L28/L41/L60/L75/L82/L100/L104/L122/L148/L165 and the 56 `util.cs` rows L32…L1367. Each row's decision appears in the target.
- Member completeness: 51 method/constructor signatures in `util.cs` and 51 in `Util.java`, with the same names in the same order (diffed). All 10 `DbUtil` members are present, and so are all fields (`context`, `Request`, `dummy`, `reCommas`, `rePipes`, `reEmail`, `_random`).
- SQL and message byte-identity: I wrote a script that extracts every C# literal, verbatim (`@"…"`, with CRLF line ends, since both files are CRLF) or regular, and decodes every Java literal chain joined by `+`. Every C# literal value occurs exactly as a Java literal chain. The one exception is `<(.|\n)*?>` (`util.cs:216`), the sanctioned C25 rewrite, which carries its required comment (`Util.java:249`). So all SQL (`util.cs:392-418,750-864,895-897,926-962,1156,1243-1333`, `dbutil.cs`) is byte-identical, tabs and `\r\n` included.
- I checked every runtime call against `port/runtime` for existence and signature. These all resolve:
  - `HttpResponse.CacheControl/Expires/ContentType/ContentEncoding/BinaryWrite/Clear/AddHeader/Write/End/Redirect`
  - `HttpRequest.QueryString/RequestContext/Url/get`
  - `UrlHelper(RequestContext)`, `IsLocalUrl`
  - `Decimal.TryParse(String,int,CultureInfo,Ref)`, `Decimal.Parse(String,CultureInfo)`
  - `DateTimes.Parse(String,CultureInfo)`
  - `Strings.Split(String,int,String,String...)`
  - `DotNetRegex` static `Replace(…,int)` and instance `Split`/`IsMatch`
  - `Rfc2898DeriveBytes(String,byte[])`
  - `Encoding.UTF8.GetBytes/GetString/GetPreamble`
  - `Path.*`, `Directory.Exists`, `AppPaths.ConfigurationFile`
  - `ClaimsIdentityExtensions.*(IIdentity)`
  - `CommandBehavior` (int constants), `SqlCommand.Connection(SqlConnection)`, `SqlDataAdapter.SelectCommand()`
  - `Stopwatch.ElapsedMilliseconds()` (long; `DotNetFormat.Format` accepts Long)
  - `SQLString.Append(String|SQLString)`, `AddParameterWithValue(String,int)`

  Every `close()` used in try-with-resources is declared without `throws`, so the precise rethrow `throw t;` in both `execute_reader` overloads compiles.
- Behavior: these all follow §2A as written:
  - string `==` becomes `Objects.equals`
  - concatenation operands are wrapped with `Strings.cs` (S1-46/S2-18)
  - `StringBuilder.Append` follows S2-3
  - catch mapping follows C11/C30/J-C8, including `rethrowIfEnd` in `sanitize_integer`, omitted where the body ends in `throw;`
  - `TimeSpan` becomes `Duration` with `*Part()` components (S1-18)
  - `Random.Next(a,b)` becomes `a + nextInt(b-a)` (C20)
  - `typeof` uses the table, including the `ClrTypes.UInt*` tokens
  - `Util.context.Application` goes through `ApplicationState.get()` (C32)
  - `IsLocalUrl` and the required BUG marker follow N2
  - `GetAbsolutePath` follows S1-17
  - `ToUpper/ToLower/Trim` go through `Strings`
  - decimal `ToString(IFormatProvider)` becomes `DotNetFormat.Format(d,"G",p)` (S1-15)
  - `Response.End/Redirect` are unchanged
  - null and DBNull handling matches, `get_datarow` keeps its null return (Error-recovery rule), and the `CommandBehavior` parameter is `int` (J-C5)

  The BUG(port) markers on the inherited quirks (`validate_email` `$`, the dead `else if` in `is_valid_decimal`, `onSAFEresetK`, PRNG and loop bound, never picking `'0'`, the relative returnUrl) reproduce the behavior and do not fix it.

## Findings

F1 | severity LOW | port/web/src/main/java/btnet/DbUtil.java:221,249 | src/BugTracker.Web/btnet/dbutil.cs:209 | RULEBOOK §3 intro ("A translator who needs a **missing** runtime member … marks `TODO(port): runtime <Class.Member>`"), §3.1 [D-009], §6.7 | The marker `// TODO(port): runtime SqlParameterCollection must implement Iterable<SqlParameter>` asks for something the runtime already has. `port/runtime/src/main/java/btnet/runtime/data/SqlParameterCollection.java:19` declares `public class SqlParameterCollection implements Iterable<SqlParameter>`, and `:123` has `iterator()`. The marker is false and greppable, so it will be batched to the runtime author as phantom work, and it inflates the trailer to `todos=1`. | Delete the marker line and set the trailer to `// PORT STATUS: confidence=high todos=0`.

F2 | severity LOW | port/web/src/main/java/btnet/DbUtil.java:222 | src/BugTracker.Web/btnet/dbutil.cs:209 (`foreach (SqlParameter param in cmd.Parameters)`) | RULEBOOK §2A General conventions, `foreach (T x in c)` over a non-generic collection [J-C18] | .NET `SqlParameterCollection` is non-generic (`DbParameterCollection : IList`), so the C# `foreach` with a typed variable does an implicit cast. J-C18 gives the canonical form for exactly this. The implementer wrote the typed enhanced-for `for (SqlParameter param : cmd.Parameters())` instead. The result is the same (the runtime collection is `Iterable<SqlParameter>`, and `Add` only accepts `SqlParameter`), but it ignores the rule as written. See also Rulebook gap G1. | Under the rule as written: `for (Object o_ : cmd.Parameters()) { SqlParameter param = (SqlParameter) o_; … }`. If G1 is adopted, keep the current line and remove F1's marker.

F3 | severity LOW | port/web/src/main/java/btnet/Util.java:364 | src/BugTracker.Web/btnet/util.cs:327 | RULEBOOK §6.5 (`// inventory: <file> L<line>`, "where a decision came from an inventory row") | The comment `// inventory: src/BugTracker.Web/btnet/util.cs (RULEBOOK §2A decimal ToString(IFormatProvider), S1-15)` has no `L<line>`. No inventory row exists for `util.cs` L327: the inventory has 56 `util.cs` rows and none is L327. The decision came from the rulebook (§2A Strings, S1-15), not from the inventory, so this inventory citation is wrong. The translated code itself is correct. | Replace it with a rule citation, for example `// RULEBOOK §2A decimal ToString(IFormatProvider) [S1-15]`, or drop the comment. Do not use `// inventory:`.

F4 | severity LOW | port/web/src/main/java/btnet/Util.java:414 | src/BugTracker.Web/btnet/util.cs:377 | RULEBOOK §6.5 | The comment `// inventory: src/BugTracker.Web/btnet/util.cs L377 (…S1-15)` cites an inventory row that does not exist: there is no L377 row for `util.cs` in `migration/inventory.tsv`. A reader grepping the inventory for the cited key finds nothing. The code (`DotNetFormat.Format(x, "G", CultureInfo.InvariantCulture)`) is correct under S1-15. | Use `// RULEBOOK §2A decimal ToString(IFormatProvider) [S1-15]` in place of the fake inventory citation.

F5 | severity LOW | port/web/src/main/java/btnet/Util.java:262 | src/BugTracker.Web/btnet/util.cs:228-259 | RULEBOOK §6.5 (format `// inventory: <file> L<line>`, `L<line>` = the `L<n>` prefix of the row's `source_construct`) | One comment covers 26 inventory rows with a range, `// inventory: src/BugTracker.Web/btnet/util.cs L228-L259 (…)`. No row has the key `L228-L259`, so the §6.5 grep contract (comment key → row) is broken for 25 of the 26 rows (L230…L259). | Put a per-row comment on each `DotNetRegex.Replace` line (`// inventory: src/BugTracker.Web/btnet/util.cs L230`, …, `L259`), keeping `L228` on the first one. Alternatively, the human sanctions range citations (G2).

No HIGH or MEDIUM defects. All five findings are markers or citations that the rules decide. I found no difference in SQL, output text, redirect/End flow, exception type or message, null/DBNull handling, formatting, ordering or thread-safety.

## Rulebook obedience

Verdict: **DEVIATIONS: 5**. The behavior is faithful in every member checked. The deviations are all marker or citation level:

1. §3 intro, §3.1 [D-009], §6.7: a false `TODO(port): runtime …` for a member that exists, and the trailer count that goes with it (F1).
2. §2A [J-C18]: a typed enhanced-for over the non-generic .NET `SqlParameterCollection` instead of the `Object o_` cast form (F2).
3. §6.5: an inventory citation with no `L<line>`, for a decision that came from S1-15 and not from the inventory (F3).
4. §6.5: an inventory citation of a row that does not exist (`L377`) (F4).
5. §6.5: one range citation for 26 rows instead of one citation per row (F5).

Checked and obeyed:

- §6.1 header, in both files.
- §6.2:
  - Completeness, including dead code: `dummy`, the commented-out fields, `Request`, and the duplicate `onmouseup` line.
  - Source comments are copied, and the source TODO becomes `(source TODO)` (`Util.java:113`).
- §6.3 marker formats.
- §6.7 trailer, except the F1 count. In `Util.java` the 0 TODOs matches `todos=0`.
- §0.1: no unsanctioned fixes. `onSAFEresetK`, the dead `else if`, the PRNG and the IsLocalUrl rejection are all reproduced and marked.
- §2A [C24] verbatim literals. Text blocks are not used, and the CRLF bytes are copied.
- §2A [C25]: the only pattern rewrite carries the required comment.
- §2A [S1-39]: the method-local `const` becomes `final String`.
- §2A [S2-12] access modifiers, including `private` on `dummy`/`reEmail`.
- §2A [C11/C30/J-C8] exceptions.
- §2A [C32] `Util.context`.
- §2A [J-C5] flags enums.
- §4 imports: no wildcards, no static imports, and no runtime/app simple-name clash.

## Rulebook gaps

- **G1: J-C18 vs runtime facade collections that are `Iterable<T>`.** J-C18 names only `ArrayList`/`Hashtable.Keys`, but its trigger condition ("non-generic collection" in .NET) also matches runtime facades whose .NET originals are non-generic: `SqlParameterCollection` (`dbutil.cs:209`), `DataColumnCollection` (§2A S1-19 already prescribes `for (DataColumn c : dt.Columns())`), and `DataRowCollection` (`for (DataRow dr : t.Rows())`). The rulebook therefore contradicts itself in spirit: S1-19 and the `foreach DataRow` row use the typed form for non-generic .NET collections. *Proposed rule:* "J-C18 applies to JDK raw collections (`ArrayList<Object>`, `HashMap<Object,Object>` keys/values). A runtime facade collection that implements `Iterable<T>` (§3) is iterated with the typed enhanced-for `for (T x : c)`." Evidence: `SqlParameterCollection.java:19`, RULEBOOK §2A `foreach (DataRow dr in t.Rows)` row and S1-19 row.
- **G2: §6.5 citation form when one decision spans many consecutive rows, or comes from a rule rather than a row.** Two implementers could differ here: one comment with a range, or one comment per row; and `// inventory:` versus a rule citation when the decision is a rulebook rule (F3, F4). *Proposed rule:* "`// inventory:` names exactly one existing row key. A decision taken from a rulebook rule is cited as `// RULEBOOK §<sec> [<id>]`. A run of N rows with the same decision gets one citation per row (no ranges)."
- **G3: how to translate the body of `Util.get_culture_info()` itself** (`util.cs:266-270`, `new CultureInfo(Thread.CurrentThread.CurrentCulture.Name)`). §2A says *calls* of `Util.get_culture_info()` map to `CultureInfo.EN_US`, but the rulebook says nothing about the method body. The implementer returned the shared singleton `CultureInfo.EN_US`, which is reasonable. .NET returns a fresh, mutable, non-read-only instance on every call, though. That only matters if a caller mutates the returned culture, and none does in `util.cs`; I did not survey other units. *Proposed rule:* "`get_culture_info()` body → `return CultureInfo.EN_US;` (runtime instance treated as read-only; no source site mutates it)", so the bakeoff cannot diverge.
