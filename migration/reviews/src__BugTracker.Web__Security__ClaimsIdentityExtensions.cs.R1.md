# Review R1 — src/BugTracker.Web/Security/ClaimsIdentityExtensions.cs

Target: `port/web/src/main/java/btnet/Security/ClaimsIdentityExtensions.java` (manifest row 4).
Core-layer unit (§0, structure-preserving). No templates (§4: not a page/master/ascx), so no judge contract applies.
Inventory rows grepped: `inventory.tsv` row for `ClaimsIdentityExtensions.GetClaimsValue` L185 (FORMAT_SAME, confirmed); the BugFromEmailController.cs L115 row only references this unit (call-site concern, not this unit).

## What was checked (side by side)

- Member set: 29 public getters + `IsInRole` + private `GetClaimsValue` in source (cs:10-192) → all 31 present, same names, same order, same return types (`int`→`int`, `bool`→`boolean`, `string`→`String`), same claim-type constants per member (each checked against cs:12-156, incl. `GetEnablePopups`→`EnablePopUps`, `GetOtherOrgsPermissionLevels`→`OtherOrgsPermissionLevel`, `GetEmail`→`ClaimTypes.Email`). §6.2 satisfied.
- Static class → `public final class` + private ctor (§2A row "static class C", which names this unit). Extension methods → plain static methods with the receiver as first parameter (§2A extension-method row).
- `Convert.ToInt32`/`Convert.ToBoolean` → runtime `Convert` (§2A rows; §3 `btnet.runtime`); runtime `ToInt32(Object)`/`ToBoolean(Object)` exist and accept the `String` claim value.
- `claim.Value` → `Value()` (§2A runtime-property convention; runtime `Claim.Value()` exists).
- `identity is ClaimsIdentity` + cast → `instanceof` + cast; null identity → false → `SecurityException` in both languages (cs:174,188-190 vs java:145,155-156; cs:161,166-168 vs java:136-140).
- `FindFirst`/`HasClaim` semantics: runtime `ClaimsIdentity` keeps ordered list, first match, case-insensitive type, ordinal value (§3 [J-C3]) — matches .NET and preserves the §0.1 `Security.cs:73` shadowing quirk.
- Exception messages byte-identical: "Identity is not a valid Claims Identity" (cs:168,190), "Identity is missing value for claim type {0}" via `Strings.Format` (cs:185; §2A `string.Format` row; inventory L185). `btnet.runtime.SecurityException` is in the §3 exception list [C26] and is unchecked (extends `DotNetException extends RuntimeException`), so no `throws` clauses needed.
- Imports: explicit single-type imports only, all from §3 packages (`btnet.runtime`, `btnet.runtime.security`); `BtnetClaimTypes` is same-package (no import needed); no name clash (§4 [J-C15]); no static imports.
- §6.1 header `// Source: src/BugTracker.Web/Security/ClaimsIdentityExtensions.cs` correct; §6.5 inventory citation `// inventory: src/BugTracker.Web/Security/ClaimsIdentityExtensions.cs L185` in the correct format at the row's site; §6.7 trailer `// PORT STATUS: confidence=high todos=0` is the last line and `todos=0` matches (no `TODO(port)` markers; none warranted). Source has no comments to copy.
- No sanctioned F1–F5 fix touches this unit, and no quirk was "fixed". No SQL, output, redirect, or threading behavior in this unit.

## Findings

None.

(No HIGH, no MEDIUM, no LOW.)

## Rulebook obedience

OBEDIENT. Every applicable rule (§0 core posture; §2A static class, extension method, runtime property, `Convert.*`, `string.Format`, exceptions [C26]; §3 runtime API; §4 naming/package/imports/output path; §5 inventory grep; §6.1, 6.2, 6.3, 6.5, 6.7) was followed. No deviations found.

## Rulebook gaps

- G1: §2A has no row for the C# idiom `if (x is T) { T y = (T)x; … }`. The implementer used `instanceof` + explicit cast (java:136-137, 145-146); a Java 21 translator could equally write the pattern form `if (x instanceof T y)`. Both behave identically (null → false), so this is not a defect, but by the meta-rule ("if two agents could answer differently, the answer goes in this file") a one-line rule would remove the choice. Proposed: "`x is T` followed by `(T)x` → `x instanceof T` + explicit cast, structure kept (core-layer posture); the pattern form is permitted only where the source uses `x is T y`." Evidence: cs:161-163, 174-176.
