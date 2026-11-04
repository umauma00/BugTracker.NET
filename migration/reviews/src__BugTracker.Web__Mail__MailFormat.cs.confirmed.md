# Confirmed findings — src__BugTracker.Web__Mail__MailFormat.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

None material for this unit. (Minor, informational: §2A states plain .NET enums "stay Java enums" only via the framework examples `SqlDbType`/`MailPriority`; application enums are covered implicitly by §0 "one Java class, same type name, same members". No two agents would plausibly answer differently, so no amendment proposed.)

## Counts

HIGH 0 / MEDIUM 0 / LOW 0 — OBEDIENT

- **G1: application-declared C# enums.** The §2A J-C5 row decides only the *.NET framework* enums (flags enums become int-constant classes; `SqlDbType` and `MailPriority` (runtime) stay Java enums). It never says that a plain enum declared in the application translates to a Java `enum` with the same members in the same order. Two implementers could still differ, for example one might emit int constants to keep `(int)` casts and `default(T)` = 0. Proposed rule for §2A: "A non-`[Flags]` enum declared in `src/` → Java `enum` with the same members in the same order. `(int)e` → `e.ordinal()` only when the enum has no explicit values; with explicit values, add a `private final int value` and a `value()` accessor. `[Flags]` application enums follow J-C5." Evidence: `Mail/MailFormat.cs`, `Mail/MailPriority.cs`, and the J-C5 row's wording "Plain (non-flags) .NET enums".
- **G2: default value of enum-typed fields.** A C# enum field or array element defaults to its zero member (`MailFormat.Text`). In Java it defaults to `null`, so a later `==` comparison or `switch` behaves differently (`switch` on `null` throws NPE). This unit has no such use (`send_email.aspx.cs:540` is a definitely-assigned local), but the rulebook does not decide the case. Proposed rule: "An enum-typed field or array element with no initializer is initialized explicitly to the member with value 0." Evidence: C# spec §9.3 (default values of enum types are 0), and the absence of any §2A row on enum defaults.
