# Review R1 — src/BugTracker.Web/Models/BugFromEmail.cs

Target: `port/web/src/main/java/btnet/Models/BugFromEmail.java` (core layer, §0; no template — §4 assigns templates to UI units only).

Inputs read: `migration/RULEBOOK.md` (§0, §2A general conventions incl. auto-property / `T?` / primitive rows, §2A Web API + JSON rows, §3.1, §4, §5, §6), inventory rows for `Models/BugFromEmail.cs` (L11, L25-L30, all `NULLABLE_REQUEST_FIELD`, confirmed) plus the `BugFromEmailController.cs` rows that read the model, the source, the target, and runtime `port/runtime/src/main/java/btnet/runtime/web/Required.java`.

## Member-by-member check

| Source line | C# | Java (target line) | Verdict |
|---|---|---|---|
| 7, 9 | `namespace btnet.Models`, `public class BugFromEmail` | L2 `package btnet.Models;`, L6 `public class BugFromEmail` | OK (§2A namespace row, §4 path) |
| 11 | `int? ProjectId` | L9 `public Integer ProjectId;` + L8 inventory cite | OK (§2A `T?`, auto-property; inventory L11) |
| 12 | `string Comment` | L10 `public String Comment;` | OK |
| 14-15 | `[Required] string FromAddress` | L12-13 `@Required public String FromAddress;` | OK (§2A Web API `[Required]` → `@Required`; runtime annotation is `@Target(FIELD)`, `AllowEmptyStrings=false` default = .NET default; listed in §3.1) |
| 16-17 | `string CcAddress`, `string Message` | L14-15 | OK |
| 18 | `byte[] Attachment` | L16 `public byte[] Attachment;` | OK (§2A primitives row; base64 binding is the JsonConfig's job) |
| 19-21 | `AttachmentContentType`, `AttachmentFileName`, `AttachmentDescription` | L17-19 `String` | OK |
| 22 | `int BugId` | L20 `public int BugId;` | OK (non-nullable `int`, default 0 = C# default) |
| 23 | `string ShortDescription` | L21 | OK |
| 25-30 | `int? OrganizationId, CategoryId, PriorityId, StatusId, AssignedTo, UdfId` | L24-34 `Integer`, each with `// inventory: … L25`…`L30` | OK (inventory L25-L30) |

- Member count: 17 source auto-properties, 17 Java public fields; names, casing and declaration order identical (order matters for Jackson declaration-order serialization, §2A JSON row [S2-34] — preserved).
- No initializers in source; none added.
- Imports: single explicit `btnet.runtime.web.Required` (§4 import rule). Unused C# `using` directives produce no imports — nothing in the class needs `java.util` etc.
- Header L1 `// Source: src/BugTracker.Web/Models/BugFromEmail.cs` — §6.1 OK.
- Trailer L36 `// PORT STATUS: confidence=high todos=0` is the last line; file contains zero `TODO(port)` markers — §6.7 count correct.
- Inventory citations §6.5: every row whose decision was applied is cited with the correct `<file> L<n>` (L11, L25, L26, L27, L28, L29, L30). No inventory row exists for the non-nullable members or `[Required]`; those decisions come from rule rows, which need no citation.
- No BUG(port) obligation: the class has no behavior; no §0.1 fix is relevant.
- Compile-level: `Integer`, `String`, `byte[]` are `java.lang`/primitive; `@Required` applied to a field matches its `@Target(ElementType.FIELD)`. No visible compile errors.
- Consumer compatibility: inventory rows for `BugFromEmailController.cs` L114-L136, L163-L165, L228-L230 assume `Integer` fields assigned back via `Nulls.coalesce(…)`, `String` attachment fields, and `int BugId` assigned from `Mime.get_bugid_from_subject` (L64) — all satisfied by the field types here.

## Findings

None. No behavior, contract, compile-level, completeness or marker defect found after a line-by-line comparison.

## Rulebook obedience

OBEDIENT. Checked rules: §0 core-layer structure preservation (same type name, same members, same names); §2A auto-property → public field; §2A `T?` → boxed type; §2A primitive mapping (`int`, `byte[]`); §2A Web API `[Required]` → runtime `@Required`; §3.1 (only runtime class used is listed); §4 path/package/import rules; §6.1 header; §6.2 completeness; §6.3 markers (none); §6.4 scope (single file); §6.5 inventory grep and citation format; §6.7 trailer and todo count. No deviations.

## Rulebook gaps

1. **`using` directives / unused imports are not addressed.** §4 says "explicit single-type imports" but nothing says C# `using` directives (here `System.Collections.Generic`, `System.Linq`, `System.Web`, `System.ComponentModel.DataAnnotations`) are simply dropped rather than mapped. The implementer's choice (drop; import only what Java needs) is the obvious one and harmless. Proposed rule (§4): "C# `using` directives are not translated; the Java file imports exactly the types its code references." Evidence: `Models/BugFromEmail.cs:1-5`.
2. **Inventory-citation placement for multi-line field groups is not specified.** §6.5 says to cite the row "in a comment" but not where. The implementer put each comment on the line right above the field it covers, which reads clearly. Proposed rule: "the `// inventory:` comment goes on the line immediately preceding the declaration/statement the row decides." Evidence: target L8, L23-L33.
