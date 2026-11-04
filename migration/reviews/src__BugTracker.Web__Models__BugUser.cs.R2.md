# Review R2 — src/BugTracker.Web/Models/BugUser.cs

Target: `port/web/src/main/java/btnet/Models/BugUser.java` (manifest row 44). No template (core-layer EF6 entity, RULEBOOK §0, §4).

Inputs read: RULEBOOK.md (all 1,884 lines, including §3.1), inventory (no rows for `Models/BugUser.cs`; rows 150-163 are for `Models/Mapping/BugUserMap.cs`, a different unit), the source, the target, `setup.sql:382-394` (`bug_user`), `Models/Mapping/BugUserMap.cs`, `Models/Context.cs:29,67`, and the runtime `btnet.runtime.data.DbSet` materializer (`DbSet.java:290-320`), to check how the field types behave there.

## Findings

None.

I checked each item below against the source and found no defect.

| Check | Source | Target | Rule | Result |
|---|---|---|---|---|
| Header | — | `BugUser.java:1` `// Source: src/BugTracker.Web/Models/BugUser.cs` | §6.1 [S1-41] | OK |
| Package | `BugUser.cs:4` `namespace btnet.Models` | `:2` `package btnet.Models;` | §2A General "Namespace", §4 | OK |
| Path / primary type | file stem `BugUser` | `port/web/src/main/java/btnet/Models/BugUser.java` | §4 naming rule, manifest row 44 | OK |
| `public partial class` (only one part; `grep` finds no other `partial class BugUser`) | `:6` | `:6` `public class BugUser` | §2A `partial class` row | OK |
| Auto-properties → public fields, same names, same order | `:8-16` | `:8-16` | §2A auto-property row; §2A EF6 "Entities" | OK (9 of 9 members) |
| `int` → `int` (Id, BugId, UserId, Flag, Seen, Vote) | `:8-11,13,15` | `:8-11,13,15` | §2A primitive-type row | OK |
| `Nullable<DateTime>` → boxed `LocalDateTime` (FlagDate, SeenDate, VoteDate) | `:12,14,16` | `:12,14,16` | §2A `T?`/`Nullable<T>` row; `DateTime` → `java.time.LocalDateTime` | OK. `LocalDateTime` is already a reference type, so `null` stays representable. |
| Imports | `using System; using System.Collections.Generic;` (nothing used from them besides `Nullable`/`DateTime`) | single-type `import java.time.LocalDateTime;`, no wildcard, no static | §4 Imports | OK |
| Runtime API calls | none | none | §3 / §3.1 | OK |
| Parameterless constructor (the runtime `DbSet.materialize` calls `getDeclaredConstructor().newInstance()`) | implicit | implicit default constructor | §2A EF6 | OK |
| Null handling | `int` columns `not null` and `datetime null` columns (`setup.sql:384-391`) | primitive `int` fields reject DBNull with EF's `InvalidOperationException` text; boxed `LocalDateTime` fields take `null` (`DbSet.java:301-307`) | §2A EF6, §2A Nullable | OK: matches EF6 (a non-nullable property fails on NULL, a nullable one accepts it) |
| Markers / trailer | no TODO/BUG sites | `:18` `// PORT STATUS: confidence=high todos=0`, 0 `TODO(port)` | §6.3, §6.7 | OK (count exact) |
| No sanctioned-fix site | not an F1–F5 file | no `FIXED(port)` | §0.1 | OK |
| Scope | — | only the manifest target written | §6.4 | OK |

## Rulebook obedience

**OBEDIENT.** I found no deviations. The translation applies §2A (partial class, auto-property → public field, `Nullable<DateTime>` → `LocalDateTime`, `int` → `int`), §2A EF6 (entity fields kept public for the runtime model), §4 (path, package, explicit single-type import) and §6.1/§6.2/§6.7 (header, completeness, trailer). The implementer had no inventory rows to cite (§6.5), and §5 says a plain entity with no per-site decisions needs none.

## Rulebook gaps

1. **Entity key with no backing column (for the `BugUserMap` unit and the runtime author, not this unit).** `BugUserMap.cs:11` declares `HasKey(t => t.Id)`, but `Id` gets no `HasColumnName`, and `bug_user` (`setup.sql:382-392`) has no `Id` column (its natural key is the unique index `bu_bug, bu_user`, `:394`). EF6 would map `Id` to a column named `Id` by convention, so any query on `Context.BugUsers` fails in .NET with an invalid-column SQL error. No call site queries `BugUsers` today (`grep` finds only `Context.cs:29,67`), so this is latent. The rulebook says what `Find`/`ToList` select ("all mapped columns"), but it does not say whether the runtime maps an unmapped field by name convention, i.e. column `Id`. Only that convention reproduces the EF failure. Proposed rule for §2A EF6: "A property with no `HasColumnName` maps to a column of the same name (EF6 convention), so a missing column fails at the SELECT exactly as EF did." Evidence: `BugUserMap.cs:11,31-38`; `setup.sql:382-394`. The entity translation does not depend on the answer.
