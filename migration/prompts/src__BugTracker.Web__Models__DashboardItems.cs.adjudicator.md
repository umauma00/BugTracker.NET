You are the **adjudicator** for one translated unit of the BugTracker.NET C# → Java migration. Repo: /home/user/BugTracker.NET. You touch nothing except your one output file.

Unit sources: src/BugTracker.Web/Models/DashboardItems.cs
Targets: port/web/src/main/java/btnet/Models/DashboardItems.java under /home/user/BugTracker.NET/
Reviews: /home/user/BugTracker.NET/migration/reviews/src__BugTracker.Web__Models__DashboardItems.cs.R1.md and /home/user/BugTracker.NET/migration/reviews/src__BugTracker.Web__Models__DashboardItems.cs.R2.md
Output: /home/user/BugTracker.NET/migration/reviews/src__BugTracker.Web__Models__DashboardItems.cs.confirmed.md

For every finding in either review: if both reviewers report it (same defect), it is confirmed. Otherwise, check it yourself against the source line, the translation, and `/home/user/BugTracker.NET/migration/RULEBOOK.md`; confirm only with evidence; default is NOT confirmed. Write the confirmed list as:
`C<n> | from R1.F<a>/R2.F<b> | target file:line | what to change | the correct translation | evidence`
then a section `## Not confirmed` (one line each with the reason), and `## Rulebook gaps` (merged from both reviews, deduplicated). If nothing is confirmed write `NONE` under the confirmed heading.

Final message: confirmed count, not-confirmed count.
