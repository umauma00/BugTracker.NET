# Review R1 — src/BugTracker.Web/Security/BtnetRoles.cs

Target: `port/web/src/main/java/btnet/Security/BtnetRoles.java` (manifest row `migration/manifest.tsv:12`). Core-layer unit (RULEBOOK §0, structure-preserving). No template (§4: not a page/master/.ascx/include), so the Judge contract does not apply directly.

Inputs read: RULEBOOK §0, §0.1, §1, §2A General conventions (incl. `const`, access modifiers, namespace, names), §4, §5, §6; inventory grep for the source path (no row for `Security/BtnetRoles.cs`; the only hit is `search.aspx` L23, which belongs to another unit); source `src/BugTracker.Web/Security/BtnetRoles.cs:1-10`; target `BtnetRoles.java:1-11`; call sites across `src/` (`Security.cs:113,119,122`, `[PageAuthorize(BtnetRoles.Admin)]` usages, `User.IsInRole(BtnetRoles.*)`).

## Findings

None.

Things I checked and found correct:

- §2A "Namespace `btnet.X`" / §4 "Java package = C# namespace verbatim": `package btnet.Security;` (target:2) matches `namespace btnet.Security` (source:1). Path matches manifest target.
- §2A "Type/member names" / §4 "Members keep C# names and casing": class `BtnetRoles`, members `Admin`, `ProjectAdmin`, `Guest`, `User`, same case. None of them is a Java keyword, so no `_` suffix is due.
- §2A `const T X = v` → `public static final T X = v;` (keep access modifier): all four constants are `public static final String` with byte-identical literals `"Admin"`, `"ProjectAdmin"`, `"Guest"`, `"User"` (source:5-8 → target:6-9). The rulebook cites `BtnetRoles.Admin` as this rule's own example.
- Compile-time constants: string literals in `static final` fields are constant expressions in Java, so the §2A row 146 annotation usage `@PageAuthorizeAttribute(BtnetRoles.Admin)` / `(BtnetRoles.Admin, BtnetRoles.ProjectAdmin)` (`categories.aspx.cs:9`) compiles.
- §2A access modifiers [S2-12]: the class is explicitly `public` in C# (source:3) and `public` in Java (target:4). It is not `static` in C#, so the `static class` → `final class` + private ctor row does not apply. The implicit public parameterless constructor exists in both languages.
- §6.2 completeness: all four constants are present. The source has no comments to copy.
- §6.1 header: `// Source: src/BugTracker.Web/Security/BtnetRoles.cs` is the first line, repo-relative [S1-41].
- §6.7 trailer: the last line is `// PORT STATUS: confidence=high todos=0`. There are 0 `TODO(port)` markers, so the count is correct.
- §6.3 markers: none present, none needed (there is no UNKNOWN site, no `BUG(port)`, no §0.1 fix in this unit).
- §6.5: no inventory row governs this unit, so no `// inventory:` citation is due.
- §4 imports: none needed, none present.
- §4 name clash [J-C15]: no runtime type named `BtnetRoles` exists (`grep "class BtnetRoles" port` → only this file).
- Encoding: the target is plain ASCII with no BOM carried over from the C# file's UTF-8 BOM (source:1), so javac will not reject it with an illegal `﻿`.
- §6.4 scope: only the manifest target exists for this unit.

## Rulebook obedience

OBEDIENT. I found no deviation from any §. Every applicable rule (§2A General conventions: names, namespace, `const`, access modifiers; §4 path/package/imports; §6.1, 6.2, 6.3, 6.5, 6.7) was followed as written.

## Rulebook gaps

None that affect this unit. One minor point, not a defect here: §6 does not say whether a source UTF-8 BOM must be stripped from the Java output. javac rejects a leading U+FEFF, and many `src/` files start with one (`BtnetRoles.cs:1`). Proposed rule for §6.1: "Java and template outputs are UTF-8 **without** BOM; a source BOM is never copied." Evidence: `src/BugTracker.Web/Security/BtnetRoles.cs` byte 0 is U+FEFF; this target is correctly BOM-free.
