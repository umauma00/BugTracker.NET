# Review R2 — src/BugTracker.Web/Models/BugPostAttachment.cs

Target: `port/web/src/main/java/btnet/Models/BugPostAttachment.java` (manifest.tsv row 33; path matches RULEBOOK §4).
No templates (entity class, not a page/control); judge contract not applicable.

## Inputs read
- Source `src/BugTracker.Web/Models/BugPostAttachment.cs:1-12`
- Target `port/web/src/main/java/btnet/Models/BugPostAttachment.java:1-10`
- RULEBOOK §2A (General conventions, auto-property row :137, `partial class` row :133, type table :163, EF6 entities :556-557), §4, §6
- inventory.tsv: no rows for `Models/BugPostAttachment.cs` (rows 88-92 belong to `Models/Mapping/BugPostAttachmentMap.cs`; they reference fields `Id`, `PostId`, `Content` by name — all present with exact names)
- Runtime `btnet/runtime/data/DbSet.java:56,252` (public fields via `getFields()`, no-arg constructor via `getDeclaredConstructor()`) — the target satisfies both (implicit public no-arg constructor, public fields)

## Line-by-line check
| Source | Target | Verdict |
|---|---|---|
| `:1-2` `using System; using System.Collections.Generic;` | none | correct — unused, no imports needed (§4 explicit imports only) |
| `:4` `namespace btnet.Models` | `:2` `package btnet.Models;` | correct (§2A namespace row, §4) |
| `:6` `public partial class BugPostAttachment` | `:4` `public class BugPostAttachment` | correct (§2A `partial class` row: one Java class; single-part partial) |
| `:8` `public int Id { get; set; }` | `:6` `public int Id;` | correct (§2A auto-property row; §2A type table `int`→`int`) |
| `:9` `public int PostId { get; set; }` | `:7` `public int PostId;` | correct |
| `:10` `public byte[] Content { get; set; }` | `:8` `public byte[] Content;` | correct (`byte[]`→`byte[]`, null default identical) |
| — | `:1` `// Source: src/BugTracker.Web/Models/BugPostAttachment.cs` | correct (§6.1) |
| — | `:10` `// PORT STATUS: confidence=high todos=0` | correct, last line, 0 `TODO(port)` markers in file (§6.7) |

Member names/casing unchanged (§4), no members missing (§6.2), no source comments to copy, no markers required (no inventory rows, no UNKNOWN sites), no runtime API used. Default values match C# (0, 0, null). No thread-safety, SQL, formatting or exception behavior in this unit.

## Findings

None. (0 HIGH, 0 MEDIUM, 0 LOW)

## Rulebook obedience

OBEDIENT. Checked §2A namespace, partial-class, auto-property and primitive-type rows; §2A EF6 entity rule; §4 output path, package, naming, imports; §6.1 header, §6.2 completeness, §6.3 markers, §6.4 scope (only the manifest target exists for this unit), §6.5 inventory (no rows → no citations needed), §6.7 status trailer. No deviations found.

## Rulebook gaps

- G1 (minor, no effect here): the §2A `partial class` row (:133) is written for multi-part partials (pages + designer, `Startup`). It does not explicitly state that a single-file `partial` entity (all 23 `Models/*.cs` are `public partial class`, EF6 power-tools output) simply drops the modifier. Implementers did the evident thing, but a one-line rule would close it. Proposed addition to the row: "A `partial` class with only one part → plain class; the modifier is dropped." Evidence: `Models/BugPostAttachment.cs:6`, `Models/BugPost.cs`.
- G2 (minor): no rule states that C# `using` directives with no referenced type produce no Java import (§4 covers imports only by positive form). Proposed: "`using` directives are not translated; imports are derived from types actually referenced." Evidence: `Models/BugPostAttachment.cs:1-2`.
