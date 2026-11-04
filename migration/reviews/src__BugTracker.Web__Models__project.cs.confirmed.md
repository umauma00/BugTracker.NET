# Confirmed findings — src__BugTracker.Web__Models__project.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

- G1 (informational, low): §2A's keyword rule (L130, "If the name is a Java keyword, append `_` (`default` → `default_`)") does not say outright that matching is case-sensitive. Members named `Default` (here `project.cs:27`, and `priority.cs`) are not Java keywords and correctly stay unrenamed. But a reader who matches case-insensitively could "fix" them to `Default_`, which would break the map units' string lookups (`this.Property("Default")`). Proposed addition to L130: "Matching is case-sensitive: `Default`, `Class`, `New` are not keywords and are kept." Evidence: `src/BugTracker.Web/Models/project.cs:27`, `port/web/src/main/java/btnet/Models/Priority.java:11`.

- Minor, no rule change needed for this unit: §2A's `T?` / `Nullable<T>` row has the survey count "(7)" and cites only `Models/BugFromEmail.cs`. Entity files such as `project.cs:11-14` add at least 4 more `Nullable<int>` sites. The mapping is still unambiguous. Proposed: refresh the count, or say the row covers every `Models/*.cs` entity property, so that a reviewer does not read the low count as limiting the rule's scope. Evidence: `project.cs:11-14`, RULEBOOK §2A line 151.
