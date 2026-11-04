# Review R2 — src/BugTracker.Web/Mail/MailPriority.cs

Target: `port/web/src/main/java/btnet/Mail/MailPriority.java`
Manifest row: `migration/manifest.tsv:17` (path matches §4 naming rule: module `port/web`, namespace `btnet.Mail` → `btnet/Mail/`, primary type `MailPriority`).
Inventory: no rows for `src/BugTracker.Web/Mail/MailPriority.cs` (`grep` of `migration/inventory.tsv` empty). The unit has no ref parameters, nullable columns, code regions or postback events, so a missing row is not an inventory bug (§5).
Templates: none — plain `.cs` unit, not a page/control (§4); judge contract not applicable.

## Checks performed (all passed)

| Check | Source | Target | Result |
|---|---|---|---|
| Package = C# namespace verbatim (§4) | `MailPriority.cs:1` `namespace btnet.Mail` | `MailPriority.java:2` `package btnet.Mail;` | OK |
| Plain (non-flags) enum stays Java `enum` (§2A row J-C5, line 173; §3 line 1219) | `MailPriority.cs:3` `public enum MailPriority` | `MailPriority.java:4` `public enum MailPriority` | OK |
| Member names, casing, declaration order (§4 "Members keep C# names and casing"; §6.2 completeness) | `:5-7` `Normal, Low, High` | `:6-8` `Normal, Low, High` | OK — order preserved, so `ordinal()` = .NET underlying value (0,1,2) should any caller need it |
| Name clash J-C15 (§4) | app enum vs `System.Net.Mail.MailPriority` | app enum declared in `btnet.Mail`; runtime enum is `btnet.runtime.mail.MailPriority` and is not referenced or imported here | OK — the clash obligation falls on callers (`email.cs:142-147`), not this unit |
| No imports needed / no wildcard imports (§4) | — | none | OK |
| Header (§6.1) | — | `:1` `// Source: src/BugTracker.Web/Mail/MailPriority.cs` | OK (repo-relative, exact path) |
| Status trailer last line, todos count (§6.7) | — | `:11` `// PORT STATUS: confidence=high todos=0`; 0 `TODO(port)` markers in file | OK |
| Markers (§6.3) | no source comments/TODOs | none | OK |
| Scope (§6.4): one Java file, no second top-level type | — | single top-level type | OK |
| Compile-level by reading | — | valid Java 21 enum; trailing C# `};` correctly dropped to `}` | OK |

Usages cross-checked for behavioral need on this unit (no extra members required): `email.cs:39,60,124,142-147`, `send_email.aspx.cs:541-552` — all use only `==` comparison and member assignment, which a Java enum supports identically.

## Findings

None.

## Rulebook obedience

OBEDIENT. Every applicable rule was followed: §4 (path, package, member names, J-C15 not violated), §2A J-C5 row / §3 (plain enum stays `enum`), §5 (no inventory decisions needed; none cited, correctly), §6.1 header, §6.2 completeness (all three members, order preserved), §6.3 markers (none), §6.4 scope, §6.7 trailer with accurate `todos=0`. No deviations found.

## Rulebook gaps

G1 — **Application-declared C# enums have no general rule.** The rulebook decides only the two named cases (`SqlDbType`, `MailPriority`) by name (§2A line 173, §3 line 1219) and the J-C15 clash (§4). It does not decide, for any app enum (`MailPriority`, `MailFormat`, `email.cs:320 AddrType`, `version_control.cs:167 State`, `POP3Main.cs:24 service_state`):
  - zero-value default: an unassigned C# enum field/array element is the first member (value 0); a Java enum field defaults to `null`, so a field read before assignment would NPE / compare unequal instead of equalling `Normal`;
  - integer casts `(int)e` / `(E)n` and `ToString()` / `Enum.Parse` (→ `ordinal()`/`values()[n]`/`name()`/`valueOf`, with .NET's out-of-range-int tolerance and case-insensitive-parse overload not reproduced);
  - explicit underlying values (`A = 5`) — `ordinal()` would then be wrong.
  Proposed rule (§2A): "A non-flags C# enum declared by the application → Java `enum` with the same members in the same order. A field, array element or `default(E)` of enum type with no explicit initializer is initialized to the member with underlying value 0 (`E.values()[0]` when values are implicit). `(int)e` → `e.ordinal()` and `(E)n` → `E.values()[n]` only when no member has an explicit value; otherwise the enum carries `private final int value` + `int value()` + `static E fromValue(int)`. `e.ToString()` → `e.name()`." Evidence: this unit has no such usage today (callers only use `==`, `email.cs:142-147`; local `send_email.aspx.cs:541` is definitely assigned in all switch branches), so the gap is latent for MailPriority but affects other enum-typed fields elsewhere in the codebase.
