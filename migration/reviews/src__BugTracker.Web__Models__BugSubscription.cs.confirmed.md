# Confirmed findings — src__BugTracker.Web__Models__BugSubscription.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

- G1 (very low impact): no rule says what to do with C# `using` directives that have no Java counterpart (`using System;`, `using System.Collections.Generic;`). The implementer dropped them without a comment, which matches the sibling entity `Models/BugRelationShip.java`. Two translators could still differ: one might drop them, another might leave a comment. Proposed rule for §6 or §4: "C# `using` namespace directives are dropped, with no comment; Java imports are derived only from the types actually referenced (explicit single-type imports, §4)." Evidence: `Models/BugSubscription.cs:1-2` and `Models/BugRelationShip.cs:1-2`, both translated without the directives.

1. **`partial class` row scope/count (§2A General conventions, line 133).** The row reads "`partial class` (148, pages+designer, `Startup`)", but 24 files in `src/BugTracker.Web/Models/*.cs` declare `public partial class` (EF6 reverse-engineered entities; 157 non-designer `.cs` files in `src/` overall), and here it is the only part. The rule's outcome (one Java class) clearly applies, but the parenthetical suggests it covers only pages and `Startup`, so two agents could disagree about whether a single-part `partial` entity needs a comment or a marker. Proposed amendment: "`partial class` (any count of parts, including the single-part EF6 entities in `Models/`) → one Java class without `partial` and without a comment or marker." Evidence: `Models/BugSubscription.cs:6`, `Models/priority.cs` → `port/web/src/main/java/btnet/Models/Priority.java` (translated the same way).
2. **Unused `using` directives (§4 Imports).** The rulebook says imports are explicit single-type imports, but it never says that a C# `using` with no Java counterpart (`using System; using System.Collections.Generic;`, `BugSubscription.cs:1-2`) is dropped without a comment. That is the only sensible reading and both translators would likely agree, but per the meta-rule a one-line amendment could state it: "`using` directives are not translated; imports follow from what the Java body uses."
