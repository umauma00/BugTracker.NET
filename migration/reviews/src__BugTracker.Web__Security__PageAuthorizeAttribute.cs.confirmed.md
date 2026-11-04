# Confirmed findings — src__BugTracker.Web__Security__PageAuthorizeAttribute.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

1. **§2A attribute row is internally inconsistent about the call site.** It says "no cast at the call: `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))`" and in the same cell "`BasePage` builds one `Logic` per annotation (`new PageAuthorizeAttribute.Logic(a.value())`)"; the LINQ-to-objects row repeats the `a.OnAuthorize(...)` form. With `a` an annotation instance, `a.OnAuthorize` does not compile. Proposed: replace both examples with `attributes.stream().allMatch(a -> new PageAuthorizeAttribute.Logic(a.value()).OnAuthorize(Page.User))` (what inventory BasePage.cs L30 LAMBDA_DEFAULT already says). Affects the BasePage unit, not this one.
2. **Inventory conflict (rule wins, §5):** inventory row BasePage.cs L30 LINQ_STREAM evidence still reads "[review-corrected: Logic(String... roles), boolean OnAuthorize(ClaimsPrincipal user)]" (from the superseded C28 text), contradicting §2A [J-C16] `OnAuthorize(IPrincipal)`. This unit follows the rule correctly; the stale row should be rewritten to the [J-C16] form so the BasePage implementer is not misled.

- **G1 — §2A L146 contradicts itself about the call site.** It gives `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))` (and so does L175), where `a` would be the annotation and has no `OnAuthorize`. The same row says `BasePage` builds `new PageAuthorizeAttribute.Logic(a.value())`. Inventory rows 4/5 have the compilable form. Proposed fix: change the L146/L175 examples to `allMatch(a -> new PageAuthorizeAttribute.Logic(a.value()).OnAuthorize(Page.User))`. This matters for the BasePage unit, not this one.
- **G2 — inconsistent `Inherited = true` mapping between sibling attributes.** `PageAllowAnonymous` uses `@Inherited` (§3 L1030), while `PageAuthorizeAttribute` (same `Inherited = true`) does not, and §2A L146 says nothing either way. Both are equivalent under the L149 superclass walk. Proposed rule: "`Inherited = true` → no meta-annotation is needed (BasePage walks superclasses, L149); `@Inherited` is kept on `PageAllowAnonymous` only as §3 L1030 states." Alternatively, require `@Inherited` on both annotation and container. The PageAllowAnonymous review already raised this as G2.
- **G3 — stale inventory row.** `inventory.tsv` row 5 (`BasePage.cs` L30) still shows the withdrawn `ClaimsPrincipal` signature. Proposed: correct the row at the next gate so the BasePage implementer is not misled.
- **G4 — placement of §6.5 citations.** The rulebook doesn't say where the citation goes. Proposed: "place the citation on the line above the construct the row decides."
