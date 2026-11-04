# Confirmed findings — src__BugTracker.Web__App_Start__BundleConfig.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

- G1 (informational, low priority): §2B's `ScriptBundle` bullet (RULEBOOK.md ~L981-985) specifies rendering but not the translation of `BundleConfig.cs` itself or of the caller chain `Global.asax.cs:36-42` (`RegisterBundles()` -> `BundleConfig.RegisterBundles(BundleTable.Bundles)`). `BundleTable` exists in the runtime (`port/runtime/src/main/java/btnet/runtime/web/BundleTable.java`) but is **not listed** in §3's `btnet.runtime.web` row (RULEBOOK.md L1086), which lists `BundleCollection` and `ScriptBundle` only. The `Global` translator needs `BundleTable.Bundles`; a strict reviewer of `Global.java` could flag it as "runtime API not in §3". Proposed: add `BundleTable` (`Bundles` static field) to the §3 `btnet.runtime.web` list, and add to the §2B bullet: "`Global.asax.cs:42` `BundleConfig.RegisterBundles(BundleTable.Bundles)` -> same call over runtime `BundleTable.Bundles`." Evidence: `src/BugTracker.Web/Global.asax.cs:40-42`; `BundleTable.java` javadoc.
- G2 (informational): the runtime `ScriptBundle` javadoc cites "RULEBOOK §2B B6" for unbundled rendering, but the bullet is not labelled B6 in §2B as I read it (the bullet at ~L981 is unlabelled). Not a defect in this unit; the label should be confirmed or the runtime comment corrected by the runtime author.

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
