# Review R2 — src/BugTracker.Web/Models/BugFromEmail.cs

Target: `port/web/src/main/java/btnet/Models/BugFromEmail.java` (36 lines). No template applies (§4: model class, not a page/master/.ascx/.inc), so the judge contract (§6.6) does not apply.

Inputs read: RULEBOOK §2A (General conventions, `T?`, `??`, auto-property, primitive table), §2A Web API/JSON (`[Required]` → `@Required`, Jackson PUBLIC_ONLY fields in declaration order), §3/§3.1 (`btnet.runtime.web.Required` listed and on disk: `@Retention(RUNTIME) @Target(FIELD)`), §4, §5, §6; inventory rows 76-82 (`NULLABLE_REQUEST_FIELD` for ProjectId L11, OrganizationId L25, CategoryId L26, PriorityId L27, StatusId L28, AssignedTo L29, UdfId L30).

## Member-by-member check

| C# (line) | Java (line) | Verdict |
|---|---|---|
| `int? ProjectId` (11) | `public Integer ProjectId;` (9) | OK — §2A `T?`, inventory L11 |
| `string Comment` (12) | `public String Comment;` (10) | OK |
| `[Required] string FromAddress` (14-15) | `@Required public String FromAddress;` (12-13) | OK — §2A Web API `[Required]` → `@Required`; import `btnet.runtime.web.Required` exists (§3.1), single-type import (§4) |
| `string CcAddress` (16) | (14) | OK |
| `string Message` (17) | (15) | OK |
| `byte[] Attachment` (18) | `public byte[] Attachment;` (16) | OK — §2A primitive table |
| `string AttachmentContentType/FileName/Description` (19-21) | (17-19) | OK |
| `int BugId` (22) | `public int BugId;` (20) | OK — non-nullable stays primitive, default 0 as C# |
| `string ShortDescription` (23) | (21) | OK |
| `int? OrganizationId/CategoryId/PriorityId/StatusId/AssignedTo/UdfId` (25-30) | `public Integer …` (24-34) | OK — inventory L25-L30 |

Declaration order preserved (matters for Jackson declaration-order serialization, §2A JSON [S2-34]). Names and casing unchanged (§4). Class `public` as source; package `btnet.Models` = namespace (§4). No initializers in source, none in target. Unused C# `using` directives have no counterpart and are correctly omitted.

## Findings

None. Searched specifically for: wrong nullability (int? vs int), a missing `@Required`, a wrong `Required` import (e.g. `jakarta.validation`), reordered fields, getters/setters instead of public fields, missing members, header/trailer format, inventory-citation format, and a `todos=` undercount (file has 0 `TODO(port)` markers; `todos=0` is correct).

## Rulebook obedience

OBEDIENT.
- §6.1 header `// Source: src/BugTracker.Web/Models/BugFromEmail.cs` is line 1 — compliant.
- §6.2 completeness — all 17 properties present; no comments in source to copy.
- §6.3 markers — none needed, none present.
- §6.4 scope — only the manifest target written.
- §6.5 inventory citations — each of the 7 inventory-decided fields carries `// inventory: src/BugTracker.Web/Models/BugFromEmail.cs L<n>` with the row's `file` column and `L<n>` prefix (L11, L25-L30) — compliant.
- §6.7 trailer `// PORT STATUS: confidence=high todos=0` is the last line — compliant.
- §2A auto-property → public field; `T?` → boxed; §2A Web API `[Required]` → `@Required` — compliant.
- §4 explicit single-type import, no wildcard, no static import — compliant.

## Rulebook gaps

1. **Dropped `using` directives.** The rulebook does not say what happens to C# `using` directives that have no Java use (`System.Linq`, `System.Web`, `System.ComponentModel.DataAnnotations` here). Proposed rule (§4 Imports): "C# `using` directives are not translated; Java imports are derived from the types the translation actually references." Evidence: `Models/BugFromEmail.cs:1-5`; the implementer did this implicitly. Low impact.
2. **Where `[Required]` lives.** The `[Required]` → `@Required` mapping is only stated in the Web API `[FromBody]` bullet, not in the §2A General conventions attribute row (which only covers `[PageAuthorize]`-style application attributes). Proposed: add a §2A row "DataAnnotations attributes on model properties: `[Required]` → `btnet.runtime.web.Required` (field annotation); any other DataAnnotations attribute → `TODO(port)`". Evidence: `Models/BugFromEmail.cs:3,14`; RULEBOOK §2A attribute row vs Web API bullet.
