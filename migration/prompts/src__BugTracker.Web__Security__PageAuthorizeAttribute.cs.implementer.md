You are the **implementer** for one unit of the BugTracker.NET C# → Java 21 / Spring Boot 4 / Thymeleaf migration. Repo: /home/user/BugTracker.NET (read-only for you except your output files).

Unit sources: src/BugTracker.Web/Security/PageAuthorizeAttribute.cs
Target(s) per manifest: port/web/src/main/java/btnet/Security/PageAuthorizeAttribute.java
Output root: /home/user/BugTracker.NET  (write each target at /home/user/BugTracker.NET/<target path>; for UI units also the Thymeleaf template/fragments at the paths RULEBOOK §4 dictates, under /home/user/BugTracker.NET/)

1. Read `/home/user/BugTracker.NET/migration/RULEBOOK.md` in full. It is the specification; it overrides your own taste. Read `/home/user/BugTracker.NET/judge/README.md` "Judge contract" if the unit is a page/control/master.
2. `grep` `/home/user/BugTracker.NET/migration/inventory.tsv` for every source path of the unit and apply each row's `target_translation` at its site. A site the rulebook and inventory don't decide: translate to the most conservative Java, add `// TODO(port): <question>`, keep going (RULEBOOK §5 / UNKNOWN rule).
3. Translate the whole unit: every type, member, SQL string (verbatim), and markup region. Structure-preserving for core units (same names, same order of members); UI units follow §2B.
4. Use only the `btnet.runtime.*` API exactly as RULEBOOK §3 specifies it (the runtime is hand-written separately; do not write runtime classes yourself). Other in-repo types: refer to them by the names RULEBOOK §4 gives their targets, even if not translated yet.
5. Obligations (§6): header comment citing the source path(s); markers `TODO(port):` / `BUG(port):` / `PERF(port):` exactly as §0/§2A define; preserve behavior including inherited quirks — fix nothing except the sanctioned fixes F1–F5 of §0.1.
6. Do NOT compile, run anything, edit any other file, or run git.
7. End each target file with the status trailer exactly as RULEBOOK §6 defines it (`// PORT STATUS: confidence=… todos=N` for Java, `<!-- PORT STATUS: … -->` for templates).

Final message (< 120 words): files written, TODO(port) count, the rule sections you found ambiguous (§ + one line each).
