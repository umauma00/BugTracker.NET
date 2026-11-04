# Review R2 — src/BugTracker.Web/Models/BugSubscription.cs

Target: `port/web/src/main/java/btnet/Models/BugSubscription.java` (manifest row 35). Core layer (`Models/`), structure-preserving (§0). No template applies (§4: templates are only for pages, masters, `.ascx`, `inc_diff.inc`).

Inputs read: RULEBOOK.md (all of it, in chunks; §0, §2A General conventions, §2A EF6, §3/§3.1, §4, §5, §6 are the ones that bear on this unit), inventory.tsv (no rows for `Models/BugSubscription.cs`; rows 124-128 belong to `Models/Mapping/BugSubscriptionMap.cs` and reference the field names `Id`, `BugId`, `UserId`), source, target, the consumers `Models/Mapping/BugSubscriptionMap.cs` and `Models/Context.cs:27,65`, and runtime `btnet/runtime/data/DbSet.java` (reflects public instance fields and uses the no-arg constructor).

## Side by side

| Source | Target | Rule | Verdict |
|---|---|---|---|
| `BugSubscription.cs:1-2` `using System; using System.Collections.Generic;` | no imports | §4 (explicit single-type imports; nothing is used) | OK |
| `:4` `namespace btnet.Models` | `:2` `package btnet.Models;` | §2A General conventions (namespace → package, same case); §4 | OK |
| `:6` `public partial class BugSubscription` | `:4` `public class BugSubscription` | §2A `partial class` → one Java class; §4 primary type = file stem | OK |
| `:8` `public int Id { get; set; }` | `:6` `public int Id;` | §2A auto-property → public field, same name; `int` → `int` | OK |
| `:9` `public int BugId { get; set; }` | `:7` `public int BugId;` | same | OK |
| `:10` `public int UserId { get; set; }` | `:8` `public int UserId;` | same | OK |
| implicit public parameterless ctor | implicit public no-arg ctor | §6.2 completeness; `DbSet.java:281` `getDeclaredConstructor().newInstance()` | OK |
| — | `:1` `// Source: src/BugTracker.Web/Models/BugSubscription.cs` | §6.1 (repo-relative path, S1-41) | OK |
| — | `:10` `// PORT STATUS: confidence=high todos=0` | §6.7 (last line; 0 `TODO(port)` markers in file) | OK |

Field default values: C# `int` auto-properties default to 0, Java `int` fields default to 0 — identical. Declaration order preserved (relevant to Jackson declaration-order serialization, §2A JSON, and to `DbSet`'s deterministic field order). Names match the mapping strings the inventory prescribes for `BugSubscriptionMap` (rows 124-128: `"Id"`, `"BugId"`, `"UserId"`). No navigation properties, no source comments, no markers required, no SQL, no strings. Nothing to compare with judge specs (not a page/control).

## Findings

None. I tried to find defects in every category the prompt lists (behavior, nullability, compile-level, missing members, markers, header/trailer, package/name, unsanctioned fixes) and found no defect I could cite to a rule or a source line.

Counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

OBEDIENT. Checked each rule that applies:

- §0 core posture (same type name, same members, same member names): followed.
- §2A General conventions: namespace → package (followed); `partial class` → one class (followed); auto-property → public field with same name and no initializer (followed); `int` → `int` (followed); access modifiers kept explicit `public` (S2-12; followed).
- §2A EF6: entity auto-properties → public fields; no navigation properties exist, so no `TODO(port): navigation property` is due (followed).
- §4: path matches manifest row 35; package = namespace verbatim; no wildcard/static imports; no extra top-level type (followed).
- §6.1 header, §6.2 completeness (all 3 members plus the implicit ctor), §6.3 markers (none, none needed), §6.4 scope (only the target written), §6.5 inventory (no rows for this unit, so no `// inventory:` citations are due), §6.7 trailer with an accurate `todos=0` (followed).

No deviations.

## Rulebook gaps

1. **`partial class` row scope/count (§2A General conventions, line 133).** The row reads "`partial class` (148, pages+designer, `Startup`)", but 24 files in `src/BugTracker.Web/Models/*.cs` declare `public partial class` (EF6 reverse-engineered entities; 157 non-designer `.cs` files in `src/` overall), and here it is the only part. The rule's outcome (one Java class) clearly applies, but the parenthetical suggests it covers only pages and `Startup`, so two agents could disagree about whether a single-part `partial` entity needs a comment or a marker. Proposed amendment: "`partial class` (any count of parts, including the single-part EF6 entities in `Models/`) → one Java class without `partial` and without a comment or marker." Evidence: `Models/BugSubscription.cs:6`, `Models/priority.cs` → `port/web/src/main/java/btnet/Models/Priority.java` (translated the same way).
2. **Unused `using` directives (§4 Imports).** The rulebook says imports are explicit single-type imports, but it never says that a C# `using` with no Java counterpart (`using System; using System.Collections.Generic;`, `BugSubscription.cs:1-2`) is dropped without a comment. That is the only sensible reading and both translators would likely agree, but per the meta-rule a one-line amendment could state it: "`using` directives are not translated; imports follow from what the Java body uses."
