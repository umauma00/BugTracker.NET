# Review R2 — src/BugTracker.Web/Security/BtnetClaimTypes.cs

Target: `port/web/src/main/java/btnet/Security/BtnetClaimTypes.java`
Reviewer: R2 (adversarial, read-only). Date: 2026-11-04.

## What was checked

- Source `src/BugTracker.Web/Security/BtnetClaimTypes.cs:1-38` against target `BtnetClaimTypes.java:1-40`, line by line.
- RULEBOOK §0, §0.1, §2A General conventions (`Namespace`, `static class`, `const`, `Access modifiers` [S2-12], type/member names), §4 (naming/output path, package, imports), §5 (inventory), §6 (per-file obligations 1–7).
- `migration/inventory.tsv`: **no rows** for this source path. That is correct: the unit has no ref parameters, nullable columns, SQL, regions or quirks for the inventory to decide (§5).
- `migration/manifest.tsv:3`: target path `port/web/src/main/java/btnet/Security/BtnetClaimTypes.java` matches the file written (§4).
- Consumers (`Security/Security.cs:43-108`, `Security/ClaimsIdentityExtensions.cs:12-111`) only reference the constants by name. Nothing in this unit depends on the consumers.
- Templates: none. This is a core-layer unit with no markup (§0, §4).

## Findings

None.

Verification detail:
- **Member set:** all 29 `public const string` members are present, in source order, with the same names and casing (§2A Type/member names, §6.2).
- **Values:** I extracted every `Name = "literal"` pair from both files mechanically and diffed them. They are identical, including the duplicate value `"og_can_edit_and_delete_posts"` shared by `CanEditAndDeleteBugs` (`.cs:16`) and `CanEditAndDeletePosts` (`.cs:22`). The duplicate is preserved, not "fixed" (§0, §0.1).
- **`const` mapping:** `public const string X = v` → `public static final String X = v;`, with the access modifier kept (§2A `const` row [S1-39]). Java treats a `static final String` with a literal initializer as a compile-time constant, so call sites inline it just as C# `const` does. No semantic drift.
- **Class declaration:** the source is `public class` (not `static class`), so the §2A `static class` row (final + private constructor) does not apply. The target `public class BtnetClaimTypes` with Java's implicit public no-arg constructor matches C#'s implicit public default constructor.
- **Package:** `package btnet.Security;` = namespace `btnet.Security` verbatim (§2A Namespace, §4).
- **Imports:** none needed, none present (§4).
- **Compile-level:** no type errors, missing imports or runtime API calls are visible by reading.
- **Header (§6.1):** line 1 is `// Source: src/BugTracker.Web/Security/BtnetClaimTypes.cs`, the correct repo-relative path [S1-41].
- **Source comments (§6.2):** the source has none, and none are missing.
- **Markers (§6.3):** none required (no BUG/PERF/TODO/UNKNOWN sites, no §0.1 site), and none present.
- **Scope (§6.4):** only the manifest target was written.
- **Inventory citations (§6.5):** none are needed because there are no inventory rows.
- **Status trailer (§6.7):** the last line is `// PORT STATUS: confidence=high todos=0`. `todos=0` is correct (0 `TODO(port)` markers in the file).

Severity counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

**OBEDIENT.** There are no deviations. Each applicable rule was followed: §2A Namespace, `const` [S1-39], Access modifiers [S2-12] (explicit `public` kept on the type and every member), Type/member names; §4 path, package and imports; §5 inventory (none applicable); §6.1 header, §6.2 completeness, §6.3 markers, §6.4 scope and §6.7 trailer. No §0.1 fix was applied, and none was applicable.

## Rulebook gaps

None that block this unit. One observation, with no change proposed: the duplicate claim-type value `"og_can_edit_and_delete_posts"` (`BtnetClaimTypes.cs:16` and `:22`) makes `Security.cs:52` and `:58` add two claims of the same type. Reads go through `FindFirst` (`ClaimsIdentityExtensions.cs`), so the effect is the same as the `Security.cs:73` shadowing case that §0.1 "Not fixed" already covers via the runtime's ordered claim list [J-C3]. Both claims come from the same column (`og_can_edit_and_delete_posts`), so they always agree and nothing diverges. The rulebook could optionally add this pair to the §0.1 "Not fixed" examples so that the reviewers of `Security.cs` do not flag it.
