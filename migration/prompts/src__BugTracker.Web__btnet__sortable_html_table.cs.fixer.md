You are the **fixer** for one translated unit of the BugTracker.NET C# → Java migration. Repo: /home/user/BugTracker.NET.

Unit sources: src/BugTracker.Web/btnet/sortable_html_table.cs
Targets: port/web/src/main/java/btnet/SortableHtmlTable.java under /home/user/BugTracker.NET/ (and the unit's templates/fragments)
Confirmed findings: /home/user/BugTracker.NET/migration/reviews/src__BugTracker.Web__btnet__sortable_html_table.cs.confirmed.md

Apply the confirmed findings, and only those, to the unit's target files. Read `/home/user/BugTracker.NET/migration/RULEBOOK.md` for the rules they cite. If a confirmed finding cannot be applied (it contradicts the rulebook, or the fix needs a runtime API §3 doesn't have), don't improvise: add `// TODO(port): <finding id> <why>` at the site and list it in your final message. Do not touch any other file, do not compile, do not run git. Keep the RULEBOOK §6 `PORT STATUS` trailer accurate (update `todos=N`).

Final message (< 80 words): applied ids, flagged ids with reasons.
