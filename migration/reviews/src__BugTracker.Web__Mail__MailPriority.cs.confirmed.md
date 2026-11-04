# Confirmed findings — src__BugTracker.Web__Mail__MailPriority.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

None that affect this unit. A minor note: the rulebook does not say whether app enums may rely on `ordinal()` to stand in for .NET underlying integer values. If a future consumer casts `(int)priority`, the mapping would need a rule. Proposed rule: "an `(int)` cast of a plain app enum without explicit values → `e.ordinal()`; an enum with explicit values gets an `int value` field and a `value()` accessor." Evidence: no such cast exists today. I grepped `MailPriority` across `src/` and found only name comparisons.

G1 — **Application-declared C# enums have no general rule.** The rulebook decides only the two named cases (`SqlDbType`, `MailPriority`) by name (§2A line 173, §3 line 1219) and the J-C15 clash (§4). It does not decide, for any app enum (`MailPriority`, `MailFormat`, `email.cs:320 AddrType`, `version_control.cs:167 State`, `POP3Main.cs:24 service_state`):
  - zero-value default: an unassigned C# enum field/array element is the first member (value 0); a Java enum field defaults to `null`, so a field read before assignment would NPE / compare unequal instead of equalling `Normal`;
  - integer casts `(int)e` / `(E)n` and `ToString()` / `Enum.Parse` (→ `ordinal()`/`values()[n]`/`name()`/`valueOf`, with .NET's out-of-range-int tolerance and case-insensitive-parse overload not reproduced);
  - explicit underlying values (`A = 5`) — `ordinal()` would then be wrong.
  Proposed rule (§2A): "A non-flags C# enum declared by the application → Java `enum` with the same members in the same order. A field, array element or `default(E)` of enum type with no explicit initializer is initialized to the member with underlying value 0 (`E.values()[0]` when values are implicit). `(int)e` → `e.ordinal()` and `(E)n` → `E.values()[n]` only when no member has an explicit value; otherwise the enum carries `private final int value` + `int value()` + `static E fromValue(int)`. `e.ToString()` → `e.name()`." Evidence: this unit has no such usage today (callers only use `==`, `email.cs:142-147`; local `send_email.aspx.cs:541` is definitely assigned in all switch branches), so the gap is latent for MailPriority but affects other enum-typed fields elsewhere in the codebase.
