# Review R2: src/BugTracker.Web/Security/PermissionLevel.cs

Target: `port/web/src/main/java/btnet/Security/PermissionLevel.java` (manifest row 21).
Reviewer: R2 (adversarial, read-only). Date: 2026-11-04.

## Inputs read

- Source: `src/BugTracker.Web/Security/PermissionLevel.cs:1-10`
- Target: `port/web/src/main/java/btnet/Security/PermissionLevel.java:1-11`
- RULEBOOK §0 (core layer, `Security/` structure-preserving), §0.1, §2A `const` row
  (RULEBOOK.md:134), §2A access-modifier row (RULEBOOK.md:136), §4, §5, §6.
- Inventory: `grep "Security/PermissionLevel" migration/inventory.tsv` returns no rows
  (the `PermissionLevel` hits are `OrganizationMap.cs`/`ProjectUserMap.cs` EF property
  names, unrelated). The unit has no per-site decision (no SQL, ref/out, nullable,
  strings, regions), so the missing row is not an inventory bug.
- Callers checked for usage shape: `src/BugTracker.Web/edit_bug.aspx.cs:197,237,328,334,341`
  (`PermissionLevel.All/None/ReadOnly` compared with `==` against `int`, passed as `int`).
- Not a page or control: judge contract and specs do not apply.

## Side-by-side check

| Item | Source | Target | Verdict |
|---|---|---|---|
| Namespace / package | `namespace btnet.Security` (:1) | `package btnet.Security;` (:2) | OK §4 (namespace verbatim) |
| Type | `public class PermissionLevel` (:3), non-static, implicit public ctor | `public class PermissionLevel` (:4), implicit public ctor | OK. Not a `static class`; no `final`/private-ctor obligation |
| `None` | `public const int None = 0;` (:5) | `public static final int None = 0;` (:6) | OK §2A const row |
| `ReadOnly` | `public const int ReadOnly = 1;` (:6) | `public static final int ReadOnly = 1;` (:7) | OK |
| `Reporter` | `public const int Reporter = 3;` (:7) | `public static final int Reporter = 3;` (:8) | OK, value 3 kept |
| `All` | `public const int All = 2;` (:8) | `public static final int All = 2;` (:9) | OK, value 2 kept (non-monotonic quirk preserved, not "fixed") |
| Member order | None, ReadOnly, Reporter, All | same | OK |
| Names / casing | as source | as source; no Java-keyword collisions | OK §4 |
| Semantics | C# `const` = compile-time constant (inlined, usable in `switch case`) | Java `static final int` with constant initializer = compile-time constant (inlined, usable in `case`) | OK, equivalent |
| Imports | none | none | OK §4 |
| Header | — | `// Source: src/BugTracker.Web/Security/PermissionLevel.cs` line 1 | OK §6.1 [S1-41] |
| Comments to copy | none | none | OK §6.2 |
| Markers | none needed | none | OK §6.3 |
| Trailer | — | `// PORT STATUS: confidence=high todos=0` last line | OK §6.7; 0 `TODO(port)` markers, count matches |
| Scope | — | only the manifest target; no template (core unit) | OK §6.4 |
| Encoding | UTF-8 with BOM | ASCII, no BOM, trailing newline | OK §1 |

## Findings

None. I looked specifically for: altered constant values or order, `const` mapped to
an enum or non-final field, a dropped/added member, a wrong package, a missing header
or trailer, and a "fixed" value ordering (All=2 < Reporter=3). None present.

## Rulebook obedience

OBEDIENT. §2A const row, §4 package/naming, §6.1 header, §6.2 completeness, §6.7
trailer all followed; no deviations found.

## Rulebook gaps

None for this unit.
