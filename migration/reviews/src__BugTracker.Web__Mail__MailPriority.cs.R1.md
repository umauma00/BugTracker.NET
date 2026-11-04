# Review R1 — src/BugTracker.Web/Mail/MailPriority.cs

Target: `port/web/src/main/java/btnet/Mail/MailPriority.java`
(manifest.tsv row 17; no template, since this is not a page or control).

## Evidence read

- Source `src/BugTracker.Web/Mail/MailPriority.cs:1-9`: `namespace btnet.Mail { public enum MailPriority { Normal, Low, High }; }`.
- Target `MailPriority.java:1-11`.
- Inventory: `grep MailPriority migration/inventory.tsv` gives no rows. The unit has no sites (no ref/out, nullable, regions, or SQL), so a missing row is not an inventory bug (§5).
- RULEBOOK §2A row [J-C5] (line 173): plain .NET and app enums "stay Java `enum`s". §4 (path, package, name clash [J-C15]). §6 items 1-7.
- Consumers: `Mail/email.cs:39,60,124,142-147` and `send_email.aspx.cs:541-552` only use the constant names and `==` comparison. No numeric cast, `ToString`, or `Enum.Parse` was found, so the constant names and their order (ordinal 0/1/2 = .NET values 0/1/2) are all that matters for behavior.

## Findings

None.

Checked and found correct:
- Path and package: `btnet.Mail` matches the C# namespace verbatim (§4), and the path matches manifest row 17.
- Members: `Normal`, `Low`, `High` appear in source order with C# casing (§4 "Members keep C# names"; §6.2 completeness). Declaration order is kept, so ordinals equal the .NET underlying values.
- Visibility: `public`, as in the source.
- The C# trailing `};` was dropped. That is legal in either form and has no effect on behavior.
- The file declares no imports and has no name clash with `btnet.runtime.mail.MailPriority`, which exists on disk (§3.1). Consumers must write the runtime one fully qualified per [J-C15]. That is the job of `email.cs`, not this unit.
- Header: line 1 `// Source: src/BugTracker.Web/Mail/MailPriority.cs` (§6.1, [S1-41]).
- Trailer: the last line is `// PORT STATUS: confidence=high todos=0`. The count is correct because the file has 0 `TODO(port)` markers (§6.7).
- The source has no comments to copy (§6.2), and the file has no stray marker styles (§6.3).

Counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

The implementer followed the rulebook. I found no deviations.
- §2A [J-C5]: the type stays a Java `enum`. It was not turned into an int-constant class.
- §4: package, path, member names, a single top-level type, and no imports are all correct.
- §6.1 header, §6.2 completeness, §6.3 markers, §6.4 scope (only the target written), §6.5 (no inventory rows apply, so no `// inventory:` citation is needed), and §6.7 trailer all follow the rules.

Verdict: **OBEDIENT**.

## Rulebook gaps

None that affect this unit. A minor note: the rulebook does not say whether app enums may rely on `ordinal()` to stand in for .NET underlying integer values. If a future consumer casts `(int)priority`, the mapping would need a rule. Proposed rule: "an `(int)` cast of a plain app enum without explicit values → `e.ordinal()`; an enum with explicit values gets an `int value` field and a `value()` accessor." Evidence: no such cast exists today. I grepped `MailPriority` across `src/` and found only name comparisons.
