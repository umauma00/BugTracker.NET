# Review R1 — src/BugTracker.Web/Models/user.cs → port/web/src/main/java/btnet/Models/User.java

Reviewer: R1 (adversarial, read-only). Date: 2026-11-04.

Inputs read: `src/BugTracker.Web/Models/user.cs` (36 lines),
`port/web/src/main/java/btnet/Models/User.java` (36 lines), RULEBOOK §0, §1,
§2A (General conventions, Strings, EF6, JSON), §4, §5, §6, §7; inventory grep
for `Models/user.cs` (no rows). Cross-checked against the sibling entity
`port/web/src/main/java/btnet/Models/BugFromEmail.java`, `Models/Context.cs:57`
(`DbSet<User> Users`) and `Models/Mapping/userMap.cs` (maps by property name).
This is a core-layer entity, so no template, judge spec or page handle applies
(§0, §4).

## Member-by-member check

| C# (user.cs) | Java (User.java) | Rule | OK |
|---|---|---|---|
| :4 `namespace btnet.Models` | :2 `package btnet.Models;` | §2A namespace, §4 | yes |
| :6 `public partial class User` | :6 `public class User` | §2A partial class (one class), §4 primary type = file stem | yes |
| :8 `int Id` | :8 `public int Id;` | §2A auto-property → public field; int→int | yes |
| :9-14 `string` UserName, Salt, Password, FristName (typo kept), LastName, EMail | :9-14 `public String …` same names, same order | §2A names identical, §6.2 | yes |
| :15-18 `int` AdminId, DefaltQueryID (typo kept), EnableNotifications, AutoSubscribe | :15-18 `public int …` | §2A | yes |
| :19-21 `Nullable<int>` AutoSubscribeOwnBugs, AutoSubscribeReportedBugs, SendNotificationsToSelf | :19-21 `public Integer …` | §2A `Nullable<T>` → boxed | yes |
| :22 `int Active` | :22 `public int Active;` | §2A | yes |
| :23-24 `Nullable<int>` BugsPerPage, ForcedProject | :23-24 `public Integer …` | §2A | yes |
| :25-27 `int` Reported/Assigned/SubscribedNotifications | :25-27 `public int …` | §2A | yes |
| :28 `string Signature` | :28 `public String Signature;` | §2A | yes |
| :29-32 `int` UseEditor, EnableBugListPopUps, CreatedUser, OrganizationId | :29-32 `public int …` | §2A | yes |
| :33 `Nullable<DateTime> LastLoginDate` | :33 `public LocalDateTime LastLoginDate;` + `import java.time.LocalDateTime;` | §2A DateTime → `java.time.LocalDateTime`; `Nullable<T>` → boxed (`LocalDateTime` named explicitly) | yes |
| :34 `string PasswordResetKey` | :34 `public String PasswordResetKey;` | §2A | yes |
| (implicit public parameterless ctor) | (implicit public no-arg ctor) | §6.2 completeness | yes |

All 27 members present, same names (including the source typos `FristName`,
`DefaltQueryID`), same declaration order (matters for Jackson declaration-order
serialization, §2A JSON [S2-34]), same types per the §2A type table. No
initializers in the source, none in the target. No Java keyword collisions.
`using System; using System.Collections.Generic;` have no Java counterpart
needed (nothing from `java.util` used). Default values agree: C# `int` 0 /
reference null ↔ Java `int` 0 / reference null.

Header (§6.1): `// Source: src/BugTracker.Web/Models/user.cs` — correct
repo-relative path, first line. Trailer (§6.7): `// PORT STATUS:
confidence=high todos=0` is the last line; the file contains zero `TODO(port)`
markers, so the count is exact. No markers were required (no quirks, no
unknowns, no BUG sites). Imports explicit single-type (§4). Nothing banned
(§1: no JPA/Lombok annotations). No `@Required`/validation attributes exist in
the source, so none are missing (contrast `BugFromEmail.java`).

## Findings

None. I tried to break it on: type mapping of each nullable vs non-nullable
member, typo-preserved names, member order, import correctness, header/trailer
format and todo count, missing members/constructor, accidental JPA or
annotation additions, and access modifiers (§2A [S2-12]: all members are
explicitly `public` in the source and stay `public`). No defect found.

Counts: HIGH 0, MEDIUM 0, LOW 0.

## Rulebook obedience

OBEDIENT. Rules checked and followed: §2A auto-property → public field;
§2A `Nullable<T>` → boxed type; §2A DateTime → `LocalDateTime`; §2A partial
class → one class; §2A access modifiers [S2-12]; §2A EF6 "Entities:
auto-properties → public fields"; §4 output path
(`port/web/src/main/java/btnet/Models/User.java`, primary type = file stem
case-insensitively), package = namespace verbatim, explicit imports; §6.1
header; §6.2 completeness; §6.3 markers (none needed, none present); §6.4
scope (only the manifest target written; no template applies); §6.7 trailer
with exact todo count.

§6.5 / §5 note: the inventory has no rows for `Models/user.cs`, so there was
no inventory decision to cite and the absence of `// inventory:` comments is
correct. Whether the implementer flagged the absence in its batch report (§5
"A site missing from the inventory is an inventory bug: flag it in the
report") could not be verified from the target file and is not a translation
defect; see gaps below.

## Rulebook gaps

1. **Nullable entity fields are not inventoried, and the §2A count is stale.**
   §2A says `T?` / `Nullable<T>` has 7 sites (citing `Models/BugFromEmail.cs`),
   and §5 says "which nullable columns" are looked up in the inventory, but
   `grep -rn "Nullable<" src/ --include=*.cs` (excluding designer files) finds
   37 sites, 6 of them in `user.cs` (:19-21, :23-24, :33), and the inventory
   has no row for any of them. The mechanical rule decided this file
   unambiguously, so no harm here. Proposed amendment: correct the survey
   count, and state that entity `Nullable<T>` auto-properties in `Models/*.cs`
   need no inventory row (the §2A row decides them), so implementers do not
   raise false "inventory bug" flags for every entity.
2. **Nullable `DateTime` wording.** §2A's `Nullable<T>` row gives the boxed
   type as "`Integer`, `LocalDateTime`". Taken literally that implies
   `LocalDateTime` is the boxed form of `DateTime`, but a non-nullable `DateTime`
   also maps to `LocalDateTime` (§2A type table), so a nullable and a
   non-nullable `DateTime` field get the same Java type and only the nullable
   one may hold `null`. The rule is not ambiguous, but it is undocumented that
   a null in a non-nullable `DateTime` entity field (C# `default(DateTime)` =
   `0001-01-01`) means something different from Java `null`. Proposed note:
   state in §2A EF6 whether the runtime `DbSet` materializer assigns `null` or
   `LocalDateTime.MIN`-style defaults for a non-nullable `DateTime` field read
   from a DBNull column (EF6 throws). It does not affect `User` (its only
   `DateTime` is nullable), but it does affect other entities.
