# Confirmed findings — src__BugTracker.Web__BasePage.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

1. **§2A row 175 example contradicts row 146.** Row 175's example renders `BasePage.cs:30` as `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))`, but `a` is a Java annotation instance with no `OnAuthorize` (would not compile); row 146 says BasePage builds `new PageAuthorizeAttribute.Logic(a.value())`. Two agents could answer differently. Proposed amendment: change row 175's example to `attributes.stream().allMatch(a -> new PageAuthorizeAttribute.Logic(a.value()).OnAuthorize(Page.User))`; same for row 146's parenthetical "no cast at the call: `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))`".
2. **Inventory row conflicts with the rule (§5 "a conflict is a finding for the human").** inventory.tsv row for `BasePage.cs` L30 LINQ_STREAM carries `[review-corrected: Logic(String... roles), boolean OnAuthorize(ClaimsPrincipal user)]`, while the L30 LAMBDA_DEFAULT row and §2A row 146 [J-C16] say `OnAuthorize(IPrincipal principal)`. The translation follows the rule (rule rows win). Proposed: rewrite the L30 LINQ_STREAM evidence to `OnAuthorize(IPrincipal principal)` per J-C16.
3. **B7 lists `BasePage.cs` as a site of `[PageAllowAnonymous]`.** BasePage.cs only *reads* the attribute (:23); it does not carry it (carrying it would make every page anonymous). Proposed wording: "`[PageAllowAnonymous]` (read in `BasePage.cs:23`; applied in `default.aspx.cs`, `forgot.aspx.cs`)" so no implementer adds `@PageAllowAnonymous` to BasePage.

1. **§2A internal contradiction about the call shape.** The attribute row (RULEBOOK.md:146) says both
   "no cast at the call: `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))`" and "`BasePage` builds
   one `Logic` per annotation (`new PageAuthorizeAttribute.Logic(a.value())`)". The LINQ row (RULEBOOK.md:175)
   repeats `a -> a.OnAuthorize(Page.User)` as the `BasePage.cs:30` example. Taken literally that cannot compile:
   `a` is an annotation instance with no `OnAuthorize`. The implementer followed the BasePage-specific clause and
   inventory L30 (row 4), which is the only compilable reading. Proposed amendment: change both examples to
   `attributes.stream().allMatch(a -> new PageAuthorizeAttribute.Logic(a.value()).OnAuthorize(Page.User))`.
2. **Stale inventory evidence for L30 (LINQ_STREAM row).** Its evidence column still says
   "[review-corrected: Logic(String... roles), boolean OnAuthorize(ClaimsPrincipal user)]", which J-C16 overrides
   (IPrincipal). The sibling LAMBDA_DEFAULT row for the same line records J-C16. Proposed: rewrite the stale
   evidence to `OnAuthorize(IPrincipal principal)` [J-C16] so a future implementer or fixer does not reintroduce
   a `ClaimsPrincipal` cast.
3. **§2B B7 attribution list (RULEBOOK.md:1029)** names `BasePage.cs` as a `[PageAllowAnonymous]` *usage* site.
   BasePage.cs only *reads* the attribute (line 23); it is not annotated with it. This does no harm here, but an
   implementer reading B7 literally could add `@PageAllowAnonymous` to `BasePage`, which would disable all page
   authorization. Proposed: reword to "read in `BasePage.cs:23`; applied in `default.aspx.cs`, `forgot.aspx.cs`".
