# Review R2 — src/BugTracker.Web/Models/status.cs

Target: `port/web/src/main/java/btnet/Models/Status.java` (manifest row 40).
No template applies (core unit, §0 / §4).

## Sources examined

- `src/BugTracker.Web/Models/status.cs:1-14`: `public partial class Status` with 5 auto-properties: `int Id`, `string Name`, `int SortOrder`, `string Style`, `int Default`.
- `port/web/src/main/java/btnet/Models/Status.java:1-12`.
- `migration/RULEBOOK.md`: §0, §2A General conventions (type/member names, partial class, auto-property, `int`/`string` mapping, access modifiers), §2A EF6, §3.1, §4, §5, §6.
- `migration/inventory.tsv`: **no rows** for `src/BugTracker.Web/Models/status.cs`. That is expected, because the unit has no gap constructs. The `Status` fields are referenced only by the `statusMap.cs` rows L11–L27 (`this.Property("Name")`, `"SortOrder"`, `"Style"`, `"Default"`, `HasKey("Id")`). The Java field names match those strings exactly.
- Cross-checks: `Models/Mapping/statusMap.cs` (column mapping), `setup.sql:452-459` (`st_sort_seq`/`st_default` are `int not null`, so primitive `int` is correct and `st_style` nullable maps to `String`). Runtime `DbSet.java` materializes through `getDeclaredConstructor().newInstance()` and public non-static fields. The implicit public no-arg constructor and the public fields satisfy this. The sibling `Priority.java` uses the same shape.

## Findings

None.

Checked and found correct:
- Header `// Source: src/BugTracker.Web/Models/status.cs` is on line 1 (§6.1).
- Package `btnet.Models` is the namespace verbatim (§2A, §4). The path matches the manifest (§4).
- `partial class` (single part) becomes one Java `public class Status` (§2A partial class).
- Each auto-property becomes a public field with the same name and source order, no initializers (§2A auto-property; §2A EF6 "Entities").
- `int` maps to `int` and `string` maps to `String` (§2A type table).
- `Default` is kept unchanged because it is not a Java keyword (case-sensitive match, §2A Type/member names [F-A18]).
- The unused `using System; using System.Collections.Generic;` directives produce no imports, which is consistent with §4 explicit imports.
- All 5 members are present (§6.2).
- The trailer `// PORT STATUS: confidence=high todos=0` is the last line, and the count is correct because the file has 0 `TODO(port)` markers (§6.7).
- The file adds no markers, no sanctioned-fix sites and no other content.

Counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

OBEDIENT. I found no deviation. Every applicable rule (§2A General conventions: names, partial, auto-property, primitive mapping; §2A EF6 entities; §4 naming/package/path; §5 inventory grep; §6.1 header, §6.2 completeness, §6.3 markers, §6.4 scope, §6.7 trailer) is followed. §6.5 (inventory citation comments) does not apply because no inventory row decided anything in this unit.

## Rulebook gaps

1. **Single-part `partial class`.** The §2A `partial class` row describes multi-part classes (pages+designer, `Startup`). It does not say explicitly that a `partial` class with only one part (all 23 EF entity files) is just a plain class. Every implementer will probably read it that way, but two agents could differ on whether to add a comment. Proposed rule: "a `partial` type with a single part translates as an ordinary class; no comment." Evidence: `Models/status.cs:6`, `Models/priority.cs`.
2. **Unused `using` directives.** No rule says that `using` directives with no Java counterpart are dropped silently, rather than translated to imports or kept as comments. Proposed rule: "`using` directives are not translated; imports are derived from the Java code (§4)." Evidence: `Models/status.cs:1-2`.
