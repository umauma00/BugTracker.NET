# Review R2 — src/BugTracker.Web/Search/IBugSearch.cs

Target: `port/web/src/main/java/btnet/Search/IBugSearch.java` (manifest row 18). Core-layer unit (§0, `Search/`); no template (§4: templates only for pages/masters/.ascx/inc).

Checked:
- Source `src/BugTracker.Web/Search/IBugSearch.cs:1-31` vs. target `:1-31` side by side.
- Inventory: `grep IBugSearch migration/inventory.tsv` → no rows. The interface has no construct the inventory tracks (no ref/out, nullable, LINQ, catch, SQL), so the absence is not an inventory bug (§5).
- Imports against §3.1: `btnet.runtime.data.DataSet` (§3.1 line 1737, file exists `port/runtime/src/main/java/btnet/runtime/data/DataSet.java`) and `btnet.runtime.security.IIdentity` (§3.1 line 1743, file exists; .NET `System.Security.Principal.IIdentity` per §2B B7 / §3). Explicit single-type imports (§4). No name clash with an application type (§4 J-C15): no `btnet.*` app type named `DataSet` or `IIdentity`.
- Package `btnet.Search` = namespace `btnet.Search` verbatim (§2A General, §4).
- Members: `IndexAll()` (src:15 → tgt:15), `IndexBug(int bugId)` (src:21 → tgt:21, `int`→`int` §2A), `Search(string searchText, IIdentity identity)` returning `DataSet` (src:29 → tgt:29, `string`→`String`). Names and casing unchanged (§2A, §4). No members missing (§6.2). Interface members keep implicit public access (§2A S2-12 exception for interface members).
- Header `// Source: src/BugTracker.Web/Search/IBugSearch.cs` as first line (§6.1). Trailer `// PORT STATUS: confidence=high todos=0` is the last line (§6.7); file has zero `TODO(port)` markers, so `todos=0` is correct.
- Source comments copied (§6.2): all three summaries and the param/returns texts are present with text unchanged, including the source typo "Index of re-index" (src:18 → tgt:18), which is correctly not "fixed" (§0). No `TODO`/`FIXME`/`HACK` keywords in source comments.
- `using System.Data; using System.Security.Principal;` (src:1-2) are correctly replaced by the two runtime imports; no stray imports.
- No behaviour to diverge (no bodies, no SQL, no strings, no exceptions). No compile-level errors visible: `DataSet` is a class in runtime, `IIdentity` an interface; `Search` as a method name is legal in Java and does not collide with the package segment `Search`.

## Findings

None.

## Rulebook obedience

OBEDIENT. Every applicable rule was followed: §2A type/member names and namespace→package, §2A primitive/`string` mapping, §3/§3.1 runtime types for `DataSet` and `IIdentity`, §4 output path and explicit imports, §6.1 header, §6.2 completeness and comment copying, §6.3 no stray markers, §6.4 scope (only the manifest target written), §6.7 status trailer with correct count. No deviations found.

## Rulebook gaps

- **G1 — XML doc comments (`///` with `<summary>`, `<param>`, `<returns>`) in non-designer files.** §6.2 says "Source comments are copied as comments" and §6.2 [S2-31] drops only the `<summary>`/`<remarks>` boilerplate of `*.designer.cs`. It does not say whether a hand-written XML doc comment is copied verbatim as `/// <summary>…` line comments or rendered as Javadoc (`/** … @param x … @return … */`). This implementer chose Javadoc (target :7-9, :11-14, :17-20, :23-28), dropping the XML tags and a trailing space after "Re-index all bugs. " (src:12). The text is preserved, so it is not a defect, but two implementers could answer differently (meta-rule). Proposed rule (§6.2): "A C# XML doc comment becomes a Javadoc comment on the same member: `<summary>` text → the description, `<param name="x">t</param>` → `@param x t`, `<returns>t</returns>` → `@return t`, other tags kept as text; line text otherwise unchanged (trailing whitespace may be dropped)." Evidence: `Search/IBugSearch.cs:6-8,11-14,17-20,23-28`; other XML-doc sites exist in `Search/BugSearch.cs` and elsewhere in `src/`.
