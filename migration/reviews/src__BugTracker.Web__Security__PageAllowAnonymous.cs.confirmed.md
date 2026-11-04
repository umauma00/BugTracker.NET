# Confirmed findings — src__BugTracker.Web__Security__PageAllowAnonymous.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

- **G1 — extra meta-annotations on translated attributes.** §2A L146 lists the
  meta-annotations to use (`@Retention(RUNTIME) @Target(TYPE)`, plus
  `@Repeatable`/`@Inherited` where stated) but does not say whether others
  (`@Documented`) may be added. Evidence: java:15 adds `@Documented`. The
  sibling `PageAuthorizeAttribute` has not been translated yet, so the two
  attribute units might come out inconsistent. Proposed rule: "Translated
  attribute annotations carry exactly the meta-annotations listed here; no
  `@Documented`." Or, if the human prefers, allow `@Documented` on all of them.
- **G2 — inventory coverage of marker attributes.** The unit has no inventory
  row of its own. The implementer correctly cited the consumer row
  (`BasePage.cs` L23), whose evidence names B7 `@Inherited`. §5 says a missing
  site is an inventory bug, but this unit has no translation decision site of
  its own (empty body). Proposed clarification: "units with no decision sites
  need no inventory row; cite a consumer row only when it decides the unit's form."

- **G1 (minor):** §2A L146 doesn't say whether an attribute with **no** logic still gets an empty nested `Logic` class, or whether extra meta-annotations (`@Documented`) are allowed. Evidence: `PageAllowAnonymous.cs:6-8` (empty body) and target :15. Proposed rule: "An attribute class with no members translates to an annotation with no `Logic` nested class. Meta-annotations are exactly `@Retention(RUNTIME) @Target(TYPE)`, plus `@Inherited` when `Inherited = true` and `@Repeatable` when `AllowMultiple = true`. No others."
- **G2 (minor):** §2A L146 sets `@Target(TYPE)` for every attribute. It doesn't map `AttributeUsage(Inherited = …)` / `AllowMultiple` in general; only B7 (`@Inherited` for this attribute) and the `PageAuthorizeAttribute` `@Repeatable` text cover the two existing cases. Proposed rule: add the general mapping `Inherited=true → @Inherited`, `AllowMultiple=true → @Repeatable(<Name>s)`.
