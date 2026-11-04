# Review R2 — src/BugTracker.Web/Mail/MailFormat.cs

Target: `port/web/src/main/java/btnet/Mail/MailFormat.java` (manifest row `migration/manifest.tsv:16`).
No templates apply (core-layer unit, RULEBOOK §0, §4).
Inventory: `grep MailFormat migration/inventory.tsv` returns no rows. That is expected because the unit has no ref parameters, nullable columns or code regions, so no row is missing (§5).

## Comparison

| Source (MailFormat.cs) | Target (MailFormat.java) | Verdict |
|---|---|---|
| L1 `namespace btnet.Mail` | L2 `package btnet.Mail;` | matches §4 (package = namespace verbatim) |
| L3 `public enum MailFormat` | L4 `public enum MailFormat` | matches; a plain non-flags enum stays a Java `enum` (§2A row J-C5, by analogy) |
| L5-6 `Text, Html` (ordinals 0, 1) | L6-7 `Text, Html` (same order, same casing) | matches §4 "Members keep C# names and casing" |
| L7 `};` (redundant C# terminator) | L8 `}` | no semantic content |
| — | L1 `// Source: src/BugTracker.Web/Mail/MailFormat.cs` | matches §6.1 |
| — | L10 `// PORT STATUS: confidence=high todos=0` (last line) | matches §6.7; there are 0 `TODO(port)` markers, so `todos=0` is correct |

Uses I checked for semantic dependence on the enum's representation (`email.cs:38,51,123,168,417`, `send_email.aspx.cs:540-586`, `forgot.aspx.cs:136`, `register.aspx.cs:84`, `bug.cs:1239`): the code only assigns members, passes them as parameters, and compares with `==`. It never casts to int, calls `ToString()`, parses, or relies on `default(MailFormat)`. Java `enum` `==` gives the same behavior.

## Findings

None.

## Rulebook obedience

OBEDIENT. I checked §4 (path, package, names, a single top-level type, no imports needed), §6.1 header, §6.2 completeness (both members are present, and the source has no comments to copy), §6.3 markers (none), §6.4 scope (one file), §6.5 inventory (no rows, so no citation is required), and §6.7 trailer. The style matches the sibling unit `MailPriority.java`. No deviations.

## Rulebook gaps

- **G1: application-declared C# enums.** The §2A J-C5 row decides only the *.NET framework* enums (flags enums become int-constant classes; `SqlDbType` and `MailPriority` (runtime) stay Java enums). It never says that a plain enum declared in the application translates to a Java `enum` with the same members in the same order. Two implementers could still differ, for example one might emit int constants to keep `(int)` casts and `default(T)` = 0. Proposed rule for §2A: "A non-`[Flags]` enum declared in `src/` → Java `enum` with the same members in the same order. `(int)e` → `e.ordinal()` only when the enum has no explicit values; with explicit values, add a `private final int value` and a `value()` accessor. `[Flags]` application enums follow J-C5." Evidence: `Mail/MailFormat.cs`, `Mail/MailPriority.cs`, and the J-C5 row's wording "Plain (non-flags) .NET enums".
- **G2: default value of enum-typed fields.** A C# enum field or array element defaults to its zero member (`MailFormat.Text`). In Java it defaults to `null`, so a later `==` comparison or `switch` behaves differently (`switch` on `null` throws NPE). This unit has no such use (`send_email.aspx.cs:540` is a definitely-assigned local), but the rulebook does not decide the case. Proposed rule: "An enum-typed field or array element with no initializer is initialized explicitly to the member with value 0." Evidence: C# spec §9.3 (default values of enum types are 0), and the absence of any §2A row on enum defaults.
