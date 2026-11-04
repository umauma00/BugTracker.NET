# Review R2: src/BugTracker.Web/App_Start/BundleConfig.cs

Target: `port/web/src/main/java/btnet/App_Start/BundleConfig.java` (manifest row 2).
Reviewer: R2 (adversarial, read-only). Date: 2026-11-04.

## Inputs read

- Source: `src/BugTracker.Web/App_Start/BundleConfig.cs:1-16`
- Target: `port/web/src/main/java/btnet/App_Start/BundleConfig.java:1-12`
- RULEBOOK §0 (core layer, `App_Start/` structure-preserving), §1, §2A General conventions,
  §2B (`ScriptBundle` + `Scripts.Render` bullet, RULEBOOK.md:981-985), §3 (`btnet.runtime.web`
  list: `Scripts`, `BundleCollection`, `ScriptBundle`), §4, §5, §6.
- Inventory: `grep BundleConfig migration/inventory.tsv` returns no rows. The unit has
  no per-site decision (no ref/out, no nullable, no strings beyond literals, no SQL), so the
  missing row is not a defect: §2B's bullet decides the only construct.
- Runtime used by the target (to check compile-level call shapes, not to review):
  `port/runtime/.../web/BundleCollection.java` (`public synchronized void Add(ScriptBundle)`),
  `ScriptBundle.java` (`public ScriptBundle(String)`,
  `public ScriptBundle IncludeDirectory(String, String, boolean)`), `BundleTable.java`.
- Caller: `src/BugTracker.Web/Global.asax.cs:40-42` (`BundleConfig.RegisterBundles(BundleTable.Bundles)`).
- Not a page or control, so the judge contract and specs do not apply.

## Side-by-side check

| Item | Source | Target | Verdict |
|---|---|---|---|
| Namespace / package | `namespace btnet.App_Start` (:7) | `package btnet.App_Start;` (:2) | OK §2A namespace row, §4 |
| Type | `public class BundleConfig` (:9, non-static, implicit public no-arg ctor) | `public class BundleConfig` (:7, implicit public no-arg ctor) | OK. Not a `static class`, so the §2A `final` + private ctor row does not apply |
| Member | `public static void RegisterBundles(BundleCollection bundles)` (:11) | same signature (:8) | OK. Name, access, static kept (§2A, §4) |
| Body | `bundles.Add(new ScriptBundle("~/bundles/all").IncludeDirectory("~/Scripts/Site", "*.js", true));` (:13) | identical text (:9) | OK. Literals byte-identical, argument order kept, `true` → Java `true` |
| Runtime API | `System.Web.Optimization` | `btnet.runtime.web.BundleCollection`, `ScriptBundle` | OK. Both listed in §3; signatures exist exactly as called |
| Imports | `using System; …Linq; …Web; …Optimization;` (unused except Optimization) | two explicit single-type imports | OK §4 (no wildcards, no static imports) |
| Header | none | `// Source: src/BugTracker.Web/App_Start/BundleConfig.cs` line 1 | OK §6.1 [S1-41] |
| Comments to copy | none | none | OK §6.2 |
| Markers | none needed | none | OK §6.3 |
| Trailer | none | `// PORT STATUS: confidence=high todos=0` last line | OK §6.7; count 0 matches 0 `TODO(port)` markers |
| Scope | n/a | only the manifest target written, no template (core unit has none per §4) | OK §6.4 |
| Encoding | UTF-8 with BOM | ASCII, no BOM | OK §1 (UTF-8 text I/O); no content lost |

Behavior: the registration happens once, the bundle path, directory, pattern and recursion
flag reach the runtime unchanged, so `Scripts.Render("~/bundles/all")` (§2B) receives the same
bundle the .NET app registered. No exception, null, ordering, formatting or thread-safety
difference is introduced by the translated code itself.

## Findings

None. I looked for a defect at every point in the table above and did not find one.

(Checked and dismissed: in .NET, `IncludeDirectory` returns `Bundle` and `BundleCollection.Add`
takes `Bundle`, while the runtime models both with `ScriptBundle`. The translated expression
compiles against the runtime as written, and the target cannot fix this, so it is not a
finding against the unit. It is recorded under gaps.)

## Rulebook obedience

OBEDIENT. Deviations: 0.

- §0 core layer, structure-preserving: same type, same member, same name, same literals.
- §2A: package = namespace; non-static class kept non-final with its implicit constructor; no
  property, string-concat, or collection rules triggered.
- §3: only listed runtime types used; no `TODO(port): runtime` needed, and none was written.
- §4: explicit single-type imports; path matches `migration/manifest.tsv` row 2.
- §5: inventory grepped (no rows; none needed, so no `// inventory:` citation is owed).
- §6.1, §6.2, §6.3, §6.4, §6.7: header, completeness, markers, scope and trailer all as required.

## Rulebook gaps

1. **`BundleTable` missing from the §3 runtime list.** `port/runtime/.../web/BundleTable.java`
   exists and is what the caller needs (`Global.asax.cs:42`
   `BundleConfig.RegisterBundles(BundleTable.Bundles)`), but §3's `btnet.runtime.web` row
   lists only `Scripts`, `BundleCollection` and `ScriptBundle`. Under §3 ("Contents
   (exhaustive)"), a strict `Global.asax` reviewer could call
   `BundleTable.Bundles` an off-list runtime call. Proposed rule: add
   `BundleTable` (`Bundles`, static final `BundleCollection`) to the §3
   `btnet.runtime.web` row.
2. **No `Bundle` base type.** .NET's `IncludeDirectory`/`Include` return `Bundle` and
   `BundleCollection.Add` takes `Bundle`. The runtime uses `ScriptBundle` throughout. A
   translator who writes a declared `Bundle` local (not this unit, but any future use) has no
   rule to follow. Proposed rule: in §2B's bundle bullet, state that `System.Web.Optimization.Bundle`
   as a declared type is written `ScriptBundle` (the only bundle kind in `src/`).
3. **Bundle file order.** §2B (RULEBOOK.md:984) pins "ordinal path order". .NET's default
   `BundleCollection.FileSetOrderList`/`DefaultBundleOrderer` puts known files first (for
   example `reset.css`, `jquery*`) and does not sort purely ordinally. With today's
   `Scripts/Site` contents (`EditBug.js`, `Page.js`, `Util/…`, `Controls/…`) no known-name file
   is present, so it makes no difference now. Proposed: record the ordinal choice as accepted
   in D-006 (or a new deviation row), with that evidence, so a later reviewer of
   `LoggedIn.Master`/`Scripts` does not re-open it.
