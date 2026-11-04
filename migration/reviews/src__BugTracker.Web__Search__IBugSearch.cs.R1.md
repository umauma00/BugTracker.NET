# Review R1 — src/BugTracker.Web/Search/IBugSearch.cs

Target: `port/web/src/main/java/btnet/Search/IBugSearch.java`
No templates or fragments: this is a core-layer `.cs` interface (§0, §4).

## Inputs checked

- Source: `src/BugTracker.Web/Search/IBugSearch.cs:1-31`
- Translation: `port/web/src/main/java/btnet/Search/IBugSearch.java:1-31`
- Inventory: `grep IBugSearch migration/inventory.tsv` returns no rows. The unit
  has no gap construct (no `ref`/`out`, no nullable columns, no SQL, no regions),
  so a missing row is not an inventory bug (§5).
- Manifest: `migration/manifest.tsv:18` maps the unit to the target path. Path
  and package `btnet.Search` match §4 (namespace verbatim, primary type = file stem).
- Runtime existence (§3.1 index, on disk): `btnet.runtime.data.DataSet`
  (`port/runtime/src/main/java/btnet/runtime/data/DataSet.java:4`, class) and
  `btnet.runtime.security.IIdentity` (`port/runtime/.../security/IIdentity.java:7`,
  interface). Both are listed in §3 (RULEBOOK:1082, :1088) and §3.1 (:1737, :1743).
- Name clash check (§4 J-C15): the app has no type named `DataSet` or `IIdentity`,
  so plain single-type imports are correct.

## Line-by-line comparison

| Source | Target | Verdict |
|---|---|---|
| `IBugSearch.cs:1-2` `using System.Data; using System.Security.Principal;` | `java:4-5` explicit single-type imports of the runtime stand-ins | correct (§4 imports, §3) |
| `:4` `namespace btnet.Search` | `java:2` `package btnet.Search;` | correct (§4) |
| `:6-8` `<summary>` comment | `java:7-9` Javadoc, same text | copied (§6.2); see gaps |
| `:9` `public interface IBugSearch` | `java:10` `public interface IBugSearch` | correct |
| `:15` `void IndexAll();` | `java:15` `void IndexAll();` | correct, name kept (§2A L130, §4) |
| `:21` `void IndexBug(int bugId);` | `java:21` `void IndexBug(int bugId);` | correct |
| `:29` `DataSet Search(string searchText, IIdentity identity);` | `java:29` `DataSet Search(String searchText, IIdentity identity);` | correct; interface members stay implicitly public (§2A L136 exception) |
| (none) | `java:1` `// Source: src/BugTracker.Web/Search/IBugSearch.cs` | correct header (§6.1) |
| (none) | `java:31` `// PORT STATUS: confidence=high todos=0` | correct trailer; the file has 0 `TODO(port)` markers, so 0 is the right count (§6.7) |

All three members are present (§6.2 completeness). There is no SQL, no
user-visible text, and no control flow. The source typo "Index of re-index"
(`:18`) is copied unchanged, which is correct (§0: quirks are kept).

## Findings

None.

## Rulebook obedience

OBEDIENT. Every rule was checked against the file, and no deviation was found:
§4 (path, package, names, imports), §2A L130 (names identical), §2A L136
(interface members), §3/§3.1 (only runtime types that are on disk), §6.1
(header), §6.2 (completeness, comments copied), §6.3 (no stray markers), §6.4
(only the target was written), §6.5 (no inventory rows, so no citation is
needed), and §6.7 (trailer).

## Rulebook gaps

1. **Form of copied XML doc comments.** §6.2 says only "Source comments are
   copied as comments" and excludes `<summary>` boilerplate only for
   `*.designer.cs`. It does not say whether a `///` `<summary>`/`<param>`/
   `<returns>` block is copied verbatim as `//` lines or turned into Javadoc
   (`@param`/`@return`), as this implementer did (`java:7-9, 11-14, 17-20,
   23-28`). The text is kept either way, but different units may end up with
   different forms. One small loss: the trailing space in "Re-index all bugs. "
   (`IBugSearch.cs:12`) is dropped in `java:12`. Proposed rule: "A `///` XML
   doc comment becomes Javadoc: `<summary>` text becomes the body, `<param
   name="x">` becomes `@param x`, `<returns>` becomes `@return`, and the text
   is kept verbatim apart from trailing whitespace."
