# Review R2 — src/BugTracker.Web/Security/PageAuthorizeAttribute.cs

Target: `port/web/src/main/java/btnet/Security/PageAuthorizeAttribute.java` (no template; not a page/control, so judge contract and specs do not apply).
Inputs read: RULEBOOK §2A (L145 `params`, L146 attribute row, L149 GetCustomAttributes row, L175 LINQ), §3 (L1026-1030 page authorization, L1088 `btnet.runtime.security`, L1205-1207 `IPrincipal` [J-C16]), §4, §5, §6; inventory rows 434, 435 (this unit) and 4, 5 (consumer `BasePage.cs`); runtime `btnet/runtime/security/IPrincipal.java`, `IIdentity.java`.

## Findings

NONE

Checked and found correct (each against the cited source/rule):

- Shape (§2A L146): `@Retention(RUNTIME) @Target(TYPE)` annotation, `String[] value() default {}`, `@Repeatable` container `PageAuthorizeAttributes` (RUNTIME, TYPE, `PageAuthorizeAttribute[] value()`); nested container referenced as `PageAuthorizeAttribute.PageAuthorizeAttributes.class` is legal Java. Matches `AttributeUsage(AllowMultiple = true)` (cs:7).
- `Logic` (§2A L146 [C28, J-C16]): `public static final class Logic`; `private final String[] _roles` (cs:10, readonly, name kept per §4); `Logic()` → `new String[0]` (cs:12-15); `Logic(String... roles)` → `_roles = roles` (cs:17-20, §2A L145 varargs); `public boolean OnAuthorize(IPrincipal principal)` (cs:23).
- Body (cs:25-26; inventory rows 434/435; §2A L175 `Any`→`anyMatch`): `principal.Identity().IsAuthenticated() && (_roles.length == 0 || Arrays.stream(_roles).anyMatch(role -> principal.IsInRole(role)))`. Same short-circuit order; null principal → NPE as .NET NRE; empty roles → true; `Identity()`/`IsAuthenticated()`/`IsInRole(String)` exist in the runtime (`IPrincipal.java`, `IIdentity.java`, §3 L1205).
- Overload choice: `new Logic()` picks the non-varargs constructor in Java as C# picks the parameterless one; `new Logic(a.value())` passes the array directly (inventory row 4). `value()` returns a fresh array per call, so `_roles` aliasing is harmless.
- `Inherited = true` (cs:7): no `@Inherited`. The rulebook's L146 row does not list it for this annotation, and L149 requires `BasePage` to walk every superclass with `getDeclaredAnnotationsByType`, which ignores `@Inherited`. Behavior is the same, and the target comment (java:13-15) explains it.
- §6.1 header `// Source: src/BugTracker.Web/Security/PageAuthorizeAttribute.cs` is correct; §6.7 trailer is the last line, `todos=0` matches (no `TODO(port)` markers); §6.5 inventory citation is present in the format `// inventory: src/BugTracker.Web/Security/PageAuthorizeAttribute.cs L26`; §4 imports are explicit single-type, with no wildcards or static imports; no other units edited (§6.4).
- §6.2 completeness: every member is present (field, 2 constructors, method). The source has no comments to copy. The `[AttributeUsage]` line is kept as a comment, which is harmless.

## Rulebook obedience

**OBEDIENT.** No deviation from §2A, §3, §4, §5 or §6 found. Notes (not deviations):
- The §6.5 inventory citation sits above the annotation declaration (java:16), next to the `Inherited` comment, not at `OnAuthorize` (java:44-46) where rows 434/435 apply. §6.5 does not say where the citation goes.
- Inventory row 5 still carries `[review-corrected: … boolean OnAuthorize(ClaimsPrincipal user)]`, which disagrees with §2A L146 / §3 L1205 [J-C16] (`IPrincipal`). The implementer followed the rule, which §5 says wins over an inventory row.

## Rulebook gaps

- **G1 — §2A L146 contradicts itself about the call site.** It gives `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))` (and so does L175), where `a` would be the annotation and has no `OnAuthorize`. The same row says `BasePage` builds `new PageAuthorizeAttribute.Logic(a.value())`. Inventory rows 4/5 have the compilable form. Proposed fix: change the L146/L175 examples to `allMatch(a -> new PageAuthorizeAttribute.Logic(a.value()).OnAuthorize(Page.User))`. This matters for the BasePage unit, not this one.
- **G2 — inconsistent `Inherited = true` mapping between sibling attributes.** `PageAllowAnonymous` uses `@Inherited` (§3 L1030), while `PageAuthorizeAttribute` (same `Inherited = true`) does not, and §2A L146 says nothing either way. Both are equivalent under the L149 superclass walk. Proposed rule: "`Inherited = true` → no meta-annotation is needed (BasePage walks superclasses, L149); `@Inherited` is kept on `PageAllowAnonymous` only as §3 L1030 states." Alternatively, require `@Inherited` on both annotation and container. The PageAllowAnonymous review already raised this as G2.
- **G3 — stale inventory row.** `inventory.tsv` row 5 (`BasePage.cs` L30) still shows the withdrawn `ClaimsPrincipal` signature. Proposed: correct the row at the next gate so the BasePage implementer is not misled.
- **G4 — placement of §6.5 citations.** The rulebook doesn't say where the citation goes. Proposed: "place the citation on the line above the construct the row decides."
