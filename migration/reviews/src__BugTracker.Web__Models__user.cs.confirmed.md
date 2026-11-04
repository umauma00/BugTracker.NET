# Confirmed findings — src__BugTracker.Web__Models__user.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

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
