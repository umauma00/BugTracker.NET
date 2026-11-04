# Review R1: src/BugTracker.Web/Security/PermissionLevel.cs

Target: `port/web/src/main/java/btnet/Security/PermissionLevel.java` (11 lines)
Templates per RULEBOOK §4: none (core-layer `Security/` unit, §0; not a page, master or `.ascx`).
Inventory rows for this source path: none (`grep Security/PermissionLevel migration/inventory.tsv` returns nothing; the `PermissionLevel` hits are EF mapping rows for `OrganizationMap.cs`/`ProjectUserMap.cs` column names, unrelated to this unit).
Manifest row: `src/BugTracker.Web/Security/PermissionLevel.cs -> port/web/src/main/java/btnet/Security/PermissionLevel.java` (manifest.tsv:21).

## Checks performed

| Check | Rule | Result |
|---|---|---|
| Output path = manifest naming (`port/web/src/main/java/<namespace>/<PrimaryType>.java`) | §4 | OK |
| Package = C# namespace verbatim (`btnet.Security`) | §4, §2A Namespace row | OK (line 2) |
| Header `// Source: src/BugTracker.Web/Security/PermissionLevel.cs` as first line | §6.1 | OK (line 1; no BOM in target) |
| Type: `public class PermissionLevel` (non-static C# class, so the §2A `static class` row does not apply; implicit public default ctor matches C#) | §2A General, §6.2 | OK |
| `public const int None = 0;` (:5) → `public static final int None = 0;` (:6) | §2A `const` row, access kept | OK |
| `public const int ReadOnly = 1;` (:6) → `public static final int ReadOnly = 1;` (:7) | §2A `const` row | OK |
| `public const int Reporter = 3;` (:7) → `public static final int Reporter = 3;` (:8) | §2A `const` row | OK, value 3 kept |
| `public const int All = 2;` (:8) → `public static final int All = 2;` (:9) | §2A `const` row | OK, value 2 kept (non-monotonic order preserved, not "fixed") |
| Names and casing identical; no Java keyword collisions (`None`, `ReadOnly`, `Reporter`, `All`) | §4, §2A Type/member names | OK |
| Constant semantics: C# `const int` is a compile-time constant; Java `static final int` with a literal initializer is also a constant expression, so callers like `edit_bug.aspx.cs:197,237,328` (`==` comparisons, argument passing) and any `switch` case labels translate unchanged | §2A `const` row | OK |
| Completeness: 4 constants, no other members | §6.2 | OK |
| No source comments to copy | §6.2 | OK |
| Imports: none needed, none present | §4 | OK |
| Markers: none needed; `todos=0` matches zero `TODO(port)` | §6.3, §6.7 | OK |
| Trailer last line `// PORT STATUS: confidence=high todos=0` | §6.7 | OK |
| Scope: only the manifest target written | §6.4 | OK |
| Inventory citation: no inventory decision used, so no `// inventory:` comment required | §5, §6.5 | OK |
| Thread-safety: immutable constants only | n/a | OK |
| Sanctioned fixes F1–F5: none apply; no behavior altered | §0.1 | OK |

## Findings

None. I tried to find a defect and could not: each of the four `const int` declarations maps token for token onto the §2A `const` row with identical names, access modifiers and values (including the out-of-order `Reporter = 3`, `All = 2`), and every §6 obligation is met. Brace style matches the sibling `BtnetRoles.java` and is not a rule matter.

## Rulebook obedience

OBEDIENT. Rules checked: §0 (core posture: same type, members, names), §2A General conventions (namespace, `const`, access modifiers, names), §4 (path, package, imports), §5/§6.5 (inventory grep; no rows, none required for a constants class), §6.1 header, §6.2 completeness, §6.3 markers, §6.4 scope, §6.7 trailer. No deviation found.

## Rulebook gaps

None found for this unit. The §2A `const` row fully decides it.
