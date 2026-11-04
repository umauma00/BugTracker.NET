# Review R2 — src/BugTracker.Web/Security/ClaimsIdentityExtensions.cs

Target: `port/web/src/main/java/btnet/Security/ClaimsIdentityExtensions.java` (161 lines). No template (not a page/control; RULEBOOK §4 places none).

Checked against: RULEBOOK §0.1, §2A (static class row, extension-method row, `string.Format` row, `Convert.ToInt32`/`Convert.ToBoolean` rows, Exceptions), §3 (`btnet.runtime` list incl. `SecurityException`, `Strings.Format`; `btnet.runtime.security` list; `ClaimsIdentity` [J-C3] semantics), §4 (package, imports, J-C15 name clash, home of shared types), §6 (header, completeness, markers, inventory citation, trailer); inventory row `ClaimsIdentityExtensions.GetClaimsValue L185` (FORMAT_SAME) and the cross-reference row `BugFromEmailController.cs L115`; runtime sources `Claim`, `ClaimsIdentity`, `IIdentity`, `ClaimTypes`, `SecurityException`, `Convert`, `Strings`; app `BtnetClaimTypes.java`.

## Findings

None.

Verification performed (each item attempted as a defect and refuted):

- **Completeness (§6.2):** all 30 public getters + `IsInRole` + private `GetClaimsValue` are present (31 Java `public static`/`private static` members vs 31 C# methods); names, casing, return types (`int`/`boolean`/`String`) and the claim-type constant used by each match source lines 10-157 one-for-one (checked every pairing, e.g. `GetEnablePopups` → `EnablePopUps`, `GetOtherOrgsPermissionLevels` → `OtherOrgsPermissionLevel`, `GetEmail` → `ClaimTypes.Email`). Every referenced `BtnetClaimTypes.*` constant exists in `btnet/Security/BtnetClaimTypes.java`.
- **Exception text and type:** messages at target :140, :153, :156 are byte-identical to source :168, :185, :190; all thrown as `btnet.runtime.SecurityException` (§2A Exceptions, §3 [C26]); `string.Format` → `Strings.Format` per §2A and inventory L185.
- **Control flow / null handling:** `is` → `instanceof` with the same branch order; a null identity throws "Identity is not a valid Claims Identity" in both (C# `is` is false for null). `FindFirst` returns the first match in insertion order (§3 J-C3), preserving the `Security.cs:73` shadowing noted in §0.1. `HasClaim` null role → `ArgumentNullException` in runtime as in .NET. `Claim.Value()` is never null (runtime ctor rejects null, as .NET).
- **Conversions:** `Convert.ToInt32(String)` / `Convert.ToBoolean(String)` route to runtime `(Object)` overloads whose String branch matches the C# `(string)` overloads (null → 0/false; `Int32.Parse`; trimmed case-insensitive True/False else `FormatException`).
- **Compile-level:** all imports are single-type, all types exist in §3-listed runtime packages with the used members (`FindFirst(String)`, `HasClaim(String,String)`, `Value()`, `ClaimTypes.Email/Role`); `BtnetClaimTypes` is same-package (no import needed); no J-C15 clash (no application type named `Convert`, `Strings`, `Claim`, `ClaimTypes`, `ClaimsIdentity`, `IIdentity`, `SecurityException` in the manifest).
- **Markers/header/trailer:** `// Source:` line correct (§6.1); inventory citation `// inventory: src/BugTracker.Web/Security/ClaimsIdentityExtensions.cs L185` in the §6.5 format; no `TODO(port)` markers, trailer `todos=0` is the last line (§6.7); no §0.1 `FIXED(port)` site applies and none is claimed.

## Rulebook obedience

**OBEDIENT.** No deviations found:
- §2A static class row: `public final class … { private …() {} }` — followed.
- §2A extension-method row: `this IIdentity identity` → plain first parameter of a static method — followed.
- §2A `string.Format` → `Strings.Format` (no `String.format`) — followed.
- §2A Exceptions / §3: `SecurityException` from `btnet.runtime` — followed.
- §4: package = C# namespace verbatim (`btnet.Security`); explicit single-type imports, no wildcard, no static imports; members keep C# names; no local copies of runtime/app types — followed.
- §5/§6.5: inventory grepped and cited for the one row that exists — followed.
- §6.1/§6.2/§6.3/§6.7 — followed.

## Rulebook gaps

- **G1 — `Convert.To*(string)` overloads are not named.** §2A rows are titled `Convert.ToInt32(object)` / `Convert.ToBoolean(o)`; C# here binds the `string` overloads (source :12-156, `GetClaimsValue` returns `string`). Semantics coincide today, but .NET `Convert.ToInt32(string)` parses with `CurrentCulture` while the runtime `ToInt32(Object)` uses `CultureInfo.EN_US`. Proposed rule: "the `string` overloads of `Convert.ToInt32/ToInt64/ToBoolean/ToDecimal/ToDouble` map to the same runtime `(Object)` call; current culture is en-US throughout (§3 `CultureInfo`)". Evidence: `ClaimsIdentityExtensions.cs:12`, runtime `Convert.java:155-157`. Low impact (claim values are invariant-formatted integers/`True`/`False` written by `Security.cs`).
