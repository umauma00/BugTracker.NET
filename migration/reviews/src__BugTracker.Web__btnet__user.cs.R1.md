# Review R1 — src/BugTracker.Web/btnet/user.cs → port/web/src/main/java/btnet/User.java

Reviewer: R1 (adversarial, read-only). Date: 2026-11-04.
Inputs read: RULEBOOK.md (all 1,884 lines), inventory row `src/BugTracker.Web/btnet/user.cs L137` (the only row for this unit),
source `src/BugTracker.Web/btnet/user.cs` (346 lines), target `port/web/src/main/java/btnet/User.java` (354 lines),
`src/BugTracker.Web/btnet/SQLString.cs`, the translated `SQLString.java`, `Util.java`, `DbUtil.java`,
`Security/PermissionLevel.java`, and runtime `Strings`, `Convert`, `Collections2`, `DataRow`, `ArgumentException`.
This is a core unit (no template, no judge page), so judge specs do not apply.

## Verified correct (no finding)

- **SQL text is byte-identical.** I pulled both `@"…"` literals out of the C# and the concatenated Java literals and compared them with a script: 171/171 and 2074/2074 chars, equal. The source lines 191–346 are LF (the file mixes CRLF and LF, but the literals sit in the LF part), so `\n` is right (C24). Trailing spaces (`us_org `, `og_domain       `, `@ORG_COLUMNS        `, `select `) and tabs are kept.
- Every member is there: 33 fields with the same initializers, `set_from_db`, `copy_user` (§6.2). Names, `public` modifiers, and the class name are unchanged.
- Casts `(int)`/`(String)` on `dr.get(…)` match §2A (`(int)o`, `(string)o`): DBNull, Short, and the rest fail the same way.
- `Convert.IsDBNull(obj)` becomes `obj == DBNull.Value`, as the §2A Convert table says.
- Dictionary indexer writes use `Collections2.set` (§2A, S1-28).
- String `==` becomes `Objects.equals`. `ToLower` becomes `Strings.ToLower` (S1-8). `Trim` becomes `Strings.Trim`. `Split('@')` becomes `Strings.Split(email, '@')`, which throws NPE on null just as C# does.
- Every concatenation operand that is not a literal is wrapped in `Strings.cs` (S1-46/S2-18). `StringBuilder.Append` of a non-literal becomes `append(Strings.cs(…))` (S2-3).
- `ex.Message` becomes `Exceptions.Message(ex)` (S1-10). `catch (Exception ex)` becomes `catch (RuntimeException ex)` (C11, inventory L137), and the inventory citation comment is present (§6.5). The runtime `DataRow.get` throws `btnet.runtime.ArgumentException extends DotNetException` (RuntimeException) for an unknown column, so the load-bearing catch still works.
- Imports are explicit single-type imports, all used, and every class exists in §3.1. `PermissionLevel` is in `btnet.Security`. `SQLString`/`DbUtil`/`Util` signatures match the calls: `AddParameterWithValue(String,Object)` returns `SQLString`, `Append(String)`, `get_custom_columns()` returns `DataSet`, `execute_nonquery(SQLString)`, `execute_scalar(SQLString)` returns `Object`, `get_dataset(SQLString)`.
- The header `// Source:` (§6.1), the copied source comments, and the trailer `// PORT STATUS: confidence=high todos=0` are right. The file has 0 `TODO(port)` markers, so the count is correct.
- No §0.1 fix was applied, and nothing was "improved".

## Findings

F1 | severity MEDIUM | port/web/src/main/java/btnet/User.java:240-269 (copy_user SQL, `og_domain … @ORG_COLUMNS` at :259-260 and :264-265) | src/BugTracker.Web/btnet/user.cs:251-257, 340 | RULEBOOK §2A "The BUG rule" | The source has a real defect, and the port reproduces it (correctly) but does not mark it. `@ORG_COLUMNS` is bound as a **parameter** (`AddParameterWithValue("ORG_COLUMNS", …)`, :340). SQLString does no text substitution (`SQLString.cs:31-38`, and §2A "SQL text is never rewritten"). So the batch contains `og_domain @ORG_COLUMNS )` in an INSERT column list and `@domain @ORG_COLUMNS from` in a select list. Both are T-SQL syntax errors, and they break the **whole batch** at compile time whatever the value of `@use_domain_as_org_name`. Result: every `copy_user` call throws `SqlException("Incorrect syntax near '@ORG_COLUMNS'.")`. This path is reachable today: `complete_registration.aspx.cs:49` (self-registration) and `Mail/Mime.cs:157` (auto-register from email). The BUG rule requires a marker on a reproduced source defect. None is present, and none of judge-report §5, the inventory, or the stress-test reports records this defect. | Keep the SQL byte-identical and add, at the `sql = new SQLString(` site (:240), `// BUG(port): @ORG_COLUMNS is bound as a SQL parameter, not substituted into the text, so the batch never compiles ("Incorrect syntax near '@ORG_COLUMNS'") and every copy_user call throws SqlException — repro: complete_registration.aspx with a valid emailed link (or POP3 auto-register via Mime.cs:157)` |

F2 | severity LOW | port/web/src/main/java/btnet/User.java:150-152 | src/BugTracker.Web/btnet/user.cs:142-144 | RULEBOOK §2A "The BUG rule" (latent form, S2-28) | Another reproduced source defect with no marker. `alter table orgs add [@name] int null` puts `@name` inside a bracket-quoted identifier, so the parameter is never used. The first missing custom column adds a column literally named `@name` to `orgs`, not `og_<name>_field_permission_level`. Every later miss fails with a SqlException (duplicate column name `@name`). That exception is thrown inside the catch body, so it escapes `set_from_db`. Compare `add_customfield.aspx.cs:204`, which does text substitution (`[og_$nm_field_permission_level]`). `set_from_db` has no caller anywhere in `src/` (grep), so the defect is latent. S2-28 still requires the marker, with a `latent:` prefix. | Keep the SQL and add `// BUG(port): latent: [@name] is a quoted identifier, so the ALTER adds a column literally named "@name" (not og_<name>_field_permission_level) and a second miss throws SqlException from inside the catch — repro: none today (User.set_from_db has no caller)` |

F3 | severity LOW | port/web/src/main/java/btnet/User.java:67 | src/BugTracker.Web/btnet/user.cs:60 | RULEBOOK §2A General conventions, row "`List<T>` / `Dictionary<K,V>` … → `ArrayList` / **`LinkedHashMap`**" (with the separate row "Declared type `IDictionary<K,V>` → `java.util.Map`") | The field's C# declared type is the concrete `Dictionary<string,int>`, not `IDictionary`. The rulebook maps `Dictionary<K,V>` to `LinkedHashMap` and saves the `Map` interface type for a declared `IDictionary` (S1-28: "the instance stays `LinkedHashMap`"). The implementer declared the field as `Map<String, Integer>`. Behavior is the same and there are no external readers today, but it is an unsanctioned change to a public field's type. The precedent unit `WebApiConfig.java:14` declares `LinkedHashMap`. | `public LinkedHashMap<String, Integer> dict_custom_field_permission_level = new LinkedHashMap<>();` (and drop the now-unused `java.util.Map` import) |

Counts: HIGH 0, MEDIUM 1, LOW 2.

## Rulebook obedience

The implementer **did not fully follow** the rulebook. The translation is behavior-faithful statement by statement, but there are three deviations:

1. §2A "The BUG rule": a reproduced, reachable source defect (`copy_user`'s `@ORG_COLUMNS` batch never compiles) has no `BUG(port)` marker (F1).
2. §2A "The BUG rule" / S2-28 (latent form): a reproduced latent defect (`[@name]` quoted identifier in the auto-ALTER) has no marker (F2).
3. §2A General conventions, `Dictionary<K,V>` → `LinkedHashMap`: the declared type became `Map` (F3).

Everything else checks out: §0.1, C24, C11, S1-8, S1-10, S1-28, S1-46, S2-3, S2-12, S2-18, §3.1 imports, §4 imports/naming, §6.1–6.7, and the inventory row L137 (applied and cited).

## Rulebook gaps

- **G1. Declared type of a concrete `Dictionary`/`List` field or local.** §2A has an explicit row for declared `IDictionary` (→ `Map`) and for `IEnumerable`/`ICollection`/`IList` (→ `List`). For a declared concrete `Dictionary<K,V>`/`List<T>` the reader has to infer the declared type from the instance-mapping row, so two implementers could differ (this unit: `Map`; WebApiConfig: `LinkedHashMap`). Proposed rule: "A C# declared type `Dictionary<K,V>` / `List<T>` / `HashSet<T>` is declared as `LinkedHashMap<K,V>` / `ArrayList<T>` / `LinkedHashSet<T>`; only interface-typed C# declarations (`IDictionary`, `IList`, …) use the Java interface." Evidence: user.cs:60, search.aspx.cs:31,34, Mail/email.cs:312.
- **G2. SQL parameters used as text macros.** The SQLString refactor left sites where a `@name` parameter is meant as a text substitution (an identifier inside `[...]`, a column-list fragment). Under SqlClient (and the port's `SqlBatch`) these are syntax errors or wrong identifiers. Neither the rulebook nor the inventory tags them, so implementers silently reproduce them without markers. Proposed: an inventory tag `SQL_PARAM_AS_TEXT` with a mandated `BUG(port)` text, plus a one-time grep over `src/` for `\[@\w+\]` and for `@[A-Z_]+` parameters bound to SQL fragments (e.g. `AddParameterWithValue("ORG_COLUMNS", …)`). Evidence: user.cs:142-143, user.cs:252,257,340. Contrast add_customfield.aspx.cs:204, which uses `$nm` text replacement.
- **G3. The inventory row L137 describes the `[@name]` ALTER as working control flow.** It says the ALTER "triggers `alter table orgs add [@name] int null`" and does not record that it adds the wrong column. It needs a correction (inventory-side, for the human).
