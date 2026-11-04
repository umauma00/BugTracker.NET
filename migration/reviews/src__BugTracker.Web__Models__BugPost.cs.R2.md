# Review R2 — src/BugTracker.Web/Models/BugPost.cs

Target: `port/web/src/main/java/btnet/Models/BugPost.java` (25 lines). No template applies (§4: EF6 entity in `Models/`, not a page/master/.ascx/.inc), so the judge contract (§6.6) does not apply.

Inputs read: RULEBOOK §0 (core layer, structure-preserving), §2A General conventions (`partial class`, auto-property, `T?`/`Nullable<T>`, primitive table incl. `DateTime` → `java.time.LocalDateTime`, access modifiers [S2-12]), §2A EF6 (entities: auto-properties → public fields), §3.1, §4, §5, §6. Inventory: `grep "Models/BugPost.cs"` returns **no rows** for this file (rows 88-116 cover `Models/Mapping/BugPostMap.cs` and `BugPostAttachmentMap.cs` only). Cross-checked against `Models/Mapping/BugPostMap.cs:36-53` (column mapping), `Models/Context.cs:25` (`DbSet<BugPost>`) and `setup.sql:308-326` (`bug_posts` column types/nullability).

## Member-by-member check

| C# (line) | Java (line) | DB column (setup.sql) | Verdict |
|---|---|---|---|
| `int Id` (8) | `public int Id;` (8) | `bp_id int not null` | OK |
| `int BugId` (9) | `public int BugId;` (9) | `bp_bug int not null` | OK |
| `string Type` (10) | `public String Type;` (10) | `bp_type varchar(8) not null` | OK |
| `int UserId` (11) | `public int UserId;` (11) | `bp_user int not null` | OK |
| `DateTime Date` (12) | `public LocalDateTime Date;` (12) | `bp_date datetime not null` | OK — §2A primitive table; import `java.time.LocalDateTime` present (4). Default `null` vs C# `DateTime.MinValue` is inherent to the mandated mapping (no initializer in source) |
| `string Comment` (13) | (13) | `ntext not null` | OK |
| `string CommentSearch` (14) | (14) | `ntext null` | OK |
| `string FromEmail` (15) | (15) | `nvarchar null` | OK |
| `string ToEmail` (16) | (16) | `nvarchar null` | OK |
| `string File` (17) | `public String File;` (17) | `nvarchar null` | OK — `File` is not a Java keyword; no `java.io.File` import, so no clash |
| `Nullable<int> Size` (18) | `public Integer Size;` (18) | `bp_size int null` | OK — §2A `T?`/`Nullable<T>` → boxed |
| `string ContentType` (19) | (19) | `nvarchar null` | OK |
| `Nullable<int> ParentId` (20) | `public Integer ParentId;` (20) | `bp_parent int null` | OK — §2A |
| `Nullable<int> OriginalCommentId` (21) | `public Integer OriginalCommentId;` (21) | `int null` | OK — §2A |
| `int HideFromExternalUsers` (22) | `public int HideFromExternalUsers;` (22) | `int not null default(0)` | OK — stays `int` (not `boolean`), as source |
| `string CCEmail` (23) | `public String CCEmail;` (23) | `nvarchar null` | OK — casing preserved |

All 16 properties present, in source order, with source names and casing (§4, §6.2). Every field name matches the `this.Property("<name>")` strings the `BugPostMap` inventory rows (93-116) dictate, so the runtime `EntityTypeConfiguration` reflection will resolve each one. `public partial class` → `public class` (§2A `partial class`: one Java class; no other `partial class BugPost` part exists in `src/`). Package `btnet.Models` = namespace (§4). Unused `using System; using System.Collections.Generic;` correctly have no counterpart. No initializers, comments, constructors or navigation properties in the source.

## Findings

None.

Searched specifically for: `int` vs `Integer` mismatch on any of the three `Nullable<int>` fields or on the non-null `int` fields; `DateTime` mapped to anything other than `LocalDateTime` (e.g. `java.util.Date`, `Instant`, `OffsetDateTime`); `HideFromExternalUsers` "improved" to `boolean`; getters/setters instead of public fields (§2A auto-property, §2A EF6); renamed/camelCased members (`ccEmail`); reordered or missing members; a `java.io.File` import colliding with the field name; wildcard or static imports (§4); header path format (§6.1, [S1-41]); trailer as last line (§6.7); `todos=` undercount (0 `TODO(port)` markers in the file, `todos=0` correct).

## Rulebook obedience

OBEDIENT.
- §6.1 header `// Source: src/BugTracker.Web/Models/BugPost.cs` is line 1 — compliant.
- §6.2 completeness — 16/16 properties; no source comments to copy.
- §6.3 markers — none needed, none present.
- §6.4 scope — only the manifest target exists for this unit.
- §6.5 inventory citations — no inventory rows exist for this file, so no `// inventory:` comments are expected; none present (correct — citing a non-existent row would be wrong).
- §6.7 trailer `// PORT STATUS: confidence=high todos=0` is the last line — compliant.
- §2A auto-property → public field; `Nullable<T>` → boxed; `DateTime` → `LocalDateTime`; `partial class` → one class; §2A EF6 entity rule — compliant.
- §2A access modifiers [S2-12] — every member and the type carry explicit `public` in the source; kept — compliant.
- §4 explicit single-type import (`java.time.LocalDateTime`), no wildcard, no static import — compliant.

Note (not a deviation in the target file): §5 says a site missing from the inventory is an inventory bug the implementer must flag in its batch report. The three `Nullable<int>` sites (L18, L20, L21) have no inventory rows (see gap 1). The rulebook row decides them outright, so the UNKNOWN rule does not apply and the translation is correct; whether the implementer flagged the missing rows in its final message cannot be checked from the files on disk.

## Rulebook gaps

1. **`Nullable<T>` long-form sites are missing from the §2A count and from the inventory.** §2A says `` `T?` / `Nullable<T>` (7) `` with evidence `Models/BugFromEmail.cs:11-30`, and the inventory's nullable rows cover only those seven `int?` sites. A `grep "Nullable<" src/BugTracker.Web/Models/*.cs` finds many more long-form sites that the count missed: `BugPost.cs:18,20,21`, `BugTask.cs:21,24-29,31,32,35`, `BugUser.cs:12,14,16`, `CustomColumnsMetaData.cs:10`, `EmailedLink.cs:13,14`, `Votes.cs:9`, `bug.cs:31,34,37`, among others. Under §5 each of these is an "inventory bug" that every entity implementer would have to flag. Proposed amendment (§2A row + §5): "The `T?`/`Nullable<T>` row applies to every site in both spellings, including `Nullable<DateTime>` → `LocalDateTime` and `Nullable<decimal>` → `BigDecimal`. EF6 entity fields (`Models/*.cs`) need no inventory row; a missing row there is not an inventory bug." The other option is to add the missing rows to the inventory. Either way the count "(7)" should be corrected.
2. **Default value of non-nullable value-type fields.** C# `DateTime Date` defaults to `DateTime.MinValue`, while the mandated `LocalDateTime` field defaults to `null`. Any code that reads `Date` on an entity it built itself (not loaded from the DB) before assigning it would behave differently (NPE vs `1/1/0001`). The rulebook does not say whether such fields get an initializer. Proposed: "A non-nullable `DateTime` field/auto-property without an initializer stays uninitialized (`null`); a site that reads it before assignment → `TODO(port): DateTime default`." Evidence: `Models/BugPost.cs:12`; same pattern in other entities. No such read is known for `BugPost` (it is only reached via `Context.cs:25`), so this has low impact.
