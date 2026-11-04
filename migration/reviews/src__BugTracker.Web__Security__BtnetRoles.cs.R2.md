# Review R2 — src/BugTracker.Web/Security/BtnetRoles.cs

Target: `port/web/src/main/java/btnet/Security/BtnetRoles.java` (11 lines).
Inventory: no rows whose `file` column is `src/BugTracker.Web/Security/BtnetRoles.cs` (the only hit, `search.aspx` L23, references `BtnetRoles.Admin` and belongs to another unit). The unit has no sites that need an inventory decision (constants only), so the missing rows are not an inventory bug (§5).
Templates: none. §4 places templates only for markup units, and this unit is a plain `.cs` type.

## Findings

None.

Checked side by side, against the assumption that the translation is wrong:
- Path/package (§4, §2A "Namespace"): namespace `btnet.Security` → `package btnet.Security;` at `port/web/src/main/java/btnet/Security/BtnetRoles.java`. Correct, case preserved.
- Type (§2A access modifiers [S2-12]): `public class BtnetRoles` (BtnetRoles.cs:3) → `public class BtnetRoles` (java:4). The class is not static and not sealed, so no `final` and no private constructor. The implicit public no-arg constructor exists on both sides.
- Members (§2A `const` row [S1-39], which names `BtnetRoles.Admin` as its own example; §6.2 completeness): all four constants are present with identical names, casing, values and `public` access: `Admin`, `ProjectAdmin`, `Guest`, `User` (cs:5-8 → java:6-9). Each is `public static final String` with a literal initializer, so it is a compile-time constant and can be used in `@PageAuthorizeAttribute(BtnetRoles.Admin)` (§2A attribute row).
- Header (§6.1 [S1-41]): `// Source: src/BugTracker.Web/Security/BtnetRoles.cs` on line 1. The repo-relative path is correct.
- Trailer (§6.7): `// PORT STATUS: confidence=high todos=0` is the last line. The file has no `TODO(port)` markers, so the count is correct.
- Markers/comments (§6.2, §6.3): the source has no comments, and the target has no marker in the wrong format.
- Imports (§4): none needed, none present.
- Runtime API (§3): none used.
- Scope (§6.4): the unit wrote only the manifest target.

Severity counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

OBEDIENT. The implementer applied every rule this unit touches (§2A General conventions: names, namespace, `const`, access modifiers; §4 path/package/imports; §6.1 header, §6.2 completeness, §6.3 markers, §6.4 scope, §6.7 trailer). I found no deviations.

## Rulebook gaps

None. The §2A `const` row decides this unit, and that row cites `BtnetRoles.Admin` as its example.
