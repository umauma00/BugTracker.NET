# Confirmed findings — src__BugTracker.Web__Search__BugSearchFactory.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

1. `string.Empty` / `String.Empty` has no row in §2A Strings. The implementer chose `""`, which is the only reasonable answer, but two agents could differ (`""` vs. a runtime constant). Proposed row: "`string.Empty` / `String.Empty` → the literal `""`." Evidence: `BugSearchFactory.cs:11`; also used elsewhere (e.g. the `?? string.Empty` example in §2A `a ?? b` row).
2. (Runtime owner, not translators) .NET Framework 4.5 `new Uri(string)` accepts implicit file paths (`C:\x`, `\\server\share`) as `file:` URIs; §3 `Uri` [S1-21] does not say whether the runtime emulates that. Unreachable for a configured `SearchServerURI` in practice; proposed: state in §3 `Uri` that implicit file paths are either emulated or are an accepted divergence (D-006 list).

- **G1. The `ElasticClient` constructor and `Uri(String)` constructor are not pinned in §3.** §2A says only "`ElasticClient(uri, "btnet")`", without the type of `uri`. §3 lists `Search` and `Index` but no constructor, and the §3 `Uri` entry describes only `HttpRequest.Url()`, `toString()`, `AbsoluteUri()` and `PathAndQuery()`. It has no `new Uri(String)`. The runtime on disk offers three constructors: `ElasticClient(btnet.runtime.net.Uri, String)`, `(java.net.URI, String)` and `(String, String)`. Two translators could therefore pass `java.net.URI` or the raw string, which would skip the .NET `new Uri` validation step (`BugSearchFactory.cs:17`, which throws `UriFormatException`). Proposed rule for §3: "`new Uri(string)` → `new btnet.runtime.net.Uri(s)` (.NET absolute-URI parsing; null → `ArgumentNullException`, relative or malformed → `UriFormatException`); `ElasticClient(Uri node, String defaultIndex)` is the constructor translators use (`BugSearchFactory.cs:17-23`); the `java.net.URI` and `String` overloads are runtime-internal." The implementer's choice already matches this proposal.
- **G2. Units without inventory rows.** §5 says "a site missing from the inventory is an inventory bug". It does not say whether a unit whose every construct is decided by a §2A rule row (as here) needs any rows. Proposed clarification for §5: "a construct fully decided by a §2A/§3 rule row needs no inventory row; only per-site decisions do." Evidence: `migration/inventory.tsv` has no `BugSearchFactory.cs` rows, but `BugSearchFactory.cs:7-24` is fully covered by the rules cited above.
