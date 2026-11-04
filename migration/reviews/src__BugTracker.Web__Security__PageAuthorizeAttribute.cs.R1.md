# Review R1 — src/BugTracker.Web/Security/PageAuthorizeAttribute.cs

Target: `port/web/src/main/java/btnet/Security/PageAuthorizeAttribute.java`
(no template: not a page/control, so the judge contract does not apply; §4 places no template for this unit).

Inputs read: RULEBOOK §2A (General conventions: attribute row, `params`, `readonly`, access modifiers, LINQ-to-objects, `GetCustomAttributes`), §3 security package + [J-C16] semantics, §4, §6; inventory rows L26 (x2) for this file and BasePage.cs L30 (x2); `rulebook-conflicts.md` C28; runtime `btnet.runtime.security.IPrincipal` / `IIdentity`.

## Findings

None.

Checked and found correct (assuming the translation is wrong, each was verified against source and rule):

- `_roles`: `private readonly string[]` -> `private final String[]` (§2A `readonly`, access modifiers [S2-12]).
- Both constructors kept: `Logic()` -> `new String[0]` (source :12-15), `Logic(String... roles)` stores the array as-is, aliasing and a `null` array behave as in C# (source :17-20; §2A `params`, attribute row [C28, J-C16]). `new Logic()` resolves to the no-arg constructor (JLS phase 1 precedes varargs), as C# overload resolution does.
- `OnAuthorize(IPrincipal principal)`: signature per §2A attribute row / [J-C16]; `principal.Identity.IsAuthenticated` -> `principal.Identity().IsAuthenticated()` matches runtime `IPrincipal`/`IIdentity` (both exist with those members). `&&`/`||` short-circuit order preserved; `Any(pred)` -> `anyMatch` (§2A LINQ-to-objects); empty-roles -> true via the `length == 0` guard, as source :26. Null `principal` -> NPE vs NRE, both unhandled, same outcome.
- Annotation shape: `@Retention(RUNTIME) @Target(TYPE)`, `String[] value() default {}`, `@Repeatable` container `PageAuthorizeAttributes` with matching retention/target (valid per JLS 9.6.3); `@Inherited` correctly not used (inheritance handled by BasePage's superclass walk, §2A `GetCustomAttributes` row). Nested `public static final class Logic` is legal in an annotation interface.
- Imports explicit single-type (§4); package `btnet.Security` (§4); header `// Source:` (§6.1); `// inventory: ... L26` citation (§6.5); trailer is the last line, `todos=0` matches zero `TODO(port)` markers (§6.7). No source comments to copy.

Counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

OBEDIENT. Every applicable rule (§2A attribute row incl. the [C28, J-C16] `Logic` signature, `params`, `readonly`, access modifiers, LINQ `Any`; §3 runtime types only; §4 naming/imports/nesting; §6 header, inventory citation, trailer) was followed. No deviations found.

## Rulebook gaps

1. **§2A attribute row is internally inconsistent about the call site.** It says "no cast at the call: `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))`" and in the same cell "`BasePage` builds one `Logic` per annotation (`new PageAuthorizeAttribute.Logic(a.value())`)"; the LINQ-to-objects row repeats the `a.OnAuthorize(...)` form. With `a` an annotation instance, `a.OnAuthorize` does not compile. Proposed: replace both examples with `attributes.stream().allMatch(a -> new PageAuthorizeAttribute.Logic(a.value()).OnAuthorize(Page.User))` (what inventory BasePage.cs L30 LAMBDA_DEFAULT already says). Affects the BasePage unit, not this one.
2. **Inventory conflict (rule wins, §5):** inventory row BasePage.cs L30 LINQ_STREAM evidence still reads "[review-corrected: Logic(String... roles), boolean OnAuthorize(ClaimsPrincipal user)]" (from the superseded C28 text), contradicting §2A [J-C16] `OnAuthorize(IPrincipal)`. This unit follows the rule correctly; the stale row should be rewritten to the [J-C16] form so the BasePage implementer is not misled.
