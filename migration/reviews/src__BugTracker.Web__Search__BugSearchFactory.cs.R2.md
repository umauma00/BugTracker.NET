# Review R2: src/BugTracker.Web/Search/BugSearchFactory.cs

Target: `port/web/src/main/java/btnet/Search/BugSearchFactory.java` (core unit; no template, §4).
Reviewer: R2 (adversarial, read-only). Date: 2026-11-04.

## Inputs checked

- Source `src/BugTracker.Web/Search/BugSearchFactory.cs:1-27`, target `BugSearchFactory.java:1-27`, side by side.
- RULEBOOK.md read in full (§0 to §7, §3.1 index).
- Inventory: `grep BugSearchFactory migration/inventory.tsv` gives **no rows**. Every construct in the unit is decided by a rule row (§2A "General conventions" `static class`, Strings `string.IsNullOrEmpty`, Exceptions list `ConfigurationErrorsException`, §2A search row "`BugSearchFactory` builds runtime `ElasticClient(uri, "btnet")`"), so no per-site inventory decision is missing. See Rulebook gaps G2.
- Runtime members called (each checked on disk): `btnet.Util.get_setting(String, String)` (`port/web/.../btnet/Util.java:159`), `btnet.runtime.Strings.IsNullOrEmpty(String)` (`Strings.java:47`), `btnet.runtime.ConfigurationErrorsException(String)` (unchecked, extends `DotNetException`), `btnet.runtime.net.Uri(String)` (.NET `new Uri(string)`: absolute only, `ArgumentNullException`/`UriFormatException`), `btnet.runtime.search.ElasticClient(Uri, String)`, `btnet.Search.BugSearch(ElasticClient)` (`BugSearch.java:34`), `btnet.Search.IBugSearch`. All are in §3.1 or are translated app units.

## Line-by-line comparison

| Source | Target | Verdict |
|---|---|---|
| `:7` `public static class BugSearchFactory` | `:10-11` `public final class BugSearchFactory { private BugSearchFactory() {} }` | §2A `static class` row: matches |
| `:9` `public static IBugSearch CreateBugSearch()` | `:13` same name, same return type, public static | §2A names, §6.2: matches |
| `:11` `Util.get_setting("SearchServerURI", string.Empty)` | `:14` `Util.get_setting("SearchServerURI", "")` | same key, same default: matches |
| `:12` `string.IsNullOrEmpty(uriString)` | `:15` `Strings.IsNullOrEmpty(uriString)` | §2A Strings row: matches |
| `:14` `throw new ConfigurationErrorsException("SearchServerURI is missing. This application setting is required in order to use search")` | `:16` same type, message byte-identical | §2A Exceptions list, §6.2 (user-visible message identical): matches |
| `:17` `new Uri( uriString)` | `:19` `new Uri(uriString)` (runtime `btnet.runtime.net.Uri`, not `java.net.URI`) | the validation and exception type (`UriFormatException` on relative or malformed input) surface at the same statement, as in .NET: matches |
| `:19-23` `new ConnectionSettings(node, defaultIndex: "btnet")`, `new ElasticClient(settings)` | `:22` `new ElasticClient(node, "btnet")` | §2A search row ("builds runtime `ElasticClient(uri, "btnet")`"): matches. Index name `btnet` is identical |
| `:24` `return new BugSearch(client)` | `:23` same | matches |

Header (§6.1) `// Source: src/BugTracker.Web/Search/BugSearchFactory.cs` is correct. The trailer (§6.7) `// PORT STATUS: confidence=high todos=0` is correct: the file has zero `TODO(port)` markers. Imports are explicit single-type imports with no unused or wildcard imports, and there is no name clash (§4). The file declares no checked exceptions (§2A Exceptions). The only comment, at `:21`, is a plain explanatory comment and not a marker (§3 marker formats), so it is allowed.

Control flow, exceptions and null handling: `get_setting` never returns null here, because the default is `""` and `get_setting`'s own empty-to-default logic is kept (§2A Settings). The null-or-empty test therefore covers the same cases as .NET. A whitespace-only setting gets past the check in both versions and fails in `new Uri`, the same as .NET. There is no shared state, so thread safety is unaffected: like .NET, each call builds a fresh client.

## Findings

None.

Severity counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

**OBEDIENT.** The implementer followed every applicable rule, and I found no deviation:

- §2A `static class` → `final` class with a private constructor: followed.
- §2A naming (member names identical, package `btnet.Search` = namespace): followed.
- §2A Strings `string.IsNullOrEmpty` → `Strings.IsNullOrEmpty`: followed.
- §2A Exceptions (runtime `ConfigurationErrorsException`, unchecked): followed.
- §2A search row (`ElasticClient(uri, "btnet")`, runtime `btnet.runtime.search`, no ES client library per §1): followed.
- §2A Web intrinsics / S1-21 (runtime `btnet.runtime.net.Uri`, never `java.net.URI`): followed, even though the `ElasticClient(java.net.URI, …)` and `(String, …)` overloads would also have compiled.
- §3 S1-13 ("Translators never mark" `ElasticClient` failure semantics): followed, with no marker added.
- §4 imports and one file per unit: followed.
- §6.1 header, §6.2 completeness (one type, one method, all present), §6.3 markers, §6.7 trailer: followed.
- §6.5 inventory citation: not applicable, because the unit has no inventory rows.

## Rulebook gaps

- **G1. The `ElasticClient` constructor and `Uri(String)` constructor are not pinned in §3.** §2A says only "`ElasticClient(uri, "btnet")`", without the type of `uri`. §3 lists `Search` and `Index` but no constructor, and the §3 `Uri` entry describes only `HttpRequest.Url()`, `toString()`, `AbsoluteUri()` and `PathAndQuery()`. It has no `new Uri(String)`. The runtime on disk offers three constructors: `ElasticClient(btnet.runtime.net.Uri, String)`, `(java.net.URI, String)` and `(String, String)`. Two translators could therefore pass `java.net.URI` or the raw string, which would skip the .NET `new Uri` validation step (`BugSearchFactory.cs:17`, which throws `UriFormatException`). Proposed rule for §3: "`new Uri(string)` → `new btnet.runtime.net.Uri(s)` (.NET absolute-URI parsing; null → `ArgumentNullException`, relative or malformed → `UriFormatException`); `ElasticClient(Uri node, String defaultIndex)` is the constructor translators use (`BugSearchFactory.cs:17-23`); the `java.net.URI` and `String` overloads are runtime-internal." The implementer's choice already matches this proposal.
- **G2. Units without inventory rows.** §5 says "a site missing from the inventory is an inventory bug". It does not say whether a unit whose every construct is decided by a §2A rule row (as here) needs any rows. Proposed clarification for §5: "a construct fully decided by a §2A/§3 rule row needs no inventory row; only per-site decisions do." Evidence: `migration/inventory.tsv` has no `BugSearchFactory.cs` rows, but `BugSearchFactory.cs:7-24` is fully covered by the rules cited above.
