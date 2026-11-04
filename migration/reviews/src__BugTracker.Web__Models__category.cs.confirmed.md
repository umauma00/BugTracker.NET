# Adjudication — src/BugTracker.Web/Models/category.cs

Target: `port/web/src/main/java/btnet/Models/Category.java`
Reviews: R1 (1 finding), R2 (1 finding)

## Confirmed

C1 | from R1.F1/R2.F1 | port/web/src/main/java/btnet/Models/Category.java:9 | Rename field `Default_` back to `Default`; the keyword suffix must not be applied to a non-keyword name | `public int Default;` | Both reviewers report the same defect. Source `src/BugTracker.Web/Models/category.cs:11` declares `public int Default { get; set; }`. RULEBOOK.md:130 says that keyword matching "is exact and case-sensitive: `Default`, `Class`, `New`, `Int` are NOT keywords and are kept unchanged [F-A18]", and §4 (RULEBOOK.md:1789) says "nothing else is renamed". The sibling entities keep the name unchanged: `Priority.java:11`, `Status.java:10`, `Project.java:25` all declare `public int Default;`. The rename also breaks behavior: the categoryMap inventory row `Property("Default").HasColumnName("ct_default")` finds no matching field, so the runtime falls back to the column name `Default_` and SQL fails with "Invalid column name". Both reviewers trace this through `DbSet.EntityModel` and `EntityTypeConfiguration.columnFor`.

## Not confirmed

(none — every finding was reported by both reviewers)

## Rulebook gaps

- G1 (R1.G1 = R2.G1): make the keyword-collision test explicitly exact and case-sensitive against Java 21 reserved keywords and literals, with `Default`/`Class`/`New` kept unchanged. Adjudicator note: RULEBOOK.md:130 already says this ([F-A18]). The gap that remains is narrower. §4 (RULEBOOK.md:1789) repeats the suffix rule without the case-sensitivity clause, and no worked example cites `Models/category.cs:11`. The proposed amendment: add the clause and the example `Default` → `Default` to §4 so that the two sections cannot be read differently. This unit and its siblings answered the question differently, which meets the meta-rule's evidence bar.
- G2 (R1.G2 = R2.G2; runtime-owned, queue for the runtime author, do not edit from a loop): `DbSet.EntityModel` silently ignores a `Property("<name>")` configuration that names no entity field, and `EntityTypeConfiguration.columnFor` falls back to the field's own name as the column. As a result, an entity/map name mismatch surfaces only as a SQL "Invalid column name" error. Proposal: throw `InvalidOperationException("The property '<name>' is not a declared property on type '<T>'.")` at model build, matching the existing key-component check.
