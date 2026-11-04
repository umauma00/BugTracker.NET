# Review R1 — src/BugTracker.Web/Security/PageAllowAnonymous.cs

Target: `port/web/src/main/java/btnet/Security/PageAllowAnonymous.java`
Reviewer: R1 (adversarial, read-only)

## Inputs read

- Source `src/BugTracker.Web/Security/PageAllowAnonymous.cs` (9 lines: marker attribute,
  `[AttributeUsage(AttributeTargets.Class, Inherited = true, AllowMultiple = false)]`, empty body).
- Target (22 lines).
- RULEBOOK §2A (Attribute class + usage row, L146; `Attribute.GetCustomAttributes` row, L149),
  §2B B7 (L1011-1030), §4 (naming/paths/packages/imports), §5, §6 (per-file obligations).
- Inventory: one row mentions the type (`src/BugTracker.Web/BasePage.cs` L23, LINQ_STREAM,
  confirmed); no row is keyed on the unit's own source path.
- Manifest row 13: source → `port/web/src/main/java/btnet/Security/PageAllowAnonymous.java` (path matches).
- Usage sites in source: `default.aspx.cs:9`, `forgot.aspx.cs:9`, consumer `BasePage.cs:23`.
- Not a page/control: no template, judge contract not applicable (no ids/names/URLs).

## Findings

None.

Checks performed, all passed:

| Check | Source | Target | Rule | Result |
|---|---|---|---|---|
| Kind | `class PageAllowAnonymous : Attribute` (cs:6) | `public @interface PageAllowAnonymous` (java:19) | §2A L146 | ok |
| Retention | runtime-visible via `GetCustomAttributes` (BasePage.cs:23) | `@Retention(RetentionPolicy.RUNTIME)` (java:17) | §2A L146 | ok |
| Target | `AttributeTargets.Class` (cs:5) | `@Target(ElementType.TYPE)` (java:18) | §2A L146 | ok |
| Inherited | `Inherited = true` (cs:5) | `@Inherited` (java:16) | §2B B7 L1030 | ok |
| AllowMultiple | `false` (cs:5) | not `@Repeatable` (java:19) | §2A L146 (Repeatable only for PageAuthorizeAttribute) | ok |
| Logic class | attribute has no members/logic (cs:7-8) | no nested `Logic` | §2A L146 ("logic of the C# attribute moves to `Logic`" — there is none) | ok |
| Members | implicit public ctor only; no `new PageAllowAnonymous()` anywhere in source | none | §6.2 | ok (annotation is the mandated form) |
| Package / path | `namespace btnet.Security` (cs:3) | `package btnet.Security;` (java:2), path per manifest | §4 | ok |
| Imports | — | explicit single-type `java.lang.annotation.*` imports, all used | §4 | ok |
| Header | — | `// Source: src/BugTracker.Web/Security/PageAllowAnonymous.cs` (java:1) | §6.1 | ok |
| Inventory cite | row `src/BugTracker.Web/BasePage.cs` L23 | `// inventory: src/BugTracker.Web/BasePage.cs L23` (java:11) | §6.5 format | ok |
| Markers | — | none; no non-§3 TODO styles | §6.3 | ok |
| Trailer | — | `// PORT STATUS: confidence=high todos=0` last line (java:22); 0 `TODO(port)` in file | §6.7 | ok |
| Scope | — | only the manifest target exists for this unit; no extra files | §4, §6.4 | ok |
| Compile by reading | — | valid annotation declaration; no runtime API used | §3 | ok |

Behavior: the consumer (`BasePage`, other unit) walks every superclass with
`getDeclaredAnnotationsByType` per §2A L149, so `@Inherited` has no effect on
the result there; the annotation's meta-annotations give the walk what it needs
(RUNTIME retention, TYPE target). `default` and `forgot` pages still resolve
`hasAnonymous == true`, matching `BasePage.cs:23`.

## Rulebook obedience

OBEDIENT. Every applicable rule (§2A L146, §2B B7, §4, §6.1/6.3/6.5/6.7) was followed.

One addition the rulebook does not mention: `@Documented` (java:4, java:15). It
is not required by §2A L146 or B7, but no rule forbids it, and it has no runtime
effect (it only controls Javadoc output, and reflection lookups do not see it).
This is not a deviation from a stated rule. It is listed under gaps below so the
human can decide.

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
