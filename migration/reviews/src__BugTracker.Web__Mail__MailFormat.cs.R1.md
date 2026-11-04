# Review R1 — src/BugTracker.Web/Mail/MailFormat.cs

Target: `port/web/src/main/java/btnet/Mail/MailFormat.java` (manifest.tsv row 16; no template — core unit, §0).

Inputs read: `migration/RULEBOOK.md` (§0, §0.1, §2A enum row, §3 runtime list, §4, §5, §6), `migration/inventory.tsv` (grep for `MailFormat`: no rows), source, target, consumers (`Mail/email.cs:38,51,123`, `send_email.aspx.cs:540-586`, `forgot.aspx.cs:136`, `register.aspx.cs:84`, `btnet/bug.cs:1239`).

## Findings

None.

Checks performed against the assumption that the translation is wrong:

- Path/package: `btnet.Mail.MailFormat` = C# namespace `btnet.Mail` verbatim and the manifest target (§4). OK.
- Members: `Text`, `Html` in source order with source casing (§4 "Members keep C# names"; §6.2 completeness). Ordinal order preserved (Text=0, Html=1), so any `ordinal()`/default-value use matches the C# underlying values. OK.
- Plain (non-`[Flags]`) enum stays a Java `enum` (§2A `[Flags]` row [J-C5]). OK.
- Name clash [J-C15]: no runtime type named `MailFormat` exists in `btnet.runtime.*` (§3 `btnet.runtime.mail` lists `SmtpClient`, `MailMessage`, `MailAddress`, `Attachment`, `MailPriority` only; grep of `port/` finds no other `MailFormat`). No qualification issue. OK.
- Header `// Source: src/BugTracker.Web/Mail/MailFormat.cs` on line 1 (§6.1 [S1-41]). OK.
- Trailer `// PORT STATUS: confidence=high todos=0` is the last line; file has 0 `TODO(port)` markers, so no undercount (§6.7). OK.
- No source comments to copy, no SQL, no messages, no markers required (§6.2, §6.3). The C# trailing `;` after the enum body (`};`, source line 7) is an empty declaration with no semantics; dropping it is correct.
- Inventory: no rows for this unit; none required (no ref params, nullable columns, regions) — not an inventory bug (§5).
- Compile-level by reading: valid Java 21 enum; no imports needed. OK.

## Rulebook obedience

OBEDIENT. No deviations from §0, §2A, §4, §6 found. Brace placement (Allman) is not governed by any rule and is not a finding.

## Rulebook gaps

None material for this unit. (Minor, informational: §2A states plain .NET enums "stay Java enums" only via the framework examples `SqlDbType`/`MailPriority`; application enums are covered implicitly by §0 "one Java class, same type name, same members". No two agents would plausibly answer differently, so no amendment proposed.)

## Counts

HIGH 0 / MEDIUM 0 / LOW 0 — OBEDIENT
