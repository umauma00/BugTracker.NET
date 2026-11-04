# Review R1 — src/BugTracker.Web/Search/BugSearchFactory.cs

Target: `port/web/src/main/java/btnet/Search/BugSearchFactory.java` (core unit; no template per RULEBOOK §4).
Inputs read: RULEBOOK.md (all sections; §2A General/Strings/Exceptions, the §2A NEST `ElasticClient` row, §3 `ElasticClient`/`Uri` semantics and S1-13, §3.1, §4, §5, §6), `migration/inventory.tsv` (no rows for this source path; none for `Search/BugSearchFactory`), the C# source (27 lines), the translation (27 lines), and for context the runtime `btnet.runtime.search.ElasticClient`, `btnet.runtime.net.Uri`, `btnet.runtime.ConfigurationErrorsException`, the translated `btnet.Util.get_setting`, `btnet.Search.BugSearch(ElasticClient)` and `IBugSearch`.

## Line-by-line check

| Source | Target | Verdict |
|---|---|---|
| `:7` `public static class BugSearchFactory` | `:10-11` `public final class BugSearchFactory { private BugSearchFactory() {} }` | §2A `static class` row — correct |
| `:9` `public static IBugSearch CreateBugSearch()` | `:13` same name, same return type, `public static` | §2A names, §6.2 completeness — correct |
| `:11` `Util.get_setting("SearchServerURI", string.Empty)` | `:14` `Util.get_setting("SearchServerURI", "")` | `btnet.Util.get_setting(String, String)` exists (`Util.java:159`); `string.Empty` = `""` — correct |
| `:12` `string.IsNullOrEmpty(uriString)` | `:15` `Strings.IsNullOrEmpty(uriString)` | §2A Strings row — correct |
| `:14` `throw new ConfigurationErrorsException("SearchServerURI is missing. …")` | `:16` same type (`btnet.runtime.ConfigurationErrorsException`, §2A Exceptions, §3.1), message byte-identical | correct |
| `:17` `new Uri( uriString)` | `:19` `new Uri(uriString)` with runtime `btnet.runtime.net.Uri` (§3 [S1-21], §3.1) | relative/malformed → `UriFormatException` "Invalid URI: The format of the URI could not be determined." as .NET; leading/trailing whitespace trimmed as .NET — correct |
| `:19-23` `new ConnectionSettings(node, defaultIndex: "btnet")` + `new ElasticClient(settings)` | `:22` `new ElasticClient(node, "btnet")` | exactly the §2A NEST row ("`BugSearchFactory` builds runtime `ElasticClient(uri, "btnet")`"); constructor `ElasticClient(Uri, String)` exists; no I/O at construction (as NEST) — correct |
| `:24` `return new BugSearch(client)` | `:23` same; `BugSearch(ElasticClient)` exists | correct |

Other obligations: header `// Source: src/BugTracker.Web/Search/BugSearchFactory.cs` (§6.1) correct; explicit single-type imports, no clash with an application type of the same simple name (§4 J-C15; no app `Uri`/`ElasticClient`/`ConfigurationErrorsException` exists); no checked exceptions declared (§2A Exceptions); the added line-21 comment is a plain comment, not a marker (§3 marker formats); no `TODO(port)` markers, trailer `// PORT STATUS: confidence=high todos=0` is the last line and the count is right (§6.7); no source comments to copy (§6.2); no SQL, no user-visible text other than the exception message; no shared state, so no thread-safety concern. S1-13 failure semantics are runtime-owned and "translators never mark it" — correctly unmarked.

## Findings

None. No defect found in output text, exception type/message, null handling, construction order, member set, imports or markers.

## Rulebook obedience

OBEDIENT. Every construct in the unit has a rule row and the implementer applied each one as written: §2A `static class`, §2A `string.IsNullOrEmpty`, §2A Exceptions (`ConfigurationErrorsException` runtime class), §3 [S1-21] runtime `Uri` (not `java.net.URI`), §2A NEST `ElasticClient` row (`ElasticClient(uri, "btnet")`), §4 package/path/imports, §6.1 header, §6.7 trailer. No deviations found. No inventory row was needed (every site is decided by a rule row), so the absence of rows is not an inventory bug under §5.

## Rulebook gaps

1. `string.Empty` / `String.Empty` has no row in §2A Strings. The implementer chose `""`, which is the only reasonable answer, but two agents could differ (`""` vs. a runtime constant). Proposed row: "`string.Empty` / `String.Empty` → the literal `""`." Evidence: `BugSearchFactory.cs:11`; also used elsewhere (e.g. the `?? string.Empty` example in §2A `a ?? b` row).
2. (Runtime owner, not translators) .NET Framework 4.5 `new Uri(string)` accepts implicit file paths (`C:\x`, `\\server\share`) as `file:` URIs; §3 `Uri` [S1-21] does not say whether the runtime emulates that. Unreachable for a configured `SearchServerURI` in practice; proposed: state in §3 `Uri` that implicit file paths are either emulated or are an accepted divergence (D-006 list).
