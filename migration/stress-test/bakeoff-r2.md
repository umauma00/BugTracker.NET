# Bakeoff round 2: diff inspection (rulebook stress test)

Date: 2026-11-04. Inspector: diff inspector (read-only; this file is the only output).

| Input | Path |
|---|---|
| Sources | `src/BugTracker.Web/btnet/print_bug.cs`, `bug_list.cs`, `version_control.cs` |
| A (rulebook to the letter) | `st-A2/port/web/src/main/java/btnet/PrintBug.java`, `BugList.java`, `VersionControl.java` |
| B (no rulebook, idiomatic Spring) | `st-B2/out/PrintBug.java`, `BugList.java`, `VersionControl.java` |
| Rules | `migration/RULEBOOK.md` (with round-1 tags `[S1-n]`), `migration/inventory.tsv`, `migration/stress-test/bakeoff-r1.md` |

`st-A2`/`st-B2` are under `/tmp/claude-0/-home-user/9c6707b8-af5b-57fb-b9d4-be71e6af9275/scratchpad/`.

**Method.**
- Formatting, brace style and naming style (B's camelCase) were ignored by hand; no Java formatter is available.
- Posture-only differences are listed separately and not counted.
- Every row is judged against what the C# does. Java facts were checked with `jshell` (JDK 21.0.12). .NET facts come from the .NET Framework 4.5 reference source and documented behavior. The original cannot run (D-001), so the .NET side of row L3 and the stderr part of V1 are marked for verification.
- A followed the rules: its 4 `RULEBOOK-SILENT` flags are real gaps, every inventory citation uses the §6.5 form, and its `todos=` trailers match its markers (PrintBug 2, BugList 0, VersionControl 12).

## Summary

| Verdict | Rows |
|---|---|
| rulebook right | 13 |
| native right | 1 (V2) |
| both defensible | 12 (6 of them expose a rulebook gap that must be pinned) |
| **both wrong** | **3** (L3, V1, V7) |
| **Total substantive rows** | **29** (+ 9 posture-only items, not counted) |

> **STOP CONDITION: human decision required.** At section level, sections amended in round 1 are indicted again: §3 and §2A General conventions (L3), §2A Strings (V7), and the §2A Logging/processes table (V1, V2).
> - At rule level, none of the round-1 `[S1-n]` rules produced a defect. Every exercised S1 rule held (see the delta).
> - The new both-wrong rows come from rules that round 1 did not touch: `Strings.CULTURE_COMPARER` [C20, C31] and the Process row [C20, J-C13]. The third comes from a construct no rule covers (`StringBuilder.Append`).
> - The human decides whether rule-level convergence is enough to apply this queue and continue. The alternative is to stop the stress test.

**Headline findings.**
1. **`Strings.CULTURE_COMPARER` is not .NET's culture comparer (L3, both wrong).**
   - The rule pins it to `java.text.Collator(Locale.US)`, and B independently chose `Collator(ROOT)`. That Collator treats the space as ignorable: jshell sorts `[inactive, info, in progress]` and `[Newark, New Jersey, Newton, New York]`.
   - Windows NLS (`StringComparer.CurrentCulture`, en-US) sorts the space as a symbol before letters, so "in progress" comes before "inactive" and "New York" before "Newark".
   - This reaches every `SortedDictionary<string,…>` (5 sites) and every string `OrderBy` the rulebook routes to the comparer.
2. **Process output decoding is not .NET's (V1, both wrong, edge).**
   - .NET reads stdout through a `StreamReader` with BOM detection. A leading UTF-8 BOM is stripped, and a UTF-16/32 BOM switches the decoding.
   - Both ports decode the raw bytes as UTF-8 and keep U+FEFF. `git show <commit>:<file>` of any Visual Studio–saved `.cs` file (UTF-8 BOM) differs.
   - B also closes the child's stdin. A leaves it as an open pipe that is never closed, so a child that reads stdin hangs, where .NET's child sees EOF (V2, native right).
3. **Null handling of `StringBuilder.Append` and `Response.Write(String)` is unpinned (V7, L7).**
   - C# `Append((string)null)` and `Response.Write((string)null)` write nothing.
   - V7: A's raw `sb.append(repo)` and B's concatenation write `"null"`. This is latent here because `svnrev_repository` is NOT NULL.
   - L7: `display_buglist_tags_line` writes `HttpUtility.HtmlEncode(Request["tags"])`, which is null on every `bugs.aspx` GET without tags. A's output depends on an unpinned runtime overload.
4. **DataView enumeration semantics are unpinned (L5).**
   - .NET `DataView.GetEnumerator()` iterates a snapshot (it copies the rows to an array).
   - `bug_list.cs:764` rewrites the `$VOTE` value inside that loop. With the list sorted by votes and the vote cache disagreeing with `votes_view`, a live runtime iterator would skip or repeat rows.

## Difference table

`A:` / `B:` are lines of the respective Java file; `cs:` is the C# source.

### print_bug.cs → PrintBug

| # | Area | A | B | Rule / gap | Verdict and evidence |
|---|---|---|---|---|---|
| R01 | HtmlEncode (all 3 files) | A:126 `HttpUtility.HtmlEncode((String) dr.get("short_desc"))` (also BugList A:835, VC A:446) | B:91 `HtmlUtils.htmlEscape(s, "UTF-8")` (also BugList B:84, VC B:389) | §2A Web intrinsics `HtmlEncode` row | **rulebook right.** .NET 4.x `WebUtility.HtmlEncode` writes U+00A0–U+00FF as `&#NNN;` (`é` → `&#233;`); B leaves them raw. `Web.config:43,46` `<httpRuntime>` has no `targetFramework`, so the 4.0 conformance applies and astral characters are *not* encoded; the rule row is complete. In `visual_diff` the changed-character split (cs:639-642) runs on the encoded text, so the part boundaries differ too. |
| R02 | UrlEncode | A:901 `HttpUtility.UrlEncode(HttpUtility.HtmlDecode(email))` | B:687 `URLEncoder.encode(HtmlUtils.htmlUnescape(email), UTF_8)` | §2A `UrlEncode` row | **rulebook right (minor).** For `"Bob S" <b!(x)*@y.com>`, B gives `%22Bob+S%22+%3Cb%21%28x%29*%40y.com%3E` (jshell). .NET gives `%22Bob+S%22+%3cb!(x)*%40y.com%3e`: lower-case hex, and `!()` kept (cs:891). |
| R03 | history-line filter | A:351 `comment.startsWith("changed tags from")` | B:350 same (with the comment "C# StartsWith(string) was culture-sensitive") | §2A `IndexOf/StartsWith` row [S1-21] (accepted) | **both defensible.** The prefixes are machine-generated ASCII history text; only the UDF prefix (cs:370) is configurable. |
| R04 | `format_email_from` | A:913 `from.indexOf("<")` | B:695 `from.indexOf('<')` | same row | **both defensible.** cs:903; `<` is not ignorable. |
| R05 | `Debug.Assert(false)` | A:584 dropped, `RULEBOOK-SILENT` + TODO | B:475 dropped | gap (F1 below) | **both defensible.** `[Conditional("DEBUG")]`; the Release build defines `TRACE` only (`BugTracker.Web.csproj:42`). In a Debug build the w3wp `DefaultTraceListener` shows no UI. |
| R06 | `m.Groups[1]` | A:1006 `m.Groups().get(1).Value()` (SILENT) | B:743 `m.group(1)` | gap (F2) | **both defensible for the default `BugLinkMarker` `bugid#`.** `BugLinkMarker` is a configurable regex fragment (cs:973-974). If it contains a group, .NET numbers unnamed groups first and then named ones. jshell gives `(?<n>bug)#([0-9]+)` group 1 = `bug`, where .NET `Groups[1]` is the digits. A non-participating group is `""` in .NET but `null` in Java, which B concatenates as `"null"`. |
| R07 | e-mail CSS `Response.WriteFile` | A:62,68,73 `Response.WriteFile(css_for_email_file)` (runtime, encoding unpinned) | B:266 `new String(Files.readAllBytes(file), UTF_8)` | gap: §2B/§3 `HttpResponse` [S1-21] silent on `WriteFile` | **both defensible (latent).** `include_style` is used only with `new HttpResponse(StringWriter)` (bug.cs:1158-1173). There .NET's `WriteFile` → `WriteStreamAsText` decodes with `Encoding.Default` (windows-1252). The shipped `btnet_base.css` and `custom/btnet_custom.css` are pure ASCII without a BOM (checked), so the output is identical today. A non-ASCII CSS would differ in B and possibly in A (amendment 7). |
| R08 | CSS error log | A:80-83 `Util.write_to_log(… + Strings.cs(Exceptions.Message(e)))` | B:261 `log.error("…\"{}\":{}", file, e.getMessage())` | §2A Exceptions [S1-10] | **rulebook right (minor).** cs:74-77 writes to the btnet log with the .NET message ("Could not find file '…'."). B prints the `NoSuchFileException` text (the path only) to SLF4J. |
| R09 | `CommentSortOrder` | A:1083 `sql.Append(Util.get_setting("CommentSortOrder", "desc"))` | B:798-802 whitelist `asc`/`desc`, else `desc` | §0 (no fixes) | **rulebook right.** cs:1070 concatenates the setting verbatim. B's whitelist is an unsanctioned fix that changes any other configured value. |
| R10 | `write_tasks` identity | A:248 `HttpContext.Current().User().Identity()` | B:273 caller's `identity` | §2A Web intrinsics | **both defensible.** For notifications the caller passes the *recipient* identity (bug.cs:1164), not the request user. However, `get_all_tasks` ignores `identity` when `bugid != 0` (util.cs:1323-1330), and `print_bug` always runs on a request thread. |
| R11 | `(int)` unboxing | A:337,343,548 `(int) dr.get("bp_hidden_from_external_users")` | B:83-87 `num()`: any `Number`, `Boolean` → 1/0 | §2A `(int)o` row | **both defensible.** These columns are `int` (setup.sql:264,324; datediff/isnull), so neither port throws. |

### bug_list.cs → BugList

| # | Area | A | B | Rule / gap | Verdict and evidence |
|---|---|---|---|---|---|
| L1 | grid cell text | A:815,881 `Convert.ToString(dr.get(i))` (G15) | B:71-76 `str(o)` → `o.toString()` | §2A Convert, Double G15 [S1-6] | **rulebook right.** For a `float` column, cs:794,859 prints `5`/`0.333333333333333`; B prints `5.0`/`0.3333333333333333`. S1-6 held. |
| L2 | numeric alignment | A:802-804 `dv.Table().Columns().get(i).DataType()` → `Util.is_numeric_datatype` | B:542-544 `util.isNumericDatatype(column.type())` (JDBC type) | §2A Database tinyint → `ClrTypes.Byte` [S1-7] | **rulebook right.** As round-1 D08: a tinyint column is not numeric in .NET (cs:784). S1-7 held. |
| L3 | filter dropdown values order | A:34 `new TreeMap<String, Integer>(Strings.CULTURE_COMPARER)` | B:91-95 `TreeSet<>(Collator.getInstance(Locale.ROOT))` | §2A `SortedDictionary` row [C20, C31]; §3 `CULTURE_COMPARER` = `Collator(Locale.US)` TERTIARY | **BOTH WRONG.** cs:21-35 sorts with .NET `Comparer<string>.Default` (NLS, en-US). NLS ignores only hyphen and apostrophe ("word sort"); a space is a symbol weight that sorts before digits and letters. Java's Collator ignores the space at primary strength. jshell gives US and ROOT `[inactive, info, in progress]` and `[Newark, New Jersey, Newton, New York]`; NLS gives `in progress < inactive < info` and `New York < Newark`. Status, project, category and user values contain spaces (setup.sql:279,464-465), so dropdown order differs as soon as two values share a prefix. Replicates through every `CULTURE_COMPARER` use (5 `SortedDictionary<string` sites; `tags.cs:108` also uses it for key equality; string `OrderBy` rows). .NET side: documented NLS behavior; verify when D-001 lifts. |
| L4 | `ToLower` | A:551,597 `Strings.ToLower(dc.ColumnName())` | B:405,433 `name.toLowerCase(Locale.ROOT)` | §2A `ToLower` [S1-8] | **rulebook right (edge).** For U+0130, per-char lowering gives `i` (U+0069, as .NET), while ROOT `toLowerCase` gives the 2-char `i̇` (jshell). A column name containing `İ` would then not match `udf_column_name.ToLower()` (cs:610). S1-8 held. |
| L5 | iterating the view while rewriting `$VOTE` | A:667 `for (DataRowView drv : dv)` + A:784 `dr.set(i, …)` | B:473-477 `dv.rows().subList(from, to)` | gap: §2A DataSet / §3 `DataView` say nothing on enumeration | **both defensible (gap).** .NET `DataView.GetEnumerator()` copies the rows into an array (snapshot). cs:764 rewrites `$VOTE` from the Application cache (tags.cs:65), which differs from `votes_view` (setup.sql:902) until `threadproc_votes` has run or after a cache delta (vote.aspx:30). With `dv.Sort = "$VOTE DESC"`, a live, index-backed runtime iterator re-sorts mid-loop and skips or repeats rows. Pin the snapshot (amendment 5). 5 `foreach (DataRowView` sites in `src/`. |
| L6 | `Request["tags"]` | A:235,453 `HttpContext.Current().Request().get("tags")` | B:223,352 `request.getParameter("tags")` | §2A `Request["k"]` row | **rulebook right (minor).** cs:218,436 read the query string, then the form, then cookies and server variables, comma-joining repeats; `getParameter` reads no cookies and returns the first value (cf. r1 D21). |
| L7 | `Response.Write(null string)` | A:453 `Response.Write(HttpUtility.HtmlEncode(…get("tags")))` → `Write(String)` with `null` | B:84 `htmlEncode(null)` → `""` | gap: §2B row "`Object` overload applies `Strings.cs`"; §3 `HttpResponse` [S1-21] silent on `Write(String null)` | **both defensible only if the runtime is null-safe (gap, reachable).** cs:436: `HtmlEncode(null)` is `null`, and `HttpWriter.Write(string)` returns on `null`, so nothing is written. Reached on every `bugs.aspx` GET without `tags`. A runtime that appends a `null` String would render `value="null"` (amendment 2). |
| L8 | sort toggle | A:259 `Objects.equals(sort_val.value, prev_sort_val.value)` | B:237 `sortVal.equals(prevSortVal)` | §2A string `==` | **rulebook right (latent).** cs:240-242: `Convert.ToInt32(null)` is 0, and `==` is null-safe. B throws an NPE when `sort_val` is null with `actn_val == "sort"`. |
| L9 | `$FLAG`/`$SEEN`/`$VOTE` and the vote cache casts | A:730,743,775,781 `(int) dr.get(i)`, `(int) obj_vote_count` | B:506,514,526 `((Number) dr.get(i)).intValue()`, B:531 `instanceof Integer n` | §2A `(int)o` row | **rulebook right (latent).** cs:710,723,755,761 throw `InvalidCastException` when a saved query yields bigint/smallint/bit for these columns. B silently accepts them, and silently ignores a non-Integer cache entry. |
| L10 | `IndexOf("$$$", pos)` past the end | A:338 `Strings.IndexOf(filter_val, "$$$", pos)` (C27) | B:281-282 `filterVal.indexOf("$$$", pos)` then `substring(pos, pos2)` | §2A C27 | **both defensible.** .NET throws at the IndexOf; B throws at the substring. Both are uncaught (cs:313-324), so both give a 500; only the exception type differs. |

### version_control.cs → VersionControl

| # | Area | A | B | Rule / gap | Verdict and evidence |
|---|---|---|---|---|---|
| V1 | process output decoding | A:55,59 `new String(proc.getInputStream().readAllBytes(), UTF_8)` (stderr likewise) | B:77,82 same | §2A Process row [C20, J-C13]: "read stdout then stderr fully as UTF-8" | **BOTH WRONG (edge).** .NET 4.5 builds `StandardOutput` as `new StreamReader(pipe, StandardOutputEncoding, detectEncodingFromByteOrderMarks: true)`. A leading UTF-8 BOM is stripped, and a UTF-16/32 BOM switches the decoding. Both ports keep U+FEFF (jshell: `"﻿a".length() == 2`). `git_get_file_contents` / `svn_cat` / `hg cat` of a BOM file (every VS-saved `.cs`) therefore differ in line 1 of git_view/svn_view and the diff columns. `StandardErrorEncoding` is unset (cs:21-27), so stderr is decoded with `Console.OutputEncoding` (a Windows code page, not UTF-8), which matters for the error text shown at cs:60 (verify the code page on the original, D-001). |
| V2 | child stdin | A:40-54 default `ProcessBuilder` stdin pipe, never closed | B:72 `p.getOutputStream().close()` | gap: Process row silent on stdin | **NATIVE RIGHT (edge).** cs:21-27 does not redirect stdin, so the child inherits the worker's (absent) stdin and reads EOF at once. In A, a child that reads stdin (an hg/git credential helper; svn is guarded by `--non-interactive`) blocks forever. |
| V3 | stderr deadlock | A:49,144 sequential drain + `BUG(port)` | B:73-81 `CompletableFuture` drains stderr concurrently | §0.1; §1 (CompletableFuture banned) | **rulebook right.** cs:43-47,129-133 deadlock when stderr exceeds the pipe buffer. A reproduces and marks it; B's fix is unsanctioned. |
| V4 | argument construction | A:40,140 `Processes.command(filename, args)` (CommandLineToArgvW) | B:539-602 `List.of(…)` per argument; B:181-202 its own `splitArgs` for `SubversionAdditionalArgs` | §2A Process row | **rulebook right (edge).** Ordinary paths give the same tokens. A path or revision containing `"`, or a repo URL containing a space (cs:867,884: `repo` and the path concatenated unquoted), tokenizes differently in the original. B's `splitArgs` ignores the backslash rules. |
| V5 | working directory | A:42 `if (!Strings.IsNullOrEmpty(working_dir))` | B:66 `if (workingDir != null)` | §2A Process row [J-C13] | **rulebook right (edge).** cs:34: `""` means the current directory in .NET; B's `new File("")` makes `start()` fail. |
| V6 | `PadLeft` | A:508-509 `Strings.PadLeft(Convert.ToString(lx + 1), digit_places, '0')` (SILENT, 12 sites) | B:361-363 `String.format("%0" + width + "d", n)` | gap (F4) | **both defensible.** Same output with an English default locale. B's `String.format` is banned (§2A Strings) and uses the default locale's digits (jshell: `ar-EG` → non-ASCII digits). |
| V7 | `StringBuilder.Append(null)` | A:899,916,933,953,961 `args.append(repo)` | B:581 `repo + filePath.replace(" ", "%20")` | gap: no `StringBuilder.Append` rule (S1-46 covers `+` only) | **BOTH WRONG (latent).** cs:867,884,901,921,929: `Append((string)null)` appends nothing, while Java appends `"null"`. It is unreachable here (`svnrev_repository nvarchar(400) not null`, setup.sql:505; svn_log.aspx:49 casts it). The construct has 172 `.Append(` sites (excluding `sql.Append`) in 12 `StringBuilder` files. `Append(bool)` → `True` and `Append(double)` → G15 differ the same way (amendment 3). |
| V8 | credentials split | A:127 `Util.rePipes.Split(…)` | B:148 `Util.RE_PIPES.split(…)` (drops trailing empties) | §2A Regex `Split` | **both defensible.** cs:115-124 reads only `parts[0]`/`parts[1]` and requires both to be non-empty, so the outcome is equal. |

### Posture-only differences (not counted)

- B makes everything Spring `@Component`s with injected `Settings`/`Util`/`JdbcClient`, uses `PrintWriter` for `HttpResponse`, `Map`/`List` rows for DataRow/DataView, and records (`WritePostResult`, `PagingResult`, `SortFilterState`, `VisualDiff`) for `ref` outputs.
- B uses camelCase names, drops unused members (`convert_to_email`, `write_file_inline`'s `filename`), and merges duplicated `if`s.
- B uses SQL text blocks with `:name` parameters and typed `int` parameters instead of `@name` plus nvarchar (r1 D26/D27). A's `\r\n` literals are byte-identical (C24).
- B replaces `HttpContext.Current` (Session, Application, Request) with servlet `HttpSession`/`ServletContext`/`HttpServletRequest`.
- In VersionControl, B throws a `VersionControlException` for a @ControllerAdvice instead of `Response.Write(msg); Response.End()` (cs:62-63,154-155). The original appends `msg` to the page already buffered and ends the response with status 200; B leaves that to an unspecified advice.
- B logs through SLF4J instead of `Util.write_to_log`.
- B resolves the CSS paths with `webRoot.resolve` instead of `Util.GetAbsolutePath` (the runtime `Path` normalizes `\`, §2A).
- B's `Collator` is a `TreeSet` instead of a `SortedDictionary<string,int>` (same key semantics; the ordering itself is row L3).
- B's `str()` treats a null string as `""` at the `(string)` cast sites (cs:126-150). These columns are `isnull`-wrapped or `case`-computed (bug.cs:605-649), so they are never DBNull.

## A's 4 RULEBOOK-SILENT flags: proposed rules

| F | Site | A's choice | Assessment | Proposed rule |
|---|---|---|---|---|
| F1 | PrintBug A:584 `System.Diagnostics.Debug.Assert(false)` | dropped, `TODO(port)` | Correct. The TODO is unnecessary once a rule exists. | "`System.Diagnostics.Debug.*` (`Assert`, `WriteLine`, `Fail`) → the statement is dropped **including argument evaluation** (`[Conditional("DEBUG")]`; Release defines `TRACE` only, `BugTracker.Web.csproj:42`). Write the comment `// Debug.Assert dropped (RULEBOOK §2A)`; no marker." |
| F2 | PrintBug A:1001 `m.Groups[1].ToString()` | `m.Groups().get(1).Value()`, `TODO(port): runtime Match.Groups` | Correct shape; the semantics are unpinned (R06). | Add §3 `Match.Groups()` → `GroupCollection` with `get(int)` and `get(String)`, and `Group.Value()`/`Success()`. Numbering is **.NET's**: unnamed groups left to right, then named groups (so `Groups[1]` of `(?<n>bug)#([0-9]+)` is the digits). A non-participating or out-of-range group has `Value() == ""` and `Success() == false`, never `null`. `m.Groups[n].ToString()` → `.Value()`. |
| F3 | BugList A:38 `dict[k] = 1` on `SortedDictionary` | `Collections2.set(dict, k, 1)` | Correct. The S1-28 row names only `Dictionary`. | Extend the S1-28 indexer/`ContainsKey`/`Add` rows to `SortedDictionary` (a `TreeMap`): the same `Collections2` calls. A `null` key → `ArgumentNullException`; the comparator decides key equality. |
| F4 | VersionControl A:508 `s.PadLeft(n, c)` (12 sites) | `Strings.PadLeft(s, n, c)`, 12 TODOs | Correct. | "`s.PadLeft(n)` / `s.PadLeft(n, c)` / `PadRight` → `Strings.PadLeft(s, n[, c])` / `Strings.PadRight(…)` (runtime; `c` defaults to `' '`; never truncates; `n < 0` → `ArgumentOutOfRangeException`). `String.format` stays banned." Add both to the §3 `Strings` list. |

**Gaps A did not flag.** None of these are A's fault: each one looks covered by an existing rule.
- `Response.Write(String null)` (L7). The rule pins only the `Object` overload.
- `StringBuilder.Append` of a nullable string, bool or double (V7). Only `+` is ruled (S1-46).
- `DataView` enumeration semantics (L5).
- `WriteFile` decoding on a writer-backed response (R07).
- The Process row's decoding and stdin (V1, V2). The rule exists but is insufficient.

## Round-over-round delta (sections amended in round 1)

The rule-level column tracks the `[S1-n]` rules themselves. The section-level column is the stop criterion as asked.

| Section (r1 tags) | Exercised here by | Rule level | Section level |
|---|---|---|---|
| §0 (S1-26) | — (no UI unit) | not exercised | not exercised |
| §1 (S1-2, 4, 5, 9) | B's `CompletableFuture` (V3); A obeys | held | held |
| §2A General conventions (S1-28, 29, 39, 40, 44, 45) | F3; L3 via the `SortedDictionary` row [C20, C31] | S1-28 held (coverage gap: `SortedDictionary`, F3) | **re-indicted** (L3, by the untouched C20/C31 row) |
| §2A Strings (S1-8, 15, 16, 20, 21, 46) | L4 (S1-8), every concat in A (S1-46), R03/R04 (S1-21 acceptance), V7, F4 | S1-8, S1-21, S1-46 held | **re-indicted** (V7 `StringBuilder.Append`, F4 `PadLeft`: constructs without a rule) |
| §2A Conversions (S1-6, S1-11) | L1 | S1-6 held | held |
| §2A Dates, numbers, culture (S1-6, 15, 18) | — | not exercised | not exercised |
| §2A Exceptions (S1-3, 5, 10, 22) | R08 | S1-10 held | held |
| §2A Database (S1-7, S1-9) | L2 | S1-7 held | held |
| §2A DataSet / EF6 (S1-7, 12, 19, 38, 42, 43) | A:513/595 `for (DataColumn dc : …Columns())` (S1-19), L5 | S1-19 held | gap only (L5; not a defect today) |
| §2A Web intrinsics (S1-17, S1-21) | R01, R02, L6 (rows not tagged S1) | held | held |
| §2A Logging/mail/search/processes/POP3 (S1-1, 3, 4, 5, 13, 29, 48) | V1, V2, V4, V5 (Process row [C20, J-C13]) | S1 rows not exercised | **re-indicted** (V1 both wrong, V2 native right, by the untouched Process row) |
| §2A Threads, timers, async (S1-1, 2, 14, 47) | — | not exercised | not exercised |
| §2B (S1-26 … S1-37) | L7 (`Response.Write` row, not S1) | not exercised | gap (L7) |
| §3 (S1-2 … S1-34) | L3 (`CULTURE_COMPARER`, gate-02 block), L7/R07 (`HttpResponse` bullet [S1-21] silent on `Write(String null)`/`WriteFile`), L1 (`DotNetFormat` S1-6) | S1-6 held; S1-21 coverage gap | **re-indicted** (L3) |
| §4 (S1-4) | PrintBug A:1095 nested `WritePostResult` | held | held |
| §5 (S1-23, S1-36) | A's `// inventory:` citations (BugList L66/L187/L321, VC L32/L395/L465…) | held | held |
| §6 (S1-24, 25, 41) | headers, citation form, `todos=` 2/0/12 exact | held | held |
| §7 (D-006) | — | n/a | n/a |

**Verdict on convergence.** No `[S1-n]` text was wrong. Three S1 rules have coverage gaps (S1-21 `HttpResponse`, S1-28, S1-46). Four amended sections carry new both-wrong or native-right findings, all from pre-round-1 rules or unruled constructs. Under the section-level criterion, **the stress test stops here for a human decision.**

## Amendment queue (for the human; nothing applied)

Ordered by blast radius × severity.

1. **`Strings.CULTURE_COMPARER` emulates NLS, not `java.text.Collator`** (L3).
   - Rule text: "`CULTURE_COMPARER` is a runtime `NlsComparer` (en-US, default `CompareOptions.None`):
     - hyphen-minus U+002D and apostrophe U+0027 carry no primary weight (word sort) and only break ties;
     - every other punctuation and white-space character, the space included, is a symbol that sorts before digits, and digits sort before letters;
     - letters compare alphabetically, then by accent, then by case (lower before upper);
     - canonically equivalent sequences compare equal;
     - `null` sorts first.
     `java.text.Collator` is BANNED in translated code and in the runtime."
   - Acceptance vectors (verify on the original when D-001 lifts): `"in progress" < "inactive" < "info"`, `"New York" < "Newark"`, `"a-z" > "ab"`, `"apple" < "Apple"`.
   - Evidence: bug_list.cs:21; tags.cs:108; jshell outputs above.
   - Regenerate every unit that uses `CULTURE_COMPARER` (5 `SortedDictionary<string` sites; LINQ string `OrderBy` rows).
2. **`HttpResponse.Write(String null)`** (L7). §3 `HttpResponse` bullet [S1-21] gets: "`Write(String s)`: `s == null` writes nothing (.NET `HttpWriter.Write`); `Write(Object o)` = `Write(Strings.cs(o))`; `Write(char)` writes the char." Evidence: bug_list.cs:436.
3. **`StringBuilder.Append`** (V7).
   - Rule text: "`sb.Append(x)` → `sb.append(x)` when `x` is a string literal, `char`, or `int`/`long` expression. Otherwise `sb.append(Strings.cs(x))`: a null string appends nothing, `bool` gives `True`, and `double` gives G15, as S1-46 does for `+`. `sb.AppendLine(x)` → `sb.append(Strings.cs(x)).append(Environment.NewLine)`."
   - Evidence: version_control.cs:867-930; 172 `.Append(` sites.
4. **Process row: decoding and stdin** (V1, V2). Replace "read stdout then stderr fully as UTF-8" with:
   - "Read stdout fully, then stderr, through runtime `Processes.readToEnd(InputStream, Encoding)`, which equals .NET `StreamReader(…, detectEncodingFromByteOrderMarks: true)`: a leading UTF-8 BOM is stripped, and UTF-16 LE/BE and UTF-32 BOMs switch the decoding.
   - stdout uses `StandardOutputEncoding` (UTF-8 at version_control.cs:27). stderr uses the original host's `Console.OutputEncoding`, which the runtime pins after verification (`TODO(port)` runtime-owned, D-001).
   - stdin: `pb.redirectInput(ProcessBuilder.Redirect.from(<null device>))`, so the child reads EOF as with the IIS worker's inherited stdin. The deadlock-preserving order is kept (`BUG(port)` as A wrote it)."
5. **DataView enumeration is a snapshot** (L5). Rule text (§2A DataSet table and §3): "`for (DataRowView drv : dv)` iterates a copy of the view's rows taken when the loop starts (.NET `DataView.GetEnumerator` copies them to an array). `dv.Count()`, `dv.get(i)` and the sort index stay live and are re-evaluated when a row value changes." Evidence: bug_list.cs:647,764; 5 sites.
6. **`Match.Groups`** (F2, R06). Use the rule text in F2.
7. **`HttpResponse.WriteFile` on a writer-backed response** (R07). Rule text: "`new HttpResponse(Writer)` + `WriteFile(path)` decodes the whole file with `Encoding.Default` (windows-1252, no BOM detection) and writes the chars (.NET `WriteStreamAsText`). On the request response, `WriteFile` copies the bytes unchanged." Evidence: print_bug.cs:56-67; bug.cs:1158.
8. **`PadLeft`/`PadRight`** (F4). Use the rule text in F4.
9. **`Debug.*`** (F1). Use the rule text in F1.
10. **`SortedDictionary` indexer/`ContainsKey`/`Add`** (F3). Use the rule text in F3.
11. **Wording nit, §2A `IndexOf/StartsWith` row [S1-21].** "differ only on ignorable chars" → "differ only on ignorable characters and canonically equivalent sequences (precomposed vs combining forms) — accepted". No behavior change.

**Regeneration note (standing rule 6).**
- Amendments 1, 2, 3 and 5 change semantics every unit may touch. After they are applied:
  - grep translated units for `CULTURE_COMPARER` (1)
  - re-run `display_buglist_tags_line`-style `Response.Write(HtmlEncode(Request…))` sites against the runtime (2)
  - grep `\.append\(` on non-literal operands (3)
  - grep `for (DataRowView` (5)
- Amendment 4 touches only `VersionControl`; regenerate it.
