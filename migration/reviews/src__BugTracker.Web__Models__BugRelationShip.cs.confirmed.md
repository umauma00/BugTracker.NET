# Confirmed findings — src__BugTracker.Web__Models__BugRelationShip.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

- None decisive for this unit. Observation only (no action needed for correctness here): §2A EF6 does not state that entity member **declaration order** must be preserved, although runtime `DbSet` builds its column list from `Class.getFields()` (whose order the JVM does not guarantee). The translation keeps source order anyway. Proposed rule (for the human): "Entity fields keep the source declaration order; the runtime must not depend on reflection order" — evidence `port/runtime/src/main/java/btnet/runtime/data/DbSet.java:56`.

1. **Unused C# `using` directives.** The rulebook (§4 Imports) says how to write Java imports but does not say explicitly that C# `using` directives with no used type are dropped rather than mapped (e.g. `using System.Collections.Generic;` → `import java.util.*` or similar). The implementer dropped them, which is the sensible reading. Proposed rule for §4: "C# `using` directives are not translated; imports are derived from the types the Java file actually references." Evidence: `Models/BugRelationShip.cs:1-2` (and the same pattern in every `Models/*.cs` entity).
2. **Single-part `partial class`.** §2A's `partial class` row and §4's `partial` line describe merging multiple parts; they do not state the single-part case (`partial` keyword on a class with only one part, as in all EF-generated entities). Proposed addition: "`partial` with a single part → plain class, modifier dropped." Evidence: `Models/BugRelationShip.cs:6`.
