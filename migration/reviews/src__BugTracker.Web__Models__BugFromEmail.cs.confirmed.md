# Confirmed findings — src__BugTracker.Web__Models__BugFromEmail.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

1. **`using` directives / unused imports are not addressed.** §4 says "explicit single-type imports" but nothing says C# `using` directives (here `System.Collections.Generic`, `System.Linq`, `System.Web`, `System.ComponentModel.DataAnnotations`) are simply dropped rather than mapped. The implementer's choice (drop; import only what Java needs) is the obvious one and harmless. Proposed rule (§4): "C# `using` directives are not translated; the Java file imports exactly the types its code references." Evidence: `Models/BugFromEmail.cs:1-5`.
2. **Inventory-citation placement for multi-line field groups is not specified.** §6.5 says to cite the row "in a comment" but not where. The implementer put each comment on the line right above the field it covers, which reads clearly. Proposed rule: "the `// inventory:` comment goes on the line immediately preceding the declaration/statement the row decides." Evidence: target L8, L23-L33.

1. **Dropped `using` directives.** The rulebook does not say what happens to C# `using` directives that have no Java use (`System.Linq`, `System.Web`, `System.ComponentModel.DataAnnotations` here). Proposed rule (§4 Imports): "C# `using` directives are not translated; Java imports are derived from the types the translation actually references." Evidence: `Models/BugFromEmail.cs:1-5`; the implementer did this implicitly. Low impact.
2. **Where `[Required]` lives.** The `[Required]` → `@Required` mapping is only stated in the Web API `[FromBody]` bullet, not in the §2A General conventions attribute row (which only covers `[PageAuthorize]`-style application attributes). Proposed: add a §2A row "DataAnnotations attributes on model properties: `[Required]` → `btnet.runtime.web.Required` (field annotation); any other DataAnnotations attribute → `TODO(port)`". Evidence: `Models/BugFromEmail.cs:3,14`; RULEBOOK §2A attribute row vs Web API bullet.
