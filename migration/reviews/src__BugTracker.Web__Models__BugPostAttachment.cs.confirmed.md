# Confirmed findings — src__BugTracker.Web__Models__BugPostAttachment.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

None found for this unit. A minor observation, not a gap: §2A does not say explicitly that unused C# `using` directives produce no Java import. §4's "explicit single-type imports" rule implies it, and the existing Models translations (e.g. `BugPost.java`) are consistent with that reading.

- G1 (minor, no effect here): the §2A `partial class` row (:133) is written for multi-part partials (pages + designer, `Startup`). It does not explicitly state that a single-file `partial` entity (all 23 `Models/*.cs` are `public partial class`, EF6 power-tools output) simply drops the modifier. Implementers did the evident thing, but a one-line rule would close it. Proposed addition to the row: "A `partial` class with only one part → plain class; the modifier is dropped." Evidence: `Models/BugPostAttachment.cs:6`, `Models/BugPost.cs`.
- G2 (minor): no rule states that C# `using` directives with no referenced type produce no Java import (§4 covers imports only by positive form). Proposed: "`using` directives are not translated; imports are derived from types actually referenced." Evidence: `Models/BugPostAttachment.cs:1-2`.
