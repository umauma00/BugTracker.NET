# Confirmed findings — src__BugTracker.Web__Models__BugUser.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

1. **`Nullable<DateTime>` long form is uncounted and has no inventory rows.** The §2A row reads "`T?` / `Nullable<T>` (7)". The 7 counts only the `int?` sites of `Models/BugFromEmail.cs` (inventory rows 76–82, `NULLABLE_REQUEST_FIELD`). The spelled-out form `Nullable<DateTime>` occurs in at least 10 Models files (`BugPost.cs`, `BugTask.cs`, `BugUser.cs`, `CustomColumnsMetaData.cs`, `EmailedLink.cs`, `Votes.cs`, `bug.cs`, `project.cs`, `query.cs`, `user.cs`), and none of them has an inventory row. §5 lists "which nullable columns" as an inventory decision. The rulebook row already decides these sites unambiguously, so the translation is not affected.
   - Proposed amendment: change the count to cover both spellings, and add one sentence: "`Nullable<DateTime>` → `LocalDateTime` (already a reference type; `null` = DB NULL); no inventory row needed for entity fields."
   - Evidence: `grep -l "Nullable<" src/BugTracker.Web/Models/*.cs`; `grep "Models/BugUser.cs" migration/inventory.tsv` returns nothing.
2. **Field-order note (runtime, not this unit).** EF6 builds the SELECT column list in CLR declaration order. The runtime instead uses configured-property order, then name order, and carries its own `TODO(port)` (`DbSet.java:96-101`). This has no translator-facing consequence for this entity. It is recorded only so the human knows the entity's declaration order is preserved but not used.

1. **Entity key with no backing column (for the `BugUserMap` unit and the runtime author, not this unit).** `BugUserMap.cs:11` declares `HasKey(t => t.Id)`, but `Id` gets no `HasColumnName`, and `bug_user` (`setup.sql:382-392`) has no `Id` column (its natural key is the unique index `bu_bug, bu_user`, `:394`). EF6 would map `Id` to a column named `Id` by convention, so any query on `Context.BugUsers` fails in .NET with an invalid-column SQL error. No call site queries `BugUsers` today (`grep` finds only `Context.cs:29,67`), so this is latent. The rulebook says what `Find`/`ToList` select ("all mapped columns"), but it does not say whether the runtime maps an unmapped field by name convention, i.e. column `Id`. Only that convention reproduces the EF failure. Proposed rule for §2A EF6: "A property with no `HasColumnName` maps to a column of the same name (EF6 convention), so a missing column fails at the SELECT exactly as EF did." Evidence: `BugUserMap.cs:11,31-38`; `setup.sql:382-394`. The entity translation does not depend on the answer.
