# Review R1: src/BugTracker.Web/Models/project.cs

Target: `port/web/src/main/java/btnet/Models/Project.java` (28 lines). Manifest row: `migration/manifest.tsv:39`.
Templates per RULEBOOK §4: none (core-layer `Models/` entity, §0; not a page, master or `.ascx`).
Inventory rows for this source path: none (`grep "Models/project.cs" migration/inventory.tsv` returns nothing; the only Project rows are for `Models/Mapping/projectMap.cs`, L303-312, which belong to the map unit).

## Checks performed

| Check | Rule | Result |
|---|---|---|
| Output path = `<module>/src/main/java/<namespace as path>/<PrimaryType>.java` | §4 | OK: `port/web/src/main/java/btnet/Models/Project.java` |
| Package = C# namespace verbatim (`btnet.Models`) | §2A namespace row (L131), §4 | OK (line 2) |
| Header `// Source: src/BugTracker.Web/Models/project.cs` on line 1 | §6.1 | OK |
| `public partial class Project` → one Java class `public class Project` (no other part exists: `grep -rn "partial class Project\b" src/` finds only `project.cs:6`) | §2A partial row (L133), §4 partial rule | OK |
| 21 auto-properties → 21 public fields, same names, same order, no initializers (source has none) | §2A auto-property row (L137), §2A EF6 entities bullet (L556) | OK, all 21 present (source :8-28 ↔ target :6-26) |
| `int` → `int` (Id, Active, EnableCustomDropDown1..3, Default) | §2A primitive mapping | OK; defaults 0 match C# |
| `Nullable<int>` → `Integer` (DefaultUser, AutoAssignToDefaultUser, AutoSubscribeDefaultUser, EnablePOP3) | §2A `T?`/`Nullable<T>` row (L151) | OK; default `null` matches C# |
| `string` → `String` (10 fields) | §2A | OK |
| Keyword collisions: `Default` is not a Java keyword (`default` is; Java is case-sensitive), so no trailing `_` | §2A names row (L130), §4 | OK, correctly not renamed; consistent with `Priority.java:11` `public int Default;` |
| Member casing unchanged (`POP3UserName`, `POP3SourceEMail`, `EnablePOP3`) — the map unit references these as strings (`this.Property("POP3UserName")`, inventory L305-307), so any rename would break the reflective lookup | §2A EF6 maps bullet (L560-563), inventory projectMap.cs L18-24 | OK, exact match |
| No navigation properties in source, so the "never loaded" navigation rule does not apply | §2A EF6 entities bullet (L556-559) | OK |
| No JPA/Lombok annotations, no getters/setters | §1 BANNED list (L106), §2A L137 | OK |
| Unused `using System; using System.Collections.Generic;` dropped; no imports needed (only `java.lang` types) | §4 imports | OK, compiles by reading |
| Implicit public no-arg constructor matches C# default ctor (needed by runtime `DbSet<T>` materialization) | §6.2 completeness | OK |
| No source comments to copy | §6.2 | OK |
| Markers: none needed; `todos=0` equals the count of `TODO(port)` (0) | §6.3, §6.7 | OK |
| Trailer last line `// PORT STATUS: confidence=high todos=0` | §6.7 | OK |
| Scope: only the manifest target written | §6.4 | OK |
| Inventory citation: no inventory decision used, so no `// inventory:` comment required | §5, §6.5 | OK |
| Thread-safety: plain mutable DTO, same as the C# entity (no shared static state) | n/a | OK |

## Findings

None. I assumed the translation was wrong and checked each of the 21 members for name, casing, order, type, nullability and default value against `project.cs:8-28`, and also checked the class shape, package, path, header and trailer. I found no defect. The file maps the entity token for token under §2A L137/L151 and the EF6 entities bullet.

Counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

OBEDIENT. Deviations: 0. Checked: §0 (core layer, structure-preserving), §1 (no JPA/Lombok), §2A (namespace L131, partial L133, auto-property L137, `Nullable<T>` L151, keyword rule L130 correctly not applied to `Default`, EF6 entity bullet L556), §4 (path, package, imports, member names, single top-level type), §5/§6.5 (inventory grep: no rows for this unit), §6.1-§6.7 (header, completeness, markers, scope, trailer).

## Rulebook gaps

- G1 (informational, low): §2A's keyword rule (L130, "If the name is a Java keyword, append `_` (`default` → `default_`)") does not say outright that matching is case-sensitive. Members named `Default` (here `project.cs:27`, and `priority.cs`) are not Java keywords and correctly stay unrenamed. But a reader who matches case-insensitively could "fix" them to `Default_`, which would break the map units' string lookups (`this.Property("Default")`). Proposed addition to L130: "Matching is case-sensitive: `Default`, `Class`, `New` are not keywords and are kept." Evidence: `src/BugTracker.Web/Models/project.cs:27`, `port/web/src/main/java/btnet/Models/Priority.java:11`.
