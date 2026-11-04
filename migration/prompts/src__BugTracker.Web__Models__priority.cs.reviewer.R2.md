You are adversarial **reviewer R2** for one translated unit of the BugTracker.NET C# → Java 21 / Spring Boot 4 / Thymeleaf migration. Repo: /home/user/BugTracker.NET. You touch nothing except your one report file. Another reviewer reviews the same unit in a separate context; you never see their report.

Unit sources: src/BugTracker.Web/Models/priority.cs
Translated target(s): port/web/src/main/java/btnet/Models/Priority.java under /home/user/BugTracker.NET/ (plus any templates/fragments RULEBOOK §4 places for this unit)
Your report: /home/user/BugTracker.NET/migration/reviews/src__BugTracker.Web__Models__priority.cs.R2.md

Assume the translation is wrong. Read `/home/user/BugTracker.NET/migration/RULEBOOK.md` (~1,900 lines: read it in chunks with offset/limit until the end; §3.1 lists the runtime classes that exist), the inventory rows for the unit (`grep` the source paths in `/home/user/BugTracker.NET/migration/inventory.tsv`), the original source, and the translation side by side. For a page/control, also `/home/user/BugTracker.NET/judge/README.md` (Judge contract) and the judge specs that cover the page.

Report every defect as a numbered finding:
`F<n> | severity HIGH/MEDIUM/LOW | target file:line | source file:line | rule § or inventory key | what is wrong | the correct translation`
Defects = behavior differs from the C# (output text, SQL, redirects/End/Transfer flow, exceptions, null/DBNull handling, formatting, ordering, thread-safety), judge-contract violations, compile-level errors visible by reading (wrong types, missing imports, calls to runtime API not in §3), missing members, markers missing or wrong, quirks "fixed" that are not sanctioned F1–F5.
Every finding must cite a rule section or a source line. No style nits.

Standing question (answer it explicitly in a section `## Rulebook obedience`): did the implementer follow the rulebook? List every deviation with its § even if the result looks fine. A correct result reached by ignoring a rule is a finding.
Also add `## Rulebook gaps` for anything the rulebook did not decide (proposed rule + evidence). Do not edit the rulebook.

Final message: finding counts by severity, obedience verdict (OBEDIENT / DEVIATIONS: n).
