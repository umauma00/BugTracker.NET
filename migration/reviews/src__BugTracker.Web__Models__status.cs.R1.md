# Review R1 — src/BugTracker.Web/Models/status.cs

Target: `port/web/src/main/java/btnet/Models/Status.java`
Templates: none (core-layer entity; RULEBOOK §4 places templates only for pages/masters/.ascx/includes).

## Inputs checked

- Source `src/BugTracker.Web/Models/status.cs:1-14` (5 auto-properties on `public partial class Status`, namespace `btnet.Models`).
- Target `port/web/src/main/java/btnet/Models/Status.java:1-12`.
- Inventory: `grep "Models/status.cs" migration/inventory.tsv` returns 0 rows. The unit has no gap sites (no nullable, ref, LINQ, or navigation properties). Every construct is decided by a general §2A rule, so the missing rows are not an inventory bug (§5).
- RULEBOOK §0, §1, §2A general conventions (names, keywords [F-A18], partial, access modifiers [S2-12], auto-properties), §2A EF6 (entities), §4 (naming/paths/imports), §6 (per-file obligations), §7.
- Cross-checks: `Models/Mapping/statusMap.cs` (it maps all 5 properties: Id, Name, SortOrder, Style, Default), `setup.sql:452-459` (`st_id int`, `st_name nvarchar(60) not null`, `st_sort_seq int not null`, `st_style nvarchar(30) null`, `st_default int not null`), runtime `DbSet.materialize` (`port/runtime/.../data/DbSet.java:290-322`: parameterless constructor plus public fields via `getFields()`), and the sibling `Priority.java` for consistency.

## Findings

None.

Item-by-item verification (attempted refutations):

| Source | Target | Rule | Result |
|---|---|---|---|
| status.cs:4 `namespace btnet.Models` | :2 `package btnet.Models;` | §2A Namespace, §4 | correct |
| status.cs:6 `public partial class Status` | :4 `public class Status` | §2A partial (single part, so one class), §4 primary type = file stem (case-insensitive) → `Status.java` | correct; path matches the manifest target |
| status.cs:8 `public int Id { get; set; }` | :6 `public int Id;` | §2A auto-property → public field, same name | correct |
| status.cs:9 `public string Name {get;set;}` | :7 `public String Name;` | same | correct; `nvarchar not null` → String |
| status.cs:10 `public int SortOrder` | :8 `public int SortOrder;` | same | correct |
| status.cs:11 `public string Style` | :9 `public String Style;` | same | correct; nullable column → reference type, so DBNull→null as in EF |
| status.cs:12 `public int Default` | :10 `public int Default;` | §2A names [F-A18]: `Default` is not a Java keyword and must NOT get `_` | correct |
| status.cs:1-2 `using` directives | no imports | §4 explicit single-type imports only where needed. Nothing is needed (`String` is java.lang) | correct |
| — | :1 `// Source: src/BugTracker.Web/Models/status.cs` | §6.1 header | correct, repo-relative, first line |
| — | :12 `// PORT STATUS: confidence=high todos=0` | §6.7 trailer, last line. The file has 0 `TODO(port)` markers | correct count |

Behavioral notes: primitive `int` for `Id`/`SortOrder`/`Default` makes the runtime throw the EF-style "could not be set to a 'null' value" on DBNull (DbSet.java:301-305), matching EF6 for non-nullable `int` properties. The class has no constructor, so the implicit public no-arg constructor satisfies `getDeclaredConstructor().newInstance()`. No initializers exist in the source, so none are expected in the target. Field declaration order matches the source.

## Rulebook obedience

OBEDIENT. Every applicable rule was followed: §2A auto-property → public field, §2A names/[F-A18] (`Default` kept), §2A partial, §2A access modifiers [S2-12] (explicit `public` kept), §2A EF6 entities, §4 package/path/primary type/imports, §6.1 header, §6.2 completeness (all 5 members, no source comments to copy), §6.3 markers (none), §6.4 scope (single file), §6.7 trailer. There are no inventory citations to make (§6.5), because no rows exist.

## Rulebook gaps

None that affect this unit. (Observation only: §5 calls a site missing from the inventory "an inventory bug", but it does not say plainly that a unit decided entirely by general §2A rules is expected to have zero rows. This review read it that way. A one-line clarification in §5 would stop reviewers and implementers from flagging pure-POCO entity units such as `status.cs` and `priority.cs` as inventory bugs.)

## Counts

HIGH 0 / MEDIUM 0 / LOW 0 — Obedience: OBEDIENT
