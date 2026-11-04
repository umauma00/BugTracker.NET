# Confirmed findings — src__BugTracker.Web__Models__status.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

None that affect this unit. (Observation only: §5 calls a site missing from the inventory "an inventory bug", but it does not say plainly that a unit decided entirely by general §2A rules is expected to have zero rows. This review read it that way. A one-line clarification in §5 would stop reviewers and implementers from flagging pure-POCO entity units such as `status.cs` and `priority.cs` as inventory bugs.)

## Counts

HIGH 0 / MEDIUM 0 / LOW 0 — Obedience: OBEDIENT

1. **Single-part `partial class`.** The §2A `partial class` row describes multi-part classes (pages+designer, `Startup`). It does not say explicitly that a `partial` class with only one part (all 23 EF entity files) is just a plain class. Every implementer will probably read it that way, but two agents could differ on whether to add a comment. Proposed rule: "a `partial` type with a single part translates as an ordinary class; no comment." Evidence: `Models/status.cs:6`, `Models/priority.cs`.
2. **Unused `using` directives.** No rule says that `using` directives with no Java counterpart are dropped silently, rather than translated to imports or kept as comments. Proposed rule: "`using` directives are not translated; imports are derived from the Java code (§4)." Evidence: `Models/status.cs:1-2`.
