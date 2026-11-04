# Review R1: src/BugTracker.Web/App_Start/BundleConfig.cs

Target: `port/web/src/main/java/btnet/App_Start/BundleConfig.java` (12 lines)
Templates per RULEBOOK §4: none (core-layer `App_Start/` unit, §0; no page, master or `.ascx`).
Inventory rows for this source path: none (`grep BundleConfig migration/inventory.tsv` returns nothing).

## Checks performed

| Check | Rule | Result |
|---|---|---|
| Output path = manifest naming (`<module>/src/main/java/<namespace>/<PrimaryType>.java`) | §4 | OK: `port/web/src/main/java/btnet/App_Start/BundleConfig.java` |
| Package = C# namespace verbatim (`btnet.App_Start`) | §4 | OK (line 2) |
| Header `// Source: src/BugTracker.Web/App_Start/BundleConfig.cs` on line 1 | §6.1 | OK |
| Completeness: class `BundleConfig` (public, non-static), method `public static void RegisterBundles(BundleCollection)` | §6.2, §0 core posture | OK, same names and casing; implicit public default ctor matches C# |
| Not a C# `static class`, so the §2A `private C() {}` row does not apply | §2A row line 132 | OK, correctly not applied |
| Body: `bundles.Add(new ScriptBundle("~/bundles/all").IncludeDirectory("~/Scripts/Site", "*.js", true))`, literals identical | §6.2, §2B ScriptBundle bullet | OK (line 9 vs source :13), argument order and values identical |
| Runtime API used is in §3 (`btnet.runtime.web`: `BundleCollection`, `ScriptBundle`) and exists with these signatures (`Add(ScriptBundle)`, `ScriptBundle(String)`, `IncludeDirectory(String,String,boolean)` returning `ScriptBundle`) | §3, §4 home of shared types | OK; compiles by reading |
| Imports explicit single-type, no wildcard, no static; no name clash with app types | §4, J-C15 | OK |
| C# `using` directives (System, System.Linq, System.Web, ...) unused: dropping them loses no behavior | n/a | OK |
| No source comments to copy | §6.2 | OK (source has none) |
| Markers: none needed; `todos=0` matches zero `TODO(port)` | §6.3, §6.7 | OK |
| Trailer last line `// PORT STATUS: confidence=high todos=0` | §6.7 | OK |
| Scope: only the manifest target written; no runtime/other-unit edits | §6.4 | OK (no other file references this unit's changes) |
| Inventory citation: no inventory decision used, so no `// inventory:` comment required | §5, §6.5 | OK |
| Thread-safety: `BundleCollection.Add` is synchronized in runtime; unit adds no state | n/a | OK |

## Findings

None. I tried to find a defect and could not: the translation is a token-for-token mapping of the single statement onto the §3 runtime types, and every §6 obligation is met.

## Rulebook obedience

OBEDIENT. Deviations: 0. Checked §0 (core layer, structure-preserving), §2A (static-class row correctly not applied), §2B (ScriptBundle bullet: registration preserved, rendering is left to the runtime `Scripts.Render`), §3 (only listed runtime types used), §4 (path, package, imports, member names), §5/§6.5 (inventory grep: no rows; none needed), §6.1-§6.7 (header, completeness, markers, scope, trailer).

## Rulebook gaps

- G1 (informational, low priority): §2B's `ScriptBundle` bullet (RULEBOOK.md ~L981-985) specifies rendering but not the translation of `BundleConfig.cs` itself or of the caller chain `Global.asax.cs:36-42` (`RegisterBundles()` -> `BundleConfig.RegisterBundles(BundleTable.Bundles)`). `BundleTable` exists in the runtime (`port/runtime/src/main/java/btnet/runtime/web/BundleTable.java`) but is **not listed** in §3's `btnet.runtime.web` row (RULEBOOK.md L1086), which lists `BundleCollection` and `ScriptBundle` only. The `Global` translator needs `BundleTable.Bundles`; a strict reviewer of `Global.java` could flag it as "runtime API not in §3". Proposed: add `BundleTable` (`Bundles` static field) to the §3 `btnet.runtime.web` list, and add to the §2B bullet: "`Global.asax.cs:42` `BundleConfig.RegisterBundles(BundleTable.Bundles)` -> same call over runtime `BundleTable.Bundles`." Evidence: `src/BugTracker.Web/Global.asax.cs:40-42`; `BundleTable.java` javadoc.
- G2 (informational): the runtime `ScriptBundle` javadoc cites "RULEBOOK §2B B6" for unbundled rendering, but the bullet is not labelled B6 in §2B as I read it (the bullet at ~L981 is unlabelled). Not a defect in this unit; the label should be confirmed or the runtime comment corrected by the runtime author.
