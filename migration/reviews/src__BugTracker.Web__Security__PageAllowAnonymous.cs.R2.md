# Review R2 — src/BugTracker.Web/Security/PageAllowAnonymous.cs

Target: `port/web/src/main/java/btnet/Security/PageAllowAnonymous.java` (manifest row 13; no template, the unit is not a page/control).

Read: RULEBOOK §0, §2A (attribute row L146, GetCustomAttributes row L149), §2B B7 (L1011-1030), §4, §5, §6; inventory row `src/BugTracker.Web/BasePage.cs L23` (the only row mentioning the type; no row for the unit's own path); usages `default.aspx.cs:10`, `forgot.aspx.cs:9`, `BasePage.cs:23`; judge `S1.md:16`, `S1_LoginTest.java:47`, `S9_SearchTest.java:40`.

Source (`PageAllowAnonymous.cs:5-8`): an empty attribute class, `AttributeTargets.Class`, `Inherited = true`, `AllowMultiple = false`, no members, no declared constructor.

## Findings

None.

What I checked, assuming it was wrong:

- **Shape (§2A L146):** Java annotation with `@Retention(RUNTIME)` and `@Target(TYPE)`. Present (target :17-18). Retention is RUNTIME, so `getDeclaredAnnotationsByType` in the translated `BasePage` will see it (BasePage.cs:23).
- **`Logic` nested class (§2A L146):** the rule moves the attribute's *logic* to `Logic`. This attribute has no logic, no fields and no declared constructor, so leaving out `Logic` is correct. No caller does `new PageAllowAnonymous()` (grep over `src/`).
- **`Inherited = true` (§2B B7 L1029-1030):** B7 asks for `@Inherited`. Present (:16).
- **`AllowMultiple = false`:** not `@Repeatable`, which is correct. In both languages, applying it twice is a compile error.
- **`AttributeTargets.Class` → `ElementType.TYPE`:** TYPE also allows interfaces/enums/annotations. Nothing in the source applies it to anything except a class, so behavior doesn't change.
- **Package/path (§4):** namespace `btnet.Security` → package `btnet.Security`, path `port/web/src/main/java/btnet/Security/PageAllowAnonymous.java`, which matches the manifest. Name is unchanged.
- **Imports (§4):** explicit single-type `java.lang.annotation.*` imports, no wildcard, no static imports.
- **Header (§6.1):** `// Source: src/BugTracker.Web/Security/PageAllowAnonymous.cs` is on line 1.
- **Completeness (§6.2):** the source has no members and no comments, so nothing is missing.
- **Markers / trailer (§6.3, §6.7):** last line is `// PORT STATUS: confidence=high todos=0` (newline-terminated). The file has 0 `TODO(port)` markers, so the count is right.
- **Inventory citation (§6.5):** `// inventory: src/BugTracker.Web/BasePage.cs L23` is in the format §6.5 requires (file column + `L<n>` prefix). The row exists and is `confirmed`.
- **Compile by reading:** `public @interface PageAllowAnonymous {}` with the four meta-annotations is valid Java 21. No runtime API is used.
- **Judge:** S1 needs anonymous access to `default.aspx`/`forgot.aspx` and redirects for every other BasePage. That depends on this annotation being visible at runtime and not being inherited by accident. Here it is runtime-visible, and only classes that carry it (or subclass one) are exempt, matching the C# `Inherited = true`.

## Rulebook obedience

**OBEDIENT.** No deviations.

Not a deviation, noted for completeness: `@Documented` (:15) is not listed in §2A L146. It only affects javadoc and has no runtime or judge effect. The rule names the required meta-annotations and does not forbid others, so I don't count it as a deviation.

## Rulebook gaps

- **G1 (minor):** §2A L146 doesn't say whether an attribute with **no** logic still gets an empty nested `Logic` class, or whether extra meta-annotations (`@Documented`) are allowed. Evidence: `PageAllowAnonymous.cs:6-8` (empty body) and target :15. Proposed rule: "An attribute class with no members translates to an annotation with no `Logic` nested class. Meta-annotations are exactly `@Retention(RUNTIME) @Target(TYPE)`, plus `@Inherited` when `Inherited = true` and `@Repeatable` when `AllowMultiple = true`. No others."
- **G2 (minor):** §2A L146 sets `@Target(TYPE)` for every attribute. It doesn't map `AttributeUsage(Inherited = …)` / `AllowMultiple` in general; only B7 (`@Inherited` for this attribute) and the `PageAuthorizeAttribute` `@Repeatable` text cover the two existing cases. Proposed rule: add the general mapping `Inherited=true → @Inherited`, `AllowMultiple=true → @Repeatable(<Name>s)`.
