# Review R1: src/BugTracker.Web/Models/BugPostAttachment.cs

Target: `port/web/src/main/java/btnet/Models/BugPostAttachment.java`
Reviewer: R1 (adversarial, read-only)

## Evidence read

- Source `src/BugTracker.Web/Models/BugPostAttachment.cs:1-12`: `public partial class BugPostAttachment` in namespace `btnet.Models`, three auto-properties `int Id`, `int PostId`, `byte[] Content`; unused `using System; using System.Collections.Generic;`.
- Target `BugPostAttachment.java:1-10`.
- Inventory: no rows for `src/BugTracker.Web/Models/BugPostAttachment.cs`. The only `BugPostAttachment` rows (inventory.tsv:88-92) belong to `Models/Mapping/BugPostAttachmentMap.cs`, a different unit. The entity holds no per-site gap decisions (no nullable value types, no navigation properties, no ref params), so having no rows is not an inventory bug (§5).
- RULEBOOK §2A General conventions (names, namespace, `partial class`, access modifiers [S2-12], auto-property → public field), §2A type table (`byte[]` → `byte[]`, `int` → `int`), §2A EF6 (entities: auto-properties → public fields), §4 (path, package, imports), §6 items 1-7.
- Runtime consumer check: `port/runtime/src/main/java/btnet/runtime/data/DbSet.java:56-57` reflects over public non-static scalar fields. `byte[].class` is in `SCALARS` (:101). `:252` instantiates through `getDeclaredConstructor().newInstance()`, which works with the implicit public no-arg constructor. The fields therefore map to `Id`/`PostId`/`Content`, the names `BugPostAttachmentMap` uses in `Property("Id")` etc. (inventory.tsv:88-92).

## Findings

None.

Points checked, with no defect found:
- Path and package: `port/web/src/main/java/btnet/Models/BugPostAttachment.java`, `package btnet.Models;`. Both match §4 and the namespace.
- Type: `public class`, so it stays public (§2A access modifiers). `partial` with one part becomes one Java class (§2A `partial class`). No other part exists in `src/` (grep: the only other `BugPostAttachment` type is the unrelated nested `Bug.BugPostAttachment` at `btnet/bug.cs:434`, which is a different unit and a different FQN, so there is no clash).
- Members: `public int Id;`, `public int PostId;` and `public byte[] Content;` match source :8-10. Names, order and types follow §2A (auto-property → public field; `int`/`byte[]` type mapping). There are no initializers in the source, and the Java defaults (0/0/null) equal the C# defaults (0/0/null).
- Completeness (§6.2): all 3 properties are present. There are no constructors, methods, constants or comments to copy.
- Imports: the C# `using`s are unused and need no Java import. The Java file has none, which complies with §4.
- Header (§6.1): `// Source: src/BugTracker.Web/Models/BugPostAttachment.cs` is correct.
- Markers (§6.3) / trailer (§6.7): `// PORT STATUS: confidence=high todos=0`. The file has 0 `TODO(port)` markers, so the count is correct. No unsanctioned fixes, and no marker is needed.
- Not a page or control, so the judge contract does not apply.

Counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

OBEDIENT. Each applicable rule was followed: §2A namespace→package, `partial class` → one class, access modifiers kept (`public`), auto-property → public field with the same name and no initializer, type mapping `int`/`byte[]`; §2A EF6 entity rule; §4 output path, package and imports; §6.1 header, §6.2 completeness, §6.3 markers, §6.4 scope (only the target file was written; no template applies to a Models unit), §6.7 trailer. §6.5 (cite inventory rows) does not apply, because no inventory row for this unit drove a decision. Deviations: 0.

## Rulebook gaps

None found for this unit. A minor observation, not a gap: §2A does not say explicitly that unused C# `using` directives produce no Java import. §4's "explicit single-type imports" rule implies it, and the existing Models translations (e.g. `BugPost.java`) are consistent with that reading.
