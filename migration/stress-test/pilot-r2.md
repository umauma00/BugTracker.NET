# 03 — Pilot run, round 2 (amended rules S1-*)

| Unit | R1 | R2 | Confirmed | Not conf. | Obedience R1/R2 | Fixer |
|---|---|---|---|---|---|---|
| btnet/print_bug.cs | L2 | L1 | 2 | 2 | DEV 2 / DEV 1 | applied 2 |
| btnet/bug_list.cs | L3 | L2 | 3 | 0 | DEV 3 / DEV 1 | applied 3 |
| btnet/version_control.cs | L1 | M1 L1 | 1 | 1 (BOM → rule gap) | DEV 1 / DEV 1 | applied 1 |
| search.aspx | M1 L2 | M1 L1 | 2 | 2 | DEV 2 / DEV 2 | applied 2 |
| edit_user.aspx | L4 | L4 | 4 | 0 | DEV 4 / DEV 2 | applied 4 |

Round-over-round: HIGH 0 → 0; MEDIUM 7 → 3 (one compile error caught by review, one BOM rule gap); confirmed findings 27 → 12; implementer TODOs per unit down (bug_list todos=0). Interruption: account rate limit mid-round; all agents resumed from saved state, no work lost.

- R2 bug_list implementer: HttpContext accessor signatures (Request()/Session()) unlisted in §3; DataColumnCollection.Count(), DataRow.set(int,Object) implied; Server.HtmlEncode → HttpUtility vs HttpServerUtility ambiguity; S1-46 Strings.cs wrapping applied to never-null Convert.ToString results (noise).
- R2 version_control implementer: Strings.PadLeft missing from §3 (12 sites); rule vs inventory L32 (Processes.command vs SplitArgs) — rule followed; StringBuilder.Append(null) not covered (C# appends nothing, Java appends "null") — LEFT BARE (potential replicated bug); Exceptions.wrap overloads unspecified; DotNetRegex.Split return type.
- R2 print_bug implementer: Match.Groups unspecified in §3; Debug.Assert dropped; rule (S1-46) vs inventory L948/L1004 on Strings.cs; private static → package-private per inventory vs rule; StartsWith/IndexOf(string) culture-sensitive in C# but rulebook says ordinal (potential replicated divergence).
- §5 missing-from-inventory sites to flag (print_bug): Debug.Assert at print_bug.cs (dropped), m.Groups[1] at print_bug.cs:995 — flagged here per §5.

## Rulebook gaps — src__BugTracker.Web__btnet__bug_list.cs

G1. **Default (implicit `private`) C# member access** (R1.G1, R2 gap 1). The members at bug_list.cs:19,42,66,284,298,443 have no access modifier, and the translation makes them package-private. The rulebook keeps access modifiers only for `const` (RULEBOOK.md:124). Proposal: a C# member without an access modifier → Java `private`; `protected` → `protected`; `internal` → package-private; a top-level type with no modifier → package-private.

G2. **`DataView` enumeration while rows are being changed** (R1.G3, R2 gap 2). bug_list.cs:647 runs `foreach (DataRowView drv in dv)` and writes `dr[i] = …` at :764, possibly while the view is sorted on `$VOTE` (:259-260/:271). .NET `DataView.GetEnumerator()` iterates a snapshot. Proposal for §3: the iterator of `DataView implements Iterable<DataRowView>` works on a snapshot of the view's rows taken when the iterator is created, so `DataRow.set` during the loop never changes the iteration order.

G3. **`HttpResponse.Write(String null)`** (R1.G2). §2B covers only the `Object` overload. .NET `Write(string null)` writes nothing. The site is BugList.java:450, where `HtmlEncode(Request["tags"])` can be null. Proposal for §3 `HttpResponse`: `Write(String s)` with `s == null` writes nothing and never writes `"null"`.

G4. **The exception type when `Substring` is out of range** (R2 gap 3). .NET throws `ArgumentOutOfRangeException`; Java throws `StringIndexOutOfBoundsException`. This is reachable at bug_list.cs:322, where it is an uncaught 500 on both sides today. Proposal: either a runtime `Strings.Substring(s, a[, n])` with .NET bounds and the .NET exception, or an explicit note that the Java exception is accepted wherever the exception is neither caught by type nor displayed.

G5. **`HttpContext.Current.Server.HtmlEncode` in core statics** (R2 gap 4). For bug_list.cs:813,859, one implementer may write `HttpUtility.HtmlEncode(x)` and another `HttpContext.Current().Server().HtmlEncode(x)`. Proposal for one line in the C34 row: `HttpContext.Current.Server.HtmlEncode/UrlEncode` → `HttpUtility.HtmlEncode/UrlEncode`, with no `Current()` call.

G6. (informational) **Culture-sensitive `IndexOf(string)` treated as ordinal** (R1.G4). §2A already accepts this. The sites that rely on it, all over user-controlled text, are bug_list.cs:313,315,321,531. Recorded so the human can see them. No rule change is proposed.

## Rulebook gaps — src__BugTracker.Web__btnet__print_bug.cs

- **G1 — `Match.Groups[i]` / `Group` members** (R1 gap 2, R2 G1). §3 `DotNetRegex` lists `Match` and `MatchEvaluator` but no `Groups()` collection and no `Group`/`GroupCollection` type. §2A C26 maps only `m.ToString()`. Proposal: `m.Groups[i]` → `m.Groups().get(i)` (index 0 = whole match; an out-of-range or unmatched group is an empty, unsuccessful Group with `Value()` = `""`, as in .NET); `m.Groups["name"]` → `m.Groups().get("name")`. Also state explicitly whether `g.ToString()`/`g.Value` → `g.Value()` (C26 extended) or J-C17 `Strings.ToStringStrict(g)`. Add `Group` and `GroupCollection` to the §3 `btnet.runtime` row. Evidence: print_bug.cs:995, PrintBug.java:1002.
- **G2 — `System.Diagnostics.Debug.Assert` / `[Conditional("DEBUG")]` calls** (R1 gap 1, R2 G2). There is no §2A row and no inventory row. Proposal: omit the call with a plain comment, `// Debug.Assert removed (RULEBOOK §2A)`, no marker and no todo count, and its argument is not evaluated. This needs the human to confirm the deployed configuration: the csproj default is Debug with `DEBUG;TRACE` (BugTracker.Web.csproj:6,28-33), but under w3wp the DefaultTraceListener is non-interactive. Evidence: print_bug.cs:575. Grep `src/` for other `Debug.` sites.
- **G3 — C# implicit-private member access** (R1 gap 3, R2 G3). The rulebook keeps access modifiers only for `const` (§2A). Implicit-private static members became Java package-private. Proposal: a member with no modifier → Java `private`; explicit modifiers are kept; `internal` → package-private. Evidence: print_bug.cs:19,28,414,764,814,886,898,927,983,990,1002,1008.
- **G4 — `Substring` out-of-range exception type** (R2 G4). `s.substring(a, a+n)` throws `StringIndexOutOfBoundsException`, where .NET throws `ArgumentOutOfRangeException`. The two are equivalent only where no typed catch exists. Proposal: accept the Java exception at sites with no typed catch, or route through a `Strings.Substring` helper. Evidence: print_bug.cs:907-908.

## Rulebook gaps — src__BugTracker.Web__btnet__version_control.cs

G1 (MEDIUM, reachable) — Process stream BOM handling [R1.G1, R2.G1, R2.F2]. .NET `Process` reads redirected streams via `StreamReader(..., detectEncodingFromByteOrderMarks: true)`, consuming a leading UTF-8 BOM (UTF-16/32 BOMs switch the decoder); the port's `new String(readAllBytes(), UTF_8)` keeps U+FEFF. Reachable through `git show`/`hg cat`/`svn cat` of BOM-prefixed files → *_view pages and `visual_diff` line 1. Proposed: §3 runtime `Processes.ReadToEnd(InputStream, Encoding)` (or `readAll`) with .NET StreamReader BOM semantics, used for both pipes; amend §2A Processes row. Evidence: version_control.cs:27,43,47,129,133.

G2 (LOW) — stderr encoding [R1.G2, R2.G2]. Source sets only `StandardOutputEncoding` (:27); .NET Framework decodes stderr with `Console.OutputEncoding` (OEM code page, e.g. 437), not UTF-8. Non-ASCII stderr reaches the error `<div>` (:60, :145). Human decision: runtime `Encoding.OEM` for stderr, or record a divergence in D-006.

G3 (LOW) — `String.PadLeft(int, char)` missing from §3 [R1.G3]. 12 `TODO(port): runtime Strings.PadLeft` sites, all in this file. Proposed: `Strings.PadLeft(String s, int totalWidth, char paddingChar)` (`totalWidth < 0` → `ArgumentOutOfRangeException`; unchanged if already long enough; else left-pad in UTF-16 units).

G4 (LOW, latent) — `StringBuilder.Append(null)` [R1.G4, R2.G3]. .NET appends nothing, Java appends `"null"`; S1-46 covers `+` only. Unreachable here (svn `repo` from `(string) dr[...]`), but the pattern replicates (target :932, :949, :965, :986, :994). Proposed row: `sb.Append(x)` with non-literal reference `x` → `sb.append(Strings.cs(x))` (or runtime `Strings.append`).

G5 (LOW) — Left operand of `+=` under S1-46 [R1.G5, R2.G4]. Unstated whether `s += e` wraps `s`. Example `args_without_password += " --non-interactive"` (:100, target :123). Proposed: `x += e` on a `String` parameter/field/element → `x = Strings.cs(x) + <wrapped e>`; locals initialised from a literal exempt.

G6 (LOW) — Stale inventory rows L32/L94 vs §2A Processes [R1.G7, R2.G5]. inventory.tsv:576, :579 prescribe `command.add(filename); command.addAll(Processes.SplitArgs(args))`; §2A prescribes `Processes.command(FileName, Arguments)`. Rule wins (§5), implementer correct; rewrite both rows to the rule form.

G7 (LOW) — Process start failure exception [R1.G6]. Missing executable / bad working dir throws `Win32Exception` with .NET text in the source; the port throws wrapped `IOException` ("Cannot run program …"). Reachable on unconfigured installs (defaults `[path to git.exe?]`, `[path to hg.exe?]`, :77, :84). Proposed: runtime `Win32Exception` + `Processes.start(ProcessBuilder)` mapping.

G8 (LOW, latent) — Out-of-range exception types [R1.G8, R2.G8]. `StringIndexOutOfBoundsException` / `IndexOutOfBoundsException` vs .NET `ArgumentOutOfRangeException` (and array → `IndexOutOfRangeException`); `Exceptions.Message` maps only the array case. Reachable only on malformed diffs (:282-284, :471, :489, :592, :609, :639); uncaught either way. Proposed: extend the S1-10 map.

G9 (LOW) — BUG(port) deadlock-threshold text [R2.G6]. Inventory-dictated marker says ">64 KB of stderr deadlocks, exactly as in .NET"; 64 KB is the Linux pipe buffer, Windows anonymous-pipe default is smaller (unverified). Proposed: reword inventory marker to an OS-dependent threshold.

G10 (LOW) — Implicit C# `private` members [R2.G7]. Rulebook does not say whether default-private members become Java `private` or package-private; inventory L175 uses package-private. Proposed: an explicit rule.

## Rulebook gaps — src__BugTracker.Web__edit_user.aspx

- G1 — `HtmlTableCell`/`HtmlTableRow`/`HtmlTable` designer types are missing from §3, and B1's tag table covers inline-script pages only. That leaves two valid readings: substitute `HtmlGenericControl`, or write the class with a TODO. The decision affects 81 designer fields across 15 files. Proposed: add the classes to §3 (with the same members as HtmlGenericControl: InnerText, InnerHtml, Visible, Attributes), or add to B1 "designer HtmlTableCell/Row/Table → HtmlGenericControl, no marker". (R1.G1, R2.G1)
- G2 — `Random.Next()` with no arguments, and `Next(n)`. Proposed: `r.Next()` → `r.nextInt(Integer.MAX_VALUE)`; `r.Next(max)` → `r.nextInt(max)`. Evidence: edit_user.aspx.cs:588, edit_user.java:632. (R1.G2, R2.G5)
- G3 — Declarative ListBox/DataGrid members are unspecified in §3: `ListSelectionMode`/`SelectionMode` (and whether `bt:control` renders `multiple`); `DataGrid.BorderColor/CssClass/CellPadding/AutoGenerateColumns/HeaderStyle()/ItemStyle()/Columns()`; the `BoundColumn(headerText, dataField)` constructor; `.Visible(false)`. The runtime author should fix these signatures before fan-out, because 3 files use DataGrid. Evidence: edit_user.aspx:256,376-384,417. (R1.G3 part, R1.G4, R2.G2)
- G4 — Layout-only declarative attributes that code does not read (`Rows="4"` → literal `size="4"` vs `.Rows(n)`; CssClass/BorderColor in the initializer). If the runtime also renders them, the element can get duplicate attributes. Proposed: "non-code-read properties of asp:* controls → fluent initializer setters; the template never repeats them". (R1.G3 part, R2.G3)
- G5 — The §2A C24 example quotes edit_user.aspx.cs:35 with four tabs; the source has three (`^I^I^Ifrom`). Correct the example. (R1.G5)
- G6 — The Hashtable iteration order sets the `in (…)` list order in generated SQL, which conflicts with §2A byte-identity. Proposed exemption: an order-insensitive SQL `in (…)` list built from Hashtable iteration needs a plain comment and no marker. (R1.G6)
- G7 — A DropDownList can end up with several selected items (:71-73 set `Items[4].Selected` after post data loads). Specify in the runtime that `SelectedItem()` returns the lowest-index selected item, and that rendering with more than one selected throws `HttpException("Cannot have multiple items selected in a DropDownList.")`. No translator action is needed. (R1.G7)
- G8 — `class` on `asp:CheckBox`: ASP.NET renders it on a wrapping `<span>`, while this unit puts it on the `<input>`. Decide one form in B5. Evidence: edit_user.aspx:127,134,148,155,229,247,264,271,278,336. (R2.G4)
- G9 — Malformed source markup (unclosed `<span>` at :215; open `<td>`s at :348-357, :371-420, :439-444) is auto-balanced by the Thymeleaf parser. State that auto-balanced output is accepted and not judged, so pages don't each repeat the TODO. (R2.G6)
- G10 — BUG markers on code that `PageAuthorize` makes unreachable (non-admin branches :32-53, :83-105, :148-163, :230-235, :273-285, :521-524). Proposed: "mark them; the repro states the blocking guard". (R2.G7)

## Rulebook gaps — src__BugTracker.Web__search.aspx

G1. **ListBox `Rows=` / `SelectionMode=`** (R1.G1, R2.G1). §3 lists no `ListBox` members and has no `ListSelectionMode` enum. B5 has no ListBox row for how `size`/`multiple` render. Pilots disagree: this unit puts both in Java, while edit_user.java:65-66,78 puts `Rows` in the template as `size="4"`. Proposed rule: `.Rows(n)` and `.SelectionMode(ListSelectionMode.Multiple)` in the field initializer, rendered by `bt:control` as `size="n"` and `multiple="multiple"`, and never repeated in the template. Add `ListSelectionMode { Single, Multiple }` to `btnet.runtime.web.ui`.

G2. **Unbalanced or implicitly closed markup, and `th:if` elements whose end tag is outside the region** (R1.G2, R2.G2). The cases are search.aspx:265-279 (one `</td>` closes either cell across `<% if (show_udf) %>`), the label cells left open at :265/283/291/299/311, and `<tr><td><tr>` at :202-206. B5 "copied unchanged" conflicts with `th:if` needing a whole element, and AttoParser's auto-close behaviour has not been pinned down. Proposed rule: a `__condN` element gets its own explicit end tag inside it, plus the end tag that the trailing source tag closes when the condition is false. This is the only sanctioned rewrite. Comment it `<!-- DOM-equivalent restructure for th:if (RULEBOOK B5) -->`. Optionally allow implied end tags elsewhere under one documented convention, and have the runtime author pin AttoParser's behaviour on `<tr><td><tr>`.

G3. **Inventory rows for nested regions (S1-36)** (R1.G3). The inventory has no rows for search.aspx L195 (nested in region 7) or L273 (nested in `__cond9`, now `__render9_1`). Proposed rule: regions nested in a `__renderN` need no row (J-U2a decides them); regions nested in `__condN`/`__itemsN` do. Add the L273 row.

G4. **Numbering of regions nested in a brace-balanced span inside `<script>`** (R2.G3). The inventory lists L129-L135 and L138 separately. J-U3/S1-31 do not say whether they consume top-level numbers. Proposed rule: "A `<% … { %> … <% } %>` span inside `<script>`/`<style>` content is one region for numbering; regions nested in it consume no top-level number."

G5. **Control IDs that are SpEL operator words** (R1.G4). `${page.and}`/`${page.or}` here are unverified. Words like `div eq ge gt le lt mod ne not` would break the expression. Proposed rule: bind such IDs as `${page['<ID>']}`, and check `${page.and}` at Step 5.

G6. **Declared form `action` vs `th:action`** (R1.G5, R2.G6). B5 says both. Proposed rule: a declared `action` replaces `th:action` (as ASP.NET 4.5 `HtmlForm` does); otherwise use `th:action="${page.FormAction()}"`.

G7. **Template `Source` header in content-page templates** (R1.G6, R2.G5). §6.1 says "after the doctype", but a content template has none. Proposed rule: the first node inside the first `th:ref` block.

G8. **Designer-file comments** (R2.G4). §6.2 "Source comments are copied as comments" vs the `<auto-generated>` and `<summary>` boilerplate in `*.designer.cs`. Proposed rule: designer auto-generated comments are not copied.

G9. **`BUG(port)` for latent, unobservable defects** (adjudicator, from R1.F3 vs R2). The BUG rule does not say whether a defect in dead code (a write-only field) gets a ledger marker. Proposed rule: mark it only when the defect reaches output, SQL or state that is read; otherwise use a plain `// note:` comment.

---
Confirmed: 2. Not confirmed: 2.

