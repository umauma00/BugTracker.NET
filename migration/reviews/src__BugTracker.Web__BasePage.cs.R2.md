# Review R2 — src/BugTracker.Web/BasePage.cs

Target: `port/web/src/main/java/btnet/BasePage.java` (no template: core unit, RULEBOOK §0 / §4).
Read: RULEBOOK §0, §2A (attribute, GetCustomAttributes, LINQ, Strings rows), §2B B1/B2/B7, §3/§3.1, §4, §6;
inventory rows for `src/BugTracker.Web/BasePage.cs` (L16 x2, L23, L30 x2); `judge/specs/S1.md:16,73-77`,
`S4.md:106`, `S6.md:28,58-59`, `S9.md:61`; runtime `Page.java`, `HttpResponse.java`, `HttpRequest.java`,
`HttpUtility.java`, `Strings.java`, `EventArgs.java`, `IPrincipal`/`IIdentity`; translated
`btnet/Security/PageAuthorizeAttribute.java`, `PageAllowAnonymous.java`, `BtnetRoles.java`.

## Findings

None. Each candidate defect was checked and rejected:

- Redirect flow (BasePage.cs:14-18 / BasePage.java:19-23): 1-arg `Response.Redirect(url)` → runtime
  `Redirect(url, true)` (HttpResponse.java:304-315) sets 302 + "Object moved" body and ends by exception,
  so `super.OnPreInit(e)` and the rest of the lifecycle do not run, as with .NET's ThreadAbortException.
  Matches inventory L16 (REDIRECT_END) and §2B B2 step 3 / B7.
- URL text: `Strings.Format("default.aspx?returnUrl={0}", HttpUtility.UrlEncode(Request.RawUrl()))`.
  Literal is byte-identical. `RawUrl()` is path+query as sent (HttpRequest.java:392). `UrlEncode` uses
  lower-case hex with space → `+` (HttpUtility.java:207). Matches inventory L16 (FORMAT_SAME), §2A Strings,
  and judge S1 test 9 / S4:106 / S6:28.
- Anonymous check (BasePage.cs:23 / .java:28-37): walks `getClass()` and every superclass with
  `getDeclaredAnnotationsByType`. That matches inherit=true (§2A GetCustomAttributes row, inventory L23).
  `@PageAllowAnonymous` has RUNTIME retention.
- Role check (BasePage.cs:29-30 / .java:40-47): same walk, collecting `@PageAuthorizeAttribute` including
  `@Repeatable`-container occurrences (getDeclaredAnnotationsByType resolves the container). The class's own
  `@PageAuthorizeAttribute(BtnetRoles.User)` is included, and so are the subclass's (Admin pages need both,
  §2A row). `IsAuthenticated() && allMatch(...)` keeps the short-circuit order, and empty → true as with `All`.
  One `Logic(a.value())` per annotation follows §2A attribute row [C28, J-C16]. `Logic(String...)` with an
  empty array behaves like `Logic()` (empty `_roles`) → same result as the C# 0-arg constructor.
- Compile-level: `Page.User` resolves to the inherited public field `Page` (variable obscures the imported
  type, JLS 6.4.2), whose `User` is `IPrincipal` (Page.java:81,84). `Identity().IsAuthenticated()` exists
  (IPrincipal/IIdentity). `Logic.OnAuthorize(IPrincipal)` exists (PageAuthorizeAttribute.java:44), so no cast is
  needed. `BtnetRoles.User` is a `static final String` constant, which is legal as an annotation value. All
  imports are single-type and present in §3.1 or app units.
- Members/visibility: `protected` override `OnPreInit`, `private boolean IsUserAuthorized()`, nothing missing
  (§6.2). Null handling: the C# NRE on null `User` becomes an NPE in Java. Both surface as an unhandled
  exception, so behavior is equivalent.
- Ordering: the walk lists attributes derived-class first. C# order is unspecified, and `All` over side-effect-free
  predicates is order-independent.

Counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

OBEDIENT. Checked:
- §6.1 header `// Source: src/BugTracker.Web/BasePage.cs`: present.
- §6.7 trailer `// PORT STATUS: confidence=high todos=0`: present as the last line. There are 0 `TODO(port)`
  markers, so the count is correct.
- §6.5 inventory citations: `// inventory: src/BugTracker.Web/BasePage.cs L16`, `L23` and `L30` cite every
  inventory line used. The two L16 rows and two L30 rows each share one comment, which the citation format allows.
- §4: package `btnet`, class name = file stem, explicit single-type imports, no wildcard, no static imports.
- §2A: attribute row, GetCustomAttributes row, LINQ `All`→`allMatch`, `string.Format`→`Strings.Format`
  (no `String.format`).
- §2B B1/B7: extends runtime `btnet.runtime.web.Page`, authorization stays in `OnPreInit` exactly as source.
- §6.4: only the manifest target was written. No template exists or is required.
- §6.3: no non-§3 markers. No quirks were "fixed" (none of F1–F5 touch this unit).

## Rulebook gaps

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
