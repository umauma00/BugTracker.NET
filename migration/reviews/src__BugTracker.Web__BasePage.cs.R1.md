# Review R1 — src/BugTracker.Web/BasePage.cs

Target: `port/web/src/main/java/btnet/BasePage.java` (core unit, §0; no template — BasePage has no markup, §4 places none).
Inputs read: RULEBOOK.md (§0, §0.1, §1, §2A rows 143-185/596-611 and Exceptions, §2B B1-B3/B7, §3 IPrincipal, §3.1, §4, §5, §6), inventory.tsv rows 3-7 (all rows for this file), source, translation, `Security/PageAuthorizeAttribute.cs` + `PageAllowAnonymous.cs` and their Java units (cross-unit contract), runtime `Page`, `HttpResponse.Redirect`, `HttpContext.User()`, `IPrincipal`, `HttpUtility.UrlEncode`, `Strings.Format`, judge specs S1/S4/S6/S9 (returnUrl handles).

## Findings

None.

Checked and found faithful (no finding):

- `OnPreInit` (BasePage.cs:12-19 → BasePage.java:17-24): `protected` + `@Override` of runtime `Page.OnPreInit(EventArgs)` (Page.java:121); single-argument `Response.Redirect(url)` → runtime `Redirect(url, true)` (HttpResponse.java:304) throws `RedirectException`, so `super.OnPreInit(e)` and the rest of the lifecycle do not run, as in .NET (ThreadAbortException); no enclosing try (§2A Exceptions, B2 steps 3/8, B3 Redirect row; inventory L16 REDIRECT_END).
- URL text: `Strings.Format("default.aspx?returnUrl={0}", HttpUtility.UrlEncode(Request.RawUrl()))` — `string.Format` → `Strings.Format` (§2A row 185), `HttpUtility.UrlEncode` lower-case hex (§2A row 610), `Request.RawUrl()` (§2A row 602); matches B7 and judge S1 test 9, S4:106, S6:28, S9:61. Inventory L16 FORMAT_SAME.
- `IsUserAuthorized` (BasePage.cs:21-31 → BasePage.java:26-48): `private boolean`; anonymous check walks `getClass()` and every superclass with `getDeclaredAnnotationsByType(PageAllowAnonymous.class)` (§2A row 149; inventory L23). `.Any()` = non-empty; `PageAllowAnonymous` is AllowMultiple=false, so .NET returning only the most-derived instance makes no difference to `Any()`.
- Authorize collection: walks all superclasses, derived first, collecting every `PageAuthorizeAttribute` (repeatable container unwrapped by `getDeclaredAnnotationsByType`) — matches .NET `GetCustomAttributes(inherit=true)` for an `AllowMultiple = true, Inherited = true` attribute (PageAuthorizeAttribute.cs:7), so an admin page needs User **and** Admin (§2A row 149; inventory L30 LINQ_STREAM). `.Cast<T>()` is a no-op on the typed list.
- `Page.User.Identity().IsAuthenticated() && attributes.stream().allMatch(a -> new PageAuthorizeAttribute.Logic(a.value()).OnAuthorize(Page.User))`: short-circuit order kept; `allMatch` on empty = true like `All` [C4]; one `Logic` per annotation with `Logic(String...)` and `OnAuthorize(IPrincipal)`, no cast (§2A row 146 [C28, J-C16]; §3 IPrincipal; inventory L30 LAMBDA_DEFAULT). `Page` resolves to the inherited instance field `public Page Page = this` (Page.java:84; JLS 6.5.2 prefers the variable over the type), `User` is `IPrincipal` (Page.java:81), never null (HttpContext.User() returns an anonymous `ClaimsPrincipal`) — same as OWIN.
- Class annotation `@PageAuthorizeAttribute(BtnetRoles.User)` from `[PageAuthorize(BtnetRoles.User)]` (§2A row 146; `BtnetRoles.User` is a `static final String` constant, valid annotation element); `extends Page` (runtime `btnet.runtime.web.Page`, B1).
- Compile-level by reading: all imports exist (§3.1: `btnet.runtime.EventArgs`, `Strings`, `btnet.runtime.web.HttpUtility`, `Page`; app types `btnet.Security.*`); explicit single-type imports, no wildcard/static imports (§4); types and signatures match the runtime and the `PageAuthorizeAttribute` unit.
- §6: header line 1 `// Source: src/BugTracker.Web/BasePage.cs` (item 1); every member translated (item 2); no source comments to copy; inventory citations `// inventory: src/BugTracker.Web/BasePage.cs L16/L23/L30` in the §6.5 format, one per decision site (item 5); trailer is the last line, `todos=0` and the file has zero `TODO(port)` markers (item 7); no quirk "fixed" (§0.1 lists nothing for this unit).

## Rulebook obedience

OBEDIENT. No deviations found: §2A rows 146/149/175/185/602/610, B2 step 3, B3 Redirect row, B7 page-authorization bullet, §4 imports/naming, §5 inventory grep, and §6 items 1-7 are all followed. Where the rulebook contradicts itself (gap 1 below) the implementer followed the specific, compilable rule (§2A row 146), which is the correct choice.

## Rulebook gaps

1. **§2A row 175 example contradicts row 146.** Row 175's example renders `BasePage.cs:30` as `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))`, but `a` is a Java annotation instance with no `OnAuthorize` (would not compile); row 146 says BasePage builds `new PageAuthorizeAttribute.Logic(a.value())`. Two agents could answer differently. Proposed amendment: change row 175's example to `attributes.stream().allMatch(a -> new PageAuthorizeAttribute.Logic(a.value()).OnAuthorize(Page.User))`; same for row 146's parenthetical "no cast at the call: `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))`".
2. **Inventory row conflicts with the rule (§5 "a conflict is a finding for the human").** inventory.tsv row for `BasePage.cs` L30 LINQ_STREAM carries `[review-corrected: Logic(String... roles), boolean OnAuthorize(ClaimsPrincipal user)]`, while the L30 LAMBDA_DEFAULT row and §2A row 146 [J-C16] say `OnAuthorize(IPrincipal principal)`. The translation follows the rule (rule rows win). Proposed: rewrite the L30 LINQ_STREAM evidence to `OnAuthorize(IPrincipal principal)` per J-C16.
3. **B7 lists `BasePage.cs` as a site of `[PageAllowAnonymous]`.** BasePage.cs only *reads* the attribute (:23); it does not carry it (carrying it would make every page anonymous). Proposed wording: "`[PageAllowAnonymous]` (read in `BasePage.cs:23`; applied in `default.aspx.cs`, `forgot.aspx.cs`)" so no implementer adds `@PageAllowAnonymous` to BasePage.
