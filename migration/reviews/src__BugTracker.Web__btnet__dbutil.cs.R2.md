# Review R2: src/BugTracker.Web/btnet/dbutil.cs (+ util.cs)

Reviewer: R2 (adversarial, read-only). Targets: `port/web/src/main/java/btnet/DbUtil.java`,
`port/web/src/main/java/btnet/Util.java`. These are core units, so there is no template (§4) and
no judge spec applies directly.

## What I checked

- I compared every member of both sources with the translation. All members are present,
  including the dead ones (`dummy`, `Request`, `s2`, `x`, the duplicate `onmouseup` line).
  Overloads are kept: `execute_nonquery` ×2, `execute_reader` ×2, `format_db_value` ×3,
  `update_user_password` ×2, `how_long_ago` ×2, `redirect` ×2.
- SQL text and verbatim strings: I extracted every `@"…"` literal from `util.cs` (CRLF source)
  and matched it byte for byte against the joined Java literals. Script:
  `/tmp/claude-0/-home-user/9c6707b8-af5b-57fb-b9d4-be71e6af9275/scratchpad/vcheck.py`. All 45
  match, including the tabs, CRLF line ends and trailing spaces (`@userorg `, `[datatype], `).
  The one exception is `<(.|\n)*?>` → `<[^>]*>`, which is the sanctioned C25 rewrite and carries
  the required comment. The plain-string SQL fragments in `get_all_tasks` match too, including
  the double spaces.
- The runtime API calls used exist on disk (§3.1). I checked `SqlCommand.Connection(..)`,
  `CommandText()`, `Parameters()` (`SqlParameterCollection implements Iterable<SqlParameter>`),
  `SqlDataAdapter.SelectCommand()`, `close()` without `throws` on `SqlConnection`, `SqlCommand`
  and `SqlDataAdapter` (so the precise `throw t;` at DbUtil.java:119/137 compiles),
  `Stopwatch.ElapsedMilliseconds()`, `DotNetFormat.Format(Object,String,CultureInfo)`,
  `Decimal.TryParse(String,int,CultureInfo,Ref)`, `Strings.Split(String,int,String,String...)`,
  `DotNetRegex` static `Replace` and instance `IsMatch`/`Split`, `HttpResponse`
  `CacheControl/Expires/ContentType/ContentEncoding/BinaryWrite/Clear/End/Redirect`,
  `HttpRequest.QueryString()/get()/Url()/RequestContext()`, `DataView implements Iterable`,
  `DataRowView.get(int)`, `Rfc2898DeriveBytes(String, byte[])`,
  `Path`/`Directory`/`AppPaths.ConfigurationFile()`, `ApplicationState.get()`, and
  `ClaimsIdentityExtensions.*(IIdentity)`.
- Semantics I checked against the rules:
  - C30 bare catches: `throw t` with `rethrowIfEnd` omitted at DbUtil :116/:134; `rethrowIfEnd`
    first at Util :606.
  - C11 catches → `RuntimeException`. J-C5 `CommandBehavior` → `int`.
  - S1-46/S2-18 concatenation wrapping. S2-3 `StringBuilder.append`. S1-8
    `Strings.ToUpper/ToLower`. C20 `Random` mapping. S1-18 `TimeSpan` parts. S1-15 decimal `G`.
    C32 `ApplicationState.get()`. S1-17 `ConfigurationFile`. N2 `UrlHelper` with its required
    `BUG(port)` marker. `typeof` table (`ClrTypes.UInt16/32/64`). S1-39 local `const` → `final`.
    S2-12 default-private members.
  - `Request.Url.ToString()` → `ToStringStrict(Url())` (rule example).
  - `drv[col].ToString()` → `Convert.ToString` (a DataRowView value cannot be null).
  - `date.ToString()` on an `object` → `ToStringStrict`.
  - Null/DBNull: `param.Value() == null || == DBNull.Value`; `getClass() == DBNull.class`
    (S1-40).
  - Error-recovery rule: `get_datarow` returns `null` unless there is exactly one row.
  - Control flow: the `Response.End`/`Redirect` sites are unchanged, as the END_RESPONSE and
    REDIRECT_END rows require.
- Status trailers: DbUtil says `todos=1` and has 1 `TODO(port)`. Util says `todos=0` and has 0.
  Both counts are accurate.

I found no defect that changes behavior. Every finding below is a rulebook-obedience defect
(markers and citations).

## Findings

F1 | severity LOW | port/web/src/main/java/btnet/DbUtil.java:221 | src/BugTracker.Web/btnet/dbutil.cs:209 | RULEBOOK §3 (missing-runtime-member marker), §3.1 + D-009, §6.3/§6.7 | The marker `// TODO(port): runtime SqlParameterCollection must implement Iterable<SqlParameter> (foreach over cmd.Parameters)` asks for a runtime member that already exists. `port/runtime/.../data/SqlParameterCollection.java:19` declares `public class SqlParameterCollection implements Iterable<SqlParameter>` and has `iterator()` at :123. §3.1/D-009 say the on-disk index is authoritative so that "implementers stop treating existing classes as missing". The spurious open question also inflates `todos=1`, and it does not use the §3 form `TODO(port): runtime <Class.Member>`. | Delete the marker line. Keep `for (SqlParameter param : cmd.Parameters())` and change the trailer to `// PORT STATUS: confidence=high todos=0`.

F2 | severity LOW | port/web/src/main/java/btnet/Util.java:364 | src/BugTracker.Web/btnet/util.cs:327 | RULEBOOK §6.5, §5 | The comment `// inventory: src/BugTracker.Web/btnet/util.cs (RULEBOOK §2A decimal ToString(IFormatProvider), S1-15)` cites the inventory without the required `L<line>`. No inventory row for util.cs L327 exists: the decision came from the rulebook (§2A Strings, decimal row), not from an inventory row. §6.5 allows `// inventory:` only "where a decision came from an inventory row". A site that is missing from the inventory should be flagged in the batch report (§5), not cited as if a row existed. | Replace it with a plain rule comment, for example `// RULEBOOK §2A decimal ToString(IFormatProvider) [S1-15]`, and list L327 as a missing inventory row in the batch report. The code itself, `DotNetFormat.Format(val, "G", get_culture_info())`, is correct.

F3 | severity LOW | port/web/src/main/java/btnet/Util.java:414 | src/BugTracker.Web/btnet/util.cs:377 | RULEBOOK §6.5, §5 | `// inventory: src/BugTracker.Web/btnet/util.cs L377 (...)` cites a row that does not exist. `grep` of `migration/inventory.tsv` finds no util.cs L377 row, so this is a phantom citation of the same kind as F2. | Replace it with `// RULEBOOK §2A decimal ToString(IFormatProvider) [S1-15]` and report L377 as a missing inventory row. The code `DotNetFormat.Format(x, "G", CultureInfo.InvariantCulture)` is correct.

F4 | severity LOW | port/web/src/main/java/btnet/Util.java:262 | src/BugTracker.Web/btnet/util.cs:228-259 | RULEBOOK §6.5 [S1-24] | The 30 per-line inventory rows (L228, L230 … L259) are cited by one non-format comment, `// inventory: src/BugTracker.Web/btnet/util.cs L228-L259 (...)`. §6.5 fixes the citation form as `<file> L<line>`, where `L<line>` is the row's own `L<n>` prefix, so a range is not a valid citation and no individual row can be grepped back to its site. | Put `// inventory: src/BugTracker.Web/btnet/util.cs L<n>` on each `DotNetRegex.Replace` line (L228, L230, L231, …, L259). Keep the separate note about the duplicate `onmouseup` line as a plain comment.

Counts: HIGH 0, MEDIUM 0, LOW 4.

## Rulebook obedience

Verdict: **DEVIATIONS: 4** (F1–F4). I found no behavioral deviation. The implementer followed:

- §2A Database: line-for-line ADO.NET facade; try-with-resources for `using`; `toArray(new SqlParameter[0])`, per the inventory LINQ_TOARRAY rows; redundant `conn.Close()` kept (N3).
- C30, C11, J-C5 and J-C24 (`cmd.Connection(v)`).
- C24 / S2-32 verbatim literals (byte-identical, CRLF), and C25 with its comment.
- S1-46 / S2-17 / S2-18 / S2-3 string concatenation and `append` wrapping. The redundant outer `Strings.cs` at DbUtil.java:220 is harmless.
- S1-8, S1-15, S1-16, S1-17, S1-18, S1-39, S1-40, C20, C32, N2 (with the mandated `BUG(port)` text), S2-12.
- The typeof table, §6.1 header, §6.2 comment copying (`(source TODO)` at Util.java:113), and §6.7 trailers.

The deviations, each with its rule:

1. §3 / §3.1 / D-009 / §6.3: a spurious missing-runtime marker (F1).
2. §6.5: an inventory citation without a row or line (F2).
3. §6.5 / §5: a phantom inventory row L377 (F3).
4. §6.5: a range citation instead of per-row `L<n>` (F4).

Also noted, not counted:

- Util.java:1141 cites `L1117` on the `UrlHelper` line (source :1103). The decision there is the rulebook's N2 row; the L1117 row only mentions the guard in passing. The citation is borderline but not false.
- Util.java:70-71 adds `final` to `public static` regex fields that are not `readonly`. This follows the inventory STATIC_CONST_LIKE rows. Nothing in `src/` assigns them, so behavior is unchanged. See gap G2.

## Rulebook gaps

G1. **Typed `foreach` over runtime ADO.NET collections.** J-C18 requires `for (Object o_ : c) { T x = (T) o_; … }` for a typed `foreach` over a *non-generic* collection, and lists only `ArrayList` and `Hashtable.Keys` as examples. .NET `SqlParameterCollection`, like `DataRowCollection` and `DataColumnCollection`, is non-generic. Yet §2A maps `foreach (DataRow dr in t.Rows)` straight to a typed `for (DataRow dr : t.Rows())`, and the runtime types are `Iterable<T>`. Two implementers could disagree at `dbutil.cs:209`: this one wrote the typed form, and J-C18 read literally asks for the cast form. Proposed rule: "J-C18 applies only to the JDK-mapped non-generic collections (`ArrayList`, `Hashtable`). Over a runtime facade collection that implements `Iterable<T>` (`DataRowCollection`, `DataColumnCollection`, `SqlParameterCollection`, `DataView`), write the typed enhanced `for`." Evidence: `dbutil.cs:209`, `SqlParameterCollection.java:19`, §2A `foreach (DataRow …)` row.

G2. **Adding `final` to a mutable static field that is never reassigned.** §2A maps only `readonly` → `final`. The inventory carries two conflicting rows for each of `util.cs` L39/L40: REGEX_SAME without `final`, STATIC_CONST_LIKE with `final`. The S2-12 example itself writes `static Regex reEmail` → `private static final DotNetRegex reEmail`, which implies `final` is allowed. Proposed rule: "A static field initialized at its declaration and never assigned anywhere in `src/` may be `final`; a field assigned anywhere (e.g. `Util.context`, `util.cs:52`) must not be." Separately, the human should resolve the duplicated L39/L40 inventory rows.

G3. **Citing a rule-decided site that has no inventory row.** §6.5 defines only the inventory-citation form. Implementers improvise rule citations in the `// inventory:` form (F2, F3). Proposed rule: "A decision taken from the rulebook is cited as `// RULEBOOK §<sec> [<amendment id>]`; `// inventory:` is used only for an existing row." Evidence: Util.java:364, :414.
