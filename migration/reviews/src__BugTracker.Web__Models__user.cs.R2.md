# Review R2 — src/BugTracker.Web/Models/user.cs

Target: `port/web/src/main/java/btnet/Models/User.java` (no template: core-layer
EF6 entity, §0 / §4 — no template belongs to a `Models/*.cs` unit).

Inputs read: RULEBOOK.md (§0, §0.1, §1, §2A general conventions + type table
+ EF6, §3.1, §4, §5, §6, §7), `migration/inventory.tsv` (grep for
`Models/user.cs` / `Models/User`: **no rows** — expected, the unit has no
nullable-coalesce, ref, or region sites; the `Nullable<T>` fields are decided
by the §2A rule row, not by inventory), the source, the target, the sibling
consumers (`Models/Context.cs:57` `DbSet<User> Users`,
`Models/Mapping/userMap.cs`, `Controls/BugList.ascx.cs:131`), and the runtime
`btnet/runtime/data/DbSet.java` (materialization via `type.getFields()`,
`getDeclaredConstructor().newInstance()`, scalar set includes `int`,
`Integer`, `String`, `LocalDateTime`).

## Member-by-member check (source line → target line)

| Source | C# | Java | Rule | OK |
|---|---|---|---|---|
| :4-6 | `namespace btnet.Models` / `public partial class User` | `package btnet.Models;` / `public class User` (:2,:6) | §2A namespace row; `partial` → one class (§2A, §4 "partial"; only one part exists) | yes |
| :8 | `int Id` | `public int Id;` (:8) | §2A auto-property → public field; `int`→`int` (§2A type table L163) | yes |
| :9-14 | `string` UserName, Salt, Password, FristName, LastName, EMail | `public String …` (:9-14), names incl. misspelling `FristName` kept | §2A names identical; §0 "translating, not improving" | yes |
| :15-18 | `int` AdminId, DefaltQueryID, EnableNotifications, AutoSubscribe | `public int …` (:15-18), `DefaltQueryID` spelling/casing kept | §2A | yes |
| :19-21 | `Nullable<int>` ×3 | `public Integer …` (:19-21) | §2A `Nullable<T>` → boxed | yes |
| :22 | `int Active` | `public int Active;` (:22) | §2A | yes |
| :23-24 | `Nullable<int>` BugsPerPage, ForcedProject | `public Integer …` (:23-24) | §2A | yes |
| :25-27 | `int` ×3 notifications | `public int …` (:25-27) | §2A | yes |
| :28 | `string Signature` | `public String Signature;` (:28) | §2A | yes |
| :29-32 | `int` UseEditor, EnableBugListPopUps, CreatedUser, OrganizationId | `public int …` (:29-32) | §2A | yes |
| :33 | `Nullable<DateTime> LastLoginDate` | `public LocalDateTime LastLoginDate;` (:33), import `java.time.LocalDateTime` (:4) | §2A type table (`DateTime`→`java.time.LocalDateTime`), `Nullable<T>`→boxed (LocalDateTime is a reference type) | yes |
| :34 | `string PasswordResetKey` | `public String PasswordResetKey;` (:34) | §2A | yes |

27 of 27 properties present, declaration order preserved (relevant to
`DbSet` column order fallback and JSON declaration order, §2A JSON / S2-34).
No initializers in source, none in target; Java field defaults (`0`/`null`)
equal C# defaults. No navigation properties in the source, so none needed
(§2A EF6). Unused `using System.Collections.Generic;` has no Java counterpart
(no import needed) — correct, §4 explicit single-type imports only.

## Findings

None.

Checked and rejected as non-defects:
- Header `// Source: src/BugTracker.Web/Models/user.cs` (:1) — matches §6.1
  (repo-relative source path, original lower-case file name).
- Trailer `// PORT STATUS: confidence=high todos=0` (:36) — last line, §6.7
  format; 0 `TODO(port)` markers in file, count correct.
- No `// inventory:` comment — correct, no inventory row drove a decision (§6.5).
- Name clash [J-C15]: §3.1 lists no runtime type named `User` (only
  `UserControl`), so the plain `btnet.Models.User` name is right.
- File path matches §4 (`port/web/src/main/java/<namespace>/<PrimaryType>.java`,
  primary type `User` = file stem `user` case-insensitively).
- Runtime compatibility: every field type is in `DbSet.SCALARS`, and the
  implicit public no-arg constructor satisfies `getDeclaredConstructor()`.

## Severity counts

HIGH 0 · MEDIUM 0 · LOW 0

## Rulebook obedience

OBEDIENT. Rules applied and followed: §0 (core layer, structure-preserving),
§2A General conventions (names identical; auto-property → public field;
`Nullable<T>` → boxed; `partial class` → one class), §2A type table
(`DateTime` → `java.time.LocalDateTime`), §2A EF6 (entities: auto-properties
→ public fields), §4 (path, package = namespace, explicit single-type
imports), §5 (inventory grep: no rows, none required), §6.1 header, §6.2
completeness (all 27 members), §6.3 markers (none), §6.4 scope (only the
manifest target written), §6.7 trailer. No deviations found.

## Rulebook gaps

None that affect this unit. Observation for the human (no action required
here): the application type `btnet.Models.User` shares its simple name with
the `User` intrinsic **field** of the runtime `Page`/`UserControl` (§2A
intrinsics row). [J-C15] only covers runtime-type vs application-type
clashes. Java resolves an ambiguous simple name to the variable (JLS 6.5.2),
so `User.Identity()` in a page or control that also imports
`btnet.Models.User` still means the intrinsic, as in C#. Today no consumer
names the type (`BugList.ascx.cs:131` uses `context.Users` with a lambda
parameter). A possible one-line rule: "inside Page/UserControl/MasterPage
subclasses, the entity type `btnet.Models.User` is written fully qualified"
— evidence: §2A intrinsics row + `Models/user.cs:6`.
