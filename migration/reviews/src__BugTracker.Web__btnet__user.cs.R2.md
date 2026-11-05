# Review R2 — src/BugTracker.Web/btnet/user.cs → port/web/src/main/java/btnet/User.java

Reviewer: R2 (adversarial, read-only). Inputs read: RULEBOOK.md (all of it, §0–§7
incl. §3.1), inventory row `src/BugTracker.Web/btnet/user.cs L137`, the source,
the target, and the runtime/app signatures the target calls (`Strings.Split`,
`Strings.Trim`, `Strings.ToLower`, `DataRow.get`, `ArgumentException`,
`Collections2.set`, `Convert.*`, `btnet.Security.PermissionLevel`,
`Util.get_setting/get_custom_columns/write_to_log`,
`DbUtil.execute_nonquery/get_dataset/execute_scalar`, `SQLString`). This is a core
unit, so there is no template and no judge spec to check.

## What I verified (no finding)

- **SQL text is byte-identical.** I extracted all three literals by script.
  The source file mixes CRLF and LF line ends, but the two verbatim literals
  (`user.cs:213-218`, `:232-318`) contain no CR. Both Java concatenations
  (`User.java:221-226`, `:240-326`) decode to exactly the same characters,
  including the trailing blanks (`us_org `, `og_domain       `,
  `@ORG_COLUMNS        `, `select `) and the tab indents. The literal
  `alter table orgs add [@name] int null` is also unchanged.
- **Every member is present.** That covers all 38 fields with the same
  initializers, `set_from_db` and `copy_user`, and the source comments are
  copied over (§6.2).
- **The catch matches inventory row L137.** `catch (RuntimeException ex)`
  (C11) carries the `// inventory: src/BugTracker.Web/btnet/user.cs L137`
  citation (§6.5). `DataRow.get` throws runtime `ArgumentException extends
  DotNetException` (RuntimeException), so the ALTER path is still reached.
- **The core conversions follow §2A:**
  - `Convert.IsDBNull(obj)` → `obj == DBNull.Value`.
  - `(int)`/`(String)` casts are kept as Java unboxing casts.
  - `==` on strings → `Objects.equals`.
  - `ToLower` → `Strings.ToLower` and `Trim` → `Strings.Trim`.
  - `Split('@')` → `Strings.Split(email, '@')`. This resolves to
    `Split(String, char...)`, the most specific of the variable-arity
    candidates, so it compiles.
  - `ex.Message` → `Exceptions.Message(ex)`.
  - Every non-literal concatenation operand is wrapped in `Strings.cs`
    (S1-46, S2-18).
  - `StringBuilder.Append` follows S2-3.
  - The dictionary write uses `Collections2.set` with a `LinkedHashMap`
    instance.
  - `foreach DataRow` → `for (DataRow … : ….Rows())`.
- **Header, trailer and imports are correct.** The header is
  `// Source: src/BugTracker.Web/btnet/user.cs` (§6.1). The trailer reads
  `todos=0`, and the file has no `TODO(port)`, so the count is right. Every
  import is in §3.1 or is an app type. There are no wildcard or static
  imports (§4).

## Findings

F1 | severity MEDIUM | port/web/src/main/java/btnet/User.java:240-265,348 | src/BugTracker.Web/btnet/user.cs:252,257,340 | §2A "The BUG rule"; §3 marker formats; §5 (site missing from inventory) | `@ORG_COLUMNS` is bound as a SQL parameter (`AddParameterWithValue("ORG_COLUMNS", …)`), not spliced into the text. Inside the batch it appears as `og_domain\n @ORG_COLUMNS\n )` in an INSERT column list, and as `@domain\n @ORG_COLUMNS\n from` in the select list. T-SQL rejects a variable in a column list, and a syntax error stops the whole batch from compiling. So `copy_user` should throw `SqlException` ("Incorrect syntax near '@ORG_COLUMNS'") on **every** call, whatever `use_domain_as_org_name` is. This is from reading the SQL; it has not been run (D-001). The callers are reachable: `complete_registration.aspx.cs:49`, `loginnt.aspx:140`, `Mail/Mime.cs:157`. The port reproduces the defect, which is correct (it is not one of §0.1 F1–F5). But the site has no `BUG(port)` marker, and no inventory row covers it. | Keep the SQL and the parameter exactly as they are. Above `sql = sql.AddParameterWithValue("ORG_COLUMNS", org_columns.toString());` (or at the `new SQLString` of the batch), add `// BUG(port): @ORG_COLUMNS is bound as a SqlParameter instead of being spliced into the SQL text, so the batch fails to compile ("Incorrect syntax near '@ORG_COLUMNS'") and copy_user throws on every call — repro: complete_registration.aspx with a valid registration link (or loginnt.aspx auto-create / email-created user via Mime.cs:157)`. Report the missing inventory row in the batch report (§5). `todos=` stays 0.

F2 | severity LOW | port/web/src/main/java/btnet/User.java:150-152 | src/BugTracker.Web/btnet/user.cs:142-144 | §2A "The BUG rule" (latent clause, S2-28); §3 marker formats | In `alter table orgs add [@name] int null`, the bracketed `[@name]` is a quoted identifier, not a parameter reference, so the statement adds a column literally named `@name`, and the `@name` parameter is never used. (Compare the correct form at `add_customfield.aspx.cs:204`, `[og_$nm_field_permission_level]`.) The first missing custom-field column adds `@name`. Every later attempt fails with a duplicate-column `SqlException` that is not caught here, so the error escapes `set_from_db`. `set_from_db` has no caller in `src/`, so the defect is latent. S2-28 requires latent defects to be reproduced **and** marked. The reproduction is correct, but the marker is missing. | Keep the code unchanged and add above line 150: `// BUG(port): latent: [@name] is a quoted identifier, not the parameter — adds a column literally named "@name", and the second missing column fails with a duplicate-column SqlException — repro: none today (set_from_db has no caller)`.

Counts: HIGH 0, MEDIUM 1, LOW 1.

## Rulebook obedience

**The implementer did not fully obey the rulebook. DEVIATIONS: 2.**

1. §2A "The BUG rule" / §3 markers: a reachable source defect has no
   `BUG(port)` marker (F1, `copy_user` `@ORG_COLUMNS`). §5 also says to
   report a site missing from the inventory, and that was not done.
2. §2A "The BUG rule", latent clause [S2-28]: a latent source defect has no
   `BUG(port): latent:` marker (F2, `[@name]`).

Everything else follows the rulebook: §2A General, Strings, Convert,
Exceptions (C11, S1-10), DataSet, Collections2 (S1-28), §4 naming, imports
and path, §6.1–6.5 and §6.7. The one open point is the declared type of
`dict_custom_field_permission_level` (`Map<String,Integer>` rather than
`LinkedHashMap<String,Integer>`). The rulebook does not clearly decide it
(see the first gap below), so I have not counted it as a deviation.

## Rulebook gaps

- **Declared type for a concrete `Dictionary<K,V>` field or local.** §2A
  says `Dictionary<K,V>` → `LinkedHashMap` and `IDictionary<K,V>` →
  `java.util.Map` ("the instance stays `LinkedHashMap`, per the row above").
  It never says whether a field *declared* as `Dictionary<K,V>`
  (`user.cs:60`) keeps `LinkedHashMap` as its declared type or becomes
  `Map`. This implementer chose `Map<String, Integer>`; another could choose
  `LinkedHashMap<String, Integer>`. The meta-rule applies because two agents
  could differ, and the choice shows in member signatures that other units
  compile against. *Proposed rule:* "A C# declared type `Dictionary<K,V>` /
  `List<T>` / `HashSet<T>` → Java declared type `Map<K,V>` / `List<T>` /
  `Set<T>`. The instance is `LinkedHashMap` / `ArrayList` / `LinkedHashSet`."
  (Or the reverse, but one of them.)
- **Defects in SQL that the app's parameter binding causes.** Neither the
  rulebook nor §0.1 nor the inventory lists SQL that is broken because
  `SQLString` binds a value as a parameter where the original code spliced
  text: `@ORG_COLUMNS` used as a column list (F1), and `[@name]` used as a
  quoted identifier (F2). These are judge-visible (registration and LDAP
  auto-create) and easy to miss. Unless the reviewer catches it, each
  implementer decides alone whether to mark them. *Proposed:* add inventory
  rows `src/BugTracker.Web/btnet/user.cs L340` (`@ORG_COLUMNS` → BUG(port),
  reproduce) and `L142` (`[@name]` → BUG(port) latent, reproduce). Also
  prompt the human to decide at the next gate whether `copy_user` joins
  §0.1 as a sanctioned fix, since as written self-registration is
  unreachable in both the original and the port.
