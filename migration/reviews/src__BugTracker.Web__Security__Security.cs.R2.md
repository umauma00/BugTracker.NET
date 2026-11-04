# Review R2 — src/BugTracker.Web/Security/Security.cs

Target: `port/web/src/main/java/btnet/Security/Security.java` (core unit; no template, §4).
Inputs read: RULEBOOK.md (all 1,884 lines), inventory row `Security.cs L22`
(`migration/inventory.tsv:436`), the source, the translation, and the runtime / sibling
classes it calls (`btnet.runtime.security.{IOwinContext, AuthenticationManager, Claim,
ClaimTypes, ClaimsIdentity}`, `btnet.runtime.web.HttpRequest.GetOwinContext()`,
`btnet.runtime.Convert.ToString(Object)`, `btnet.Security.{PermissionLevel, BtnetClaimTypes,
BtnetRoles}`, `btnet.{DbUtil.get_datarow, SQLString.AddParameterWithValue, Util.get_setting}`).

## Findings

None. I assumed the translation was wrong and checked these points. None of them shows a defect:

- **SQL text (§2A @"verbatim" C24, §0.1 F4).** I checked it by machine. I concatenated the Java
  literal pieces, unescaped them, and compared the result with the C# verbatim literal
  (`Security.cs:21-35`, LF line ends, the trailing space after `inner join orgs org`, the
  leading tabs at :33-34) after applying exactly the F4 insertion
  (`,\n       (select count(1) from project_user_xref pa where pa.pu_user = u.us_id and pa.pu_admin = 1) [project_admin]`
  after `u.us_admin`). The two strings are **byte-identical**. No text block is used. The F4
  site carries `// FIXED(port): F4 — see RULEBOOK §0.1`. The read of line 117,
  `(int) dr.get("project_admin") > 0`, is unchanged, as F4 requires.
- **Duplicate column (`inventory Security.cs L22`, J-C12).** The translation reads
  `dr.get("us_forced_project")` and checks it against DBNull at Java :97 (source :87). That
  reads the first, nullable column, as the row requires. The query itself was not reshaped.
- **Claims.** All 29 claims of the initializer are present, in source order and with the same
  type constants and source columns. That includes the duplicate `CanEditAndDeletePosts` and
  `CanEditAndDeleteBugs` claims, both read from `og_can_edit_and_delete_posts`, and the
  shadowed second `CanOnlySeeOwnReportedBugs` claim (§0.1 "Not fixed", J-C3). After the
  initializer, these follow in order: `CanAddBugs`, `ForcedProjectId`,
  `TagsFieldPermissionLevel`, then Admin or ProjectAdmin, then User. `ClaimsIdentity(claims,
  "ApplicationCookie", ClaimTypes.Name, ClaimTypes.Role)` matches the runtime's 4-argument
  constructor, which takes an `Iterable<Claim>`.
- **Casts and nulls (§2A Convert table, `(int)o`, `o == DBNull.Value`).** Every `(int)dr[..]`
  became `(int) dr.get(..)`, an unboxing cast that fails where C# fails. Both DBNull checks
  (:39, :81) map to `== DBNull.Value`. `Convert.ToString(bool)` gives `"True"`/`"False"`
  through `Convert.ToString(Object)`. When `get_datarow` returns null, Java throws an NPE where
  C# throws an NRE, which keeps the Error-recovery rule.
- **String compare (§2A Strings).** `Util.get_setting("EnableTags", "0") == "1"` became
  `Objects.equals(..., "1")`.
- **OWIN (B7, §2A runtime property convention).** `owinContext.Authentication.SignIn(identity)`
  became `owinContext.Authentication().SignIn(identity)`, and `request.GetOwinContext()` maps
  to the runtime `HttpRequest.GetOwinContext()`. `SignOut()` binds to the varargs
  `SignOut(String...)`. All of these exist in the runtime.
- **Imports (§3.1, §4).** Every import exists in §3.1 or is a translated application type.
  They are explicit single-type imports, with no wildcards and no static imports.
  `PermissionLevel`, `BtnetClaimTypes` and `BtnetRoles` are in the same package; I confirmed
  that their members exist with `int`/`String` types. `btnet.DbUtil` is written qualified, as
  in the source.
- **Per-file obligations (§6).** The header is `// Source: src/BugTracker.Web/Security/Security.cs`.
  Every member is present: `SignIn`, `GetIdentity`, `SignOut`. The source comment at :84-86 is
  copied. The inventory citation is in the §6.5 format. The trailer is
  `// PORT STATUS: confidence=high todos=0`, which is correct because the file has no
  `TODO(port)`. The markers use the §3 formats.

## Rulebook obedience

**OBEDIENT.** I found no deviations. Every construct maps to its canonical §2A/B7 form. The
only behavior change is the sanctioned F4, and it carries its marker. The latent duplicate
claim is reproduced and marked with the S2-28 `latent:` form.

## Rulebook gaps

1. **Collection initializer for `List<T>`.** S1-29 decides `Dictionary` initializers (construct,
   then one `Collections2.Add` per entry). It is silent on `new List<T> { a, b, … }`
   (`Security.cs:41-78`). The implementer wrote `new ArrayList<>(Arrays.asList(a, b, …))`.
   Another implementer could write `var claims = new ArrayList<Claim>(); claims.add(a); …`. The
   behavior is the same: left-to-right evaluation, the same first exception, insertion order.
   But two agents could answer differently. *Proposed rule:* "`new List<T> { e1, …, en }` →
   `new ArrayList<>(Arrays.asList(e1, …, en))` (declared type per C1); evaluation order and the
   first exception are those of C#."
2. **Marker on the §0.1 "Not fixed" examples.** §0.1 lists `Security.cs:73` as preserved. It
   does not say whether the site needs a `BUG(port)` marker. The implementer added
   `// BUG(port): latent: … — repro: none today …`, which is reasonable under S2-28 because
   the claim is unobservable. Another implementer could leave it unmarked, since the
   behavior is "specified" rather than defective. *Proposed rule:* "Each §0.1 'Not fixed' site
   carries `// BUG(port):` (with `latent:` when unobservable, S2-28)."
