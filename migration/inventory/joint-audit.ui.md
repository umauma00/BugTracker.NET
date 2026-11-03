# Joint audit — UI layer (rulebook §2B × inventory × judge contract)

Auditor: adversarial joint auditor (UI half; the core half is audited separately).
Date: 2026-11-03. Inputs: `migration/RULEBOOK.md` (C1–C35 applied), `judge/README.md`
"Judge contract", `judge/src/test/java/judge/core/{Form,Scenario,Web}.java`,
`judge/specs/S*.md`, `migration/inventory.tsv` (326 rows on markup/code-behind files),
and the source pages. Walked in full: `edit_bug.aspx`, `search.aspx`, `edit_user.aspx`,
`bugs.aspx` + `Controls/BugList.ascx`, `admin.aspx` (+ `LoggedIn.Master`,
`LoggedOut.Master`, `MainMenu.ascx`, `default.aspx` because the five pages depend on them).

Every conflict names the rule, the site, the wrong translation, and a recommended fix
(pre-approved). "Side" = where the fix lands: **rulebook** (§2B/§3 text, applied between
batches) or **inventory** (row edits/additions).

Summary: 18 conflicts — 13 rulebook-side, 3 inventory-side, 2 both. Five break judged
behavior or DB effects (J-U1, J-U3, J-U4, J-U6, J-U11).

| # | Severity | Kind | Site | Side |
|---|---|---|---|---|
| J-U1 | critical (judge S6 fails) | c, b | edit_user.aspx:376-401 grid radios | both |
| J-U2 | critical (45 regions/22 files) | c, d | regions inside `<script>`/attributes/start tags | rulebook |
| J-U3 | high (login page) | c, d | LoggedOut.Master:21-25; regions in masters/.ascx | rulebook |
| J-U4 | high (DB effects) | a | 5 `validateRequest="false"` pages missing from B3 | rulebook |
| J-U5 | medium | c | mbugs.aspx:102 GET server form | rulebook |
| J-U6 | high (DB effects) | c | edit_user.aspx.cs:511, edit_self.aspx.cs:259 textarea | rulebook |
| J-U7 | medium | d | `.ClientID` (search.aspx:129-164, bugs.aspx:67, 6 more) | rulebook |
| J-U8 | medium | c | search.aspx:246-247 + .cs:84,139; admin.aspx.cs:44-45 | rulebook |
| J-U9 | medium | c | backup_db.aspx:27 ID-less `onserverclick`; 5 more ID-less | both |
| J-U10 | medium | c, b | RadioButtonList, CheckBoxList, Label, `<title runat>`, case | rulebook |
| J-U11 | high (DB effects) | c | edit_self.aspx.cs:108-112,247; edit_bug.aspx.cs:1304 | rulebook |
| J-U12 | medium | c | edit_bug.aspx:113,239 declared `name="submit"` | rulebook |
| J-U13 | low | d | Global.asax.cs:46 `HttpContext.Current` at startup | rulebook |
| J-U14 | medium (confidence medium) | c | request validation granularity (B3) | rulebook |
| J-U15 | low | a | BugList.ascx:128 rows use `String.join`/`Iterable` | inventory |
| J-U16 | low | a | admin.aspx.cs:43, edit_queued_notifications.aspx:37 raw `new Thread` | inventory |
| J-U17 | low | a | install.aspx:32/46 C7 rows deviate from the C7 template | inventory |
| J-U18 | low | c | B4 select event validation vs. `SelectedValue` before `DataBind` (bugs.aspx.cs:67-76) | rulebook |

---

## J-U1 — Radio buttons inside a DataGrid TemplateColumn: wrong name, wrong value, no item state

- **Rules.** §2B B5 name row: "`asp:RadioButton` → `name` = `GroupName`, `value` = `ID`".
  B5 DataGrid row: "runtime `DataGrid` renders BoundColumn/HyperLinkColumn/ButtonColumn"
  (no TemplateColumn). B4: view state holds "(a) list items of DropDownList/ListBox/
  CheckBoxList/HtmlSelect, (b) control property set by code" (no DataGrid items).
- **Site.** `edit_user.aspx:376-401` (`MyDataGrid`, TemplateColumn with four
  `<asp:RadioButton GroupName="permissions" value="0|1|3|2" ID="none|readonly|reporter|edit">`,
  `Checked='<%# … %>'`); hidden `BoundColumn DataField="pj_id" Visible="False"` (:384).
  Same shape: `edit_user_permissions2.aspx:163-178`. The grid is bound only when
  `!IsPostBack` (`edit_user.aspx.cs:237-239`); on postback `update_project_user_xref`
  walks `MyDataGrid.Items`, `dgi.FindControl("none").Checked` and
  `dgi.Cells[1].Text` (`edit_user.aspx.cs:724-753`).
- **Wrong translation.** (1) There is no rule for rendering a TemplateColumn or its
  `<%# %>` attribute bindings. (2) Applying B5: every row renders `name="permissions"`
  with `value="none"`, so the whole grid is one radio group, and the value is the ID
  instead of the declared `value`. Judge `S6.savingReporterUserDowngradesReporterPermission`
  (`S6_AdminCrudTest.java:386-395`) asserts `f.get("permissions") == "3"`, which fails
  (the port gives `reporter`). On submit the port gets `permissions=…&permissions=…`, so no
  row matches, every project falls back to `DefaultPermissionLevel`, and project 2's
  level 0 becomes 2 (that test asserts 0). (3) Even with correct names, B4 does not keep
  DataGrid items, so on postback `MyDataGrid.Items` is empty and no
  `project_user_xref` change is written.
- **Fix (recommended).** Rulebook, B5 + B4 + §3 `DataGrid`:
  (a) Naming containers: a control inside a DataGrid item renders
  `name="<GridID>$ctl<NN>$<GroupName|ID>"` (NN = ASP.NET item numbering, 2-digit,
  header = `ctl01`, first item `ctl02`) and `id="<GridID>_ctl<NN>_<ID>"`. The logical
  name (after the last `$`) stays `permissions`, which satisfies judge contract 2, and each
  row is its own group (the judge's `Form` keeps one value list per real name).
  (b) `asp:RadioButton` `value` = the declared `value` attribute if present, else `ID`
  (.NET `RadioButton.ValueAttribute`). (c) Runtime `TemplateColumn`: the page supplies
  an item factory lambda that creates the template's controls per row. `<%# expr %>`
  property bindings become lambdas over the `DataRowView`
  (`rb.Checked((int) drv.get("pu_permission_level") == 0)`), run at `DataBind`.
  (d) DataGrid items persist in view state (cell texts, including `Visible=false`
  columns, and template child-control state). They are recreated on postback before
  posted values load, so `Items`, `FindControl`, `Cells[i].Text` work.
  Inventory: add rows for `edit_user.aspx:386-401` and `edit_user_permissions2.aspx:163-178`
  naming the four bindings and the factory.

## J-U2 — Code regions in non-element positions: C8 direct-write and `<th:block>` cannot apply

- **Rules.** B5: every region becomes `<th:block th:utext="${page.__renderN()}"/>` and
  `__renderN()` "writes … directly to the page's Response buffer … and returns `""`"
  [C8]. `<%= %>` field form also uses `<th:block th:utext>`.
- **Sites (top-level regions, i.e. not inside a larger balanced region).** 33 inside
  `<script>` content in 17 files, for example `search.aspx:19,23-138,141,148,164,170,366`,
  `bugs.aspx:66-67`, `edit_bug.aspx:317`, `BugList.ascx:128-129`, `MainMenu.ascx:123`,
  `admin.aspx:47`, `default.aspx:81`, `send_email.aspx:70-92`. 12 inside start tags or
  attribute values in 7 files: `edit_bug.aspx:19-22,27,50`
  (`data-bug-id="<%: … %>"`; :22 emits a whole attribute), `edit_attachment.aspx:7`,
  `edit_comment.aspx:33`, `edit_dashboard.aspx:71` (hidden `ses` value), `edit_task.aspx:23`,
  `tasks_frame.aspx:36`, `view_subscribers.aspx:223`.
- **Wrong translation.** Thymeleaf HTML mode parses `<script>` content as text, so a
  `<th:block>` there is emitted literally. A `<th:block>` cannot sit inside an attribute
  value or a start tag. If the translator falls back to `th:attr`/inlining, the C8 method
  writes to the buffer while Thymeleaf is still evaluating the tag or text node. The output
  then lands before `<div …` or before `search_suggest_min_chars = `, which produces broken JS
  and markup. Example: `bugs.aspx:66-67` (`enable_popups`, `asp_form_id`, used by
  `bug_list.js:134,143,222,424`) and `BugList.ascx:128` (`columnNames`, which drives the S2
  DataTable).
- **Fix (recommended).** Rulebook, B5 new row (C8 still applies to element-content
  regions): "A top-level region in a non-element position becomes
  `public String __textN()`. It **returns** the text it would write: literal text, plus
  `Strings.cs(x)` for `<%= %>` / `Response.Write(x)`, plus `HttpUtility.HtmlEncode` for
  `<%: %>`, built in a local `StringBuilder`, with no Response writes. Inside
  `<script>`/`<style>`, the whole element body (literal JS plus every region, in order)
  is one method `__scriptN()`, and the template is `<script …declared attrs…
  th:utext="${page.__scriptN()}"></script>`. This avoids `th:inline`, which would also
  rewrite literal `[[`/`[(` in the JS. An attribute-value region becomes
  `th:attr="<name>=${page.__textN()}"`, returning the complete attribute value. A start-tag
  region that emits a whole attribute (`edit_bug.aspx:22`) becomes one `th:attr` per
  attribute, with `null` meaning absent. A `Response.End()` in such a region →
  `TODO(port)` (none exists today)." Inventory: tag these 45 regions `TEXT_REGION`.

## J-U3 — Render regions in masters and user controls have no host class; LoggedOut.Master has none at all

- **Rules.** B5: regions become "`public String __renderN()`" methods of **the page**,
  with N = order in the source file. B6: `.ascx` fragments address controls as
  `${uc.<ID>}`. LoggedOut.Master's class `btnet.pages.LoggedOut` "is dead code", and its
  pages declare `public final LoggedIn Master = master(new LoggedIn())` with the LoggedOut
  layout.
- **Sites.** `LoggedOut.Master:21-25` (`Response.Write(Application["custom_logo"])`),
  which is used by `default.aspx` (the S1 login page, so every scenario passes through it)
  and `forgot.aspx`. `LoggedIn.Master:36` (`Scripts.Render`). `MainMenu.ascx:4,15,21-62,85,123`.
  `BugList.ascx:7-28,128-129`. `Footer.ascx:16`.
- **Wrong translation.** Region numbering is per source file, so `__render1` of
  LoggedIn.Master, MainMenu.ascx and the content page would collide on `page`. The
  LoggedOut layout's region must be called on the `master` object, but that object is a
  `LoggedIn` whose `__render1()` is the script bundle. The result is either the wrong
  output on the login page or a Thymeleaf property error (500) on `default.aspx`.
- **Fix (recommended).** Rulebook, B5/B6: "Region and `__condN`/`__textN` methods live on
  the class compiled from the markup file that contains them. Page → `${page…}`. Master →
  `${master.__renderN()}`. `.ascx` → `${uc.__renderN()}`. N counts per file."
  For LoggedOut.Master, mirror ASP.NET's generated `ASP.loggedout_master : btnet.LoggedIn`.
  `btnet.pages.LoggedOut` is translated as `extends LoggedIn`, keeping the
  `// BUG(port): markup Inherits btnet.LoggedIn` note. It holds LoggedOut.Master's
  regions and its own empty `Page_Load`, and its pages declare
  `public final LoggedIn Master = master(new LoggedOut())`. The `MainMenu` field it
  inherits is never rendered by the LoggedOut layout. The original has none, so mark
  `// BUG(port): MainMenu exists but is not rendered`.

## J-U4 — B3's ValidateRequest list misses five pages

- **Rule.** B3: "`ValidateRequest="false"` (11 pages: backup_db, change_password,
  default, edit_bug, edit_comment, edit_custom_html, edit_dashboard, manage_logs, mbug,
  search, send_email) … all other pages validate".
- **Sites.** `git_hook.aspx`, `hg_hook.aspx`, `svn_hook.aspx`, `translate.aspx` and
  `tropo_recording.aspx` all declare `validateRequest="false"` (lower-case `v`, which is why
  a case-sensitive grep missed them).
- **Wrong translation.** The port validates these pages. A commit message posted to a hook
  that contains `<b`, `</` or `&#` is a 500, so its `svn_revisions`/`git_commits`/
  `hg_revisions` rows are not written. `translate.aspx` 500s on any HTML comment text.
- **Fix (recommended).** Rulebook, B3: replace the closed list with "class annotation
  `@ValidateRequest(false)` iff the `@Page` directive has `ValidateRequest="false"`
  (attribute name and value case-insensitive). Today that is 16 pages: <list>."

## J-U5 — GET server forms: IsPostBack and form method

- **Rules.** B2 step 2: "`IsPostBack` = method is POST **and** the form contains
  `__VIEWSTATE` or `__EVENTTARGET`". B5 form row: `<form method="post" …>`, "Declared
  `action`/`target`/`enctype`/`onsubmit` kept" (not `method`).
- **Site.** `mbugs.aspx:102`: `<form … id="frm" method="get" action="mbugs.aspx"
  runat="server">`, with checkbox `only_mine` submitted by `onchange="submit_me()"`. The
  inline `Page_Load` reads `only_mine.Checked` (`mbugs.aspx:58`).
- **Wrong translation.** ASP.NET `DeterminePostBackMode` uses `Request.QueryString` for
  non-POST requests, so a GET with `__VIEWSTATE` is a postback and `only_mine` loads from
  the query. The port either forces `method="post"` (the URL and query parameters of the
  submit change, judge contract 1) or keeps GET and treats it as no postback. In the second
  case `only_mine.Checked` is always false and the "only mine" filter never applies.
- **Fix (recommended).** Rulebook, B2 step 2 and B4: "Postback collection = `Form` for
  POST, else `QueryString`. `IsPostBack` iff that collection contains `__VIEWSTATE` or
  `__EVENTTARGET` (and the request is not a Transfer target). B4 posted values are read
  from that collection." B5 form row: "declared `method` kept (default `post`)".

## J-U6 — `HtmlTextArea`: posted text never reaches `InnerText`

- **Rule.** B4: "posted values are applied: inputs/textarea → `Value`". View state also
  restores "`InnerText`, `InnerHtml` … set by code".
- **Sites.** `edit_user.aspx.cs:308,316` set `signature.InnerText` on GET, and :511 writes
  `signature.InnerText` to `us_signature`. Same pattern in `edit_self.aspx.cs:123,259`.
- **Wrong translation.** On postback `InnerText` comes back from view state (the **old**
  signature) and the posted text goes only to `Value`. A user who edits their signature
  silently keeps the old one. The judge cannot see this, because the seeded signatures are
  empty and S6 posts `""`.
- **Fix (recommended).** Rulebook, B4 + §3 `HtmlTextArea`: "`Value`, `InnerText` and
  `InnerHtml` of `HtmlTextArea` are views of one content state, as in .NET
  (`HtmlTextArea.Value` ⇄ `InnerHtml`; `InnerText` = decoded). The posted value sets that
  state." `TODO(port)` in the runtime only, to pin the exact encode/decode against
  .NET 4.5.

## J-U7 — `.ClientID` has no rule, and the form's rendered id differs from its `ID`

- **Rules.** B5: element `id` = "exactly the source `ID`" and server form →
  `id="aspnetForm" name="aspnetForm"` on master pages. No rule maps `x.ClientID`.
- **Sites.** `search.aspx:129-135,141,148,164` (`<%=searchForm.ClientID%>`, `project`, …).
  `bugs.aspx:67` (`asp_form_id = '<%=frm.ClientID %>'`, read by `bug_list.js:134-424`
  for sort, filter and paging). Also `send_email.aspx:70-76`, `add_attachment.aspx:12,18`,
  `edit_priority.aspx:11`, `default.aspx:81`, `forgot.aspx:60`.
- **Wrong translation.** If the runtime's `ClientID()` returns the `ID`
  (`"frm"`/`"searchForm"`) while the template renders `id="aspnetForm"`, then
  `getElementById` returns null. That breaks bug-list sorting and paging and search's
  show/hide and mass-edit JS. The original returns the id it renders: `aspnetForm` under
  AutoID, the bare `ID` under `ClientIDMode="Static"` (`bugs.aspx:11`, `edit_bug.aspx:16`).
- **Fix (recommended).** Rulebook, B1/§3 `Control`: "`x.ClientID` → `x.ClientID()`, which
  returns exactly the `id` the template renders for it: the `ID` for every control, and for
  the server form the value B5 renders. B5 form row: on master pages `name="aspnetForm"`
  always (EditBug.js:30,37 selects `form[name=aspnetForm]`). `id` = the declared `ID`
  when the content has `ClientIDMode="Static"` (bugs.aspx `frm`), else `aspnetForm`."

## J-U8 — Control properties rendered statically from markup (AutoPostBack, Enabled, Text, Style)

- **Rules.** B5 AutoPostBack row (DropDownList only): literal
  `onchange="__doPostBack('x','')"`. The `bt:control` processor applies "Visible,
  InnerText/InnerHtml, Value, Checked, Disabled, Attributes, select options". B4 (b)
  lists persisted properties without `AutoPostBack`/`Enabled`/`Style`.
- **Sites.** `search.aspx:246-247`: `asp:ListBox ID="project" … onchange="set_project_changed()"
  AutoPostBack="true"`. `search.aspx.cs:84` sets `project.AutoPostBack = false` on GET, and
  `:139` reads it on postback. `search.aspx.cs:63-70` sets `….Style["display"]`.
  `admin.aspx.cs:44-45` sets `reindexLink.Enabled = false` and `reindexLink.Text = … +
  " (Indexing in process)"`.
- **Wrong translation.** The row does not cover ListBox. If it is applied anyway, the
  declared `onchange` is duplicated or lost (browsers keep the first attribute), so either
  `project_changed` never becomes `1` or the postback never fires. The literal
  `__doPostBack` stays even after code turns AutoPostBack off. On postback,
  `project.AutoPostBack` reads the markup default `true` instead of the persisted `false`,
  so `search.aspx.cs:139` takes `handle_project_custom_dropdowns()` instead of the query
  branch. The admin button keeps its markup label and stays enabled.
- **Fix (recommended).** Rulebook, B4/B5: "`bt:control` renders `onchange` for any
  list control from its `AutoPostBack` property, with the declared `onchange` first:
  `<declared>;__doPostBack('x','')`. The template carries no literal `__doPostBack`. The
  processor also applies `Text` (Button `value`, Label/CheckBox label text), `Enabled=false`
  → `disabled` and `Style` → `style`. B4 (b) is any property set by code, explicitly
  including `AutoPostBack`, `Enabled`, `Text`, `Style`."

## J-U9 — `runat="server"` elements without an ID (one has a server event)

- **Rules.** B1: each server element "becomes a `public final` field named by its `ID`".
  B5: `name` = the control `ID`. B2 step 6 events: `OnClick`, `OnItemCommand` only.
- **Sites.** `backup_db.aspx:27`: `<input type="submit" … runat="server" onserverclick="on_backup">`
  with no ID. Others without an ID and with no event: `search.aspx:344`,
  `UpdatePassword.aspx:84`, `change_password.aspx:35`, `forgot.aspx:41`, `register.aspx:72`.
  42 ID-less `<form runat>`/`<head runat>` are covered by B5/B6.
- **Wrong translation.** No field name and no `name` attribute, so nothing posts the
  button's name. `onserverclick` has no mapping, so "Backup Database Now" never runs
  `on_backup`.
- **Fix (recommended).** Rulebook, B1/B2: "An ID-less server element that code never
  references and that has no server event is literal markup (no field). Otherwise the
  inventory assigns the synthetic ID `_ctl<N>` (N = 1-based order of ID-less server
  controls in the file), used for the field, `id` and `name`. `onserverclick="X"` →
  `f.OnServerClick(this::X)`, raised when the button's name is posted." Inventory: add a
  row for `backup_db.aspx:27` (`_ctl1`).

## J-U10 — Control types with no B1/B5 mapping; contract-2 names for list items

- **Rules.** B1 type list and B5 rendering rows; B5 "`name` … = the control `ID`".
- **Sites.**
  - `asp:RadioButtonList`: 9 in `edit_org.aspx:48-189`, missing from B1 and §3.
  - `asp:CheckBoxList`: `send_email.aspx:160`. The original renders item names
    `ctl00$body$lstAttachments$<i>`, so the logical names are `0`, `1`, ….
  - `asp:Label`: `translate.aspx:165`. It is in B1 but has no B5 row, and `Text` is not
    applied (J-U8).
  - `<title id="titl" runat="server">`: 20 pages, e.g. `mbugs.aspx:75`, `install.aspx`.
    Their code sets `titl.InnerText`. `title` is not in the B1 tag map.
  - Lower-case `<asp:checkbox>`: `edit_attachment.aspx:26`.
- **Wrong translation.** A translator either improvises (two translators would differ, which
  breaks the meta-rule) or applies "name = ID". For a CheckBoxList, that changes the logical
  field names from `0..n` to `lstAttachments`, which violates judge contract 2.
- **Fix (recommended).** Rulebook, B1/B5/§3:
  - Add `RadioButtonList`: one `<input type=radio id="x_<i>" name="x" value="<item value>">`
    plus a label per item. The posted value → `SelectedValue`.
  - Add `CheckBoxList`: per item `id="x_<i>" name="x$<i>"`, which keeps the logical name
    `<i>`; checked = name present.
  - Add an `asp:Label` row: `<span id bt:control>`, with `Text` → inner HTML.
  - Map `title` → `HtmlGenericControl`.
  - "`asp:` tag and attribute names are matched case-insensitively."

## J-U11 — Posted-value loading for controls that were not rendered or not posted

- **Rule.** B4: "posted values are applied: inputs/textarea → `Value`; checkbox →
  `Checked` = name present; …"
- **Sites.** `edit_self.aspx.cs:108-112,247`: with `DisableFCKEditor=1`, `use_fckeditor` is
  `Visible=false`, `Checked` comes from the DB on GET, and the postback writes
  `use_fckeditor.Checked` to `us_use_fckeditor`. `edit_user.aspx.cs:48` (`admin.Visible=false`
  for project admins). `edit_bug.aspx.cs:1302-1304,1511` (hidden `prev_org`, `prev_udf`
  set invisible).
- **Wrong translation.** ASP.NET calls `LoadPostData` only for controls that registered
  for postback (rendered, visible and enabled in the previous response) or whose key is in
  the collection. An invisible checkbox keeps its view-state `Checked`, and an absent hidden
  field keeps its `Value`. B4 as written sets `Checked=false` (name absent), so a user
  with FCKeditor enabled has `us_use_fckeditor` reset to 0 on every save of `edit_self`.
- **Fix (recommended).** Rulebook, B4: "Posted values apply only to (a) controls whose
  key is in the postback collection, and (b) checkboxes/radios that were rendered visible
  and enabled in the previous response (recorded in view state). For those, an absent name
  means unchecked. All other controls keep their restored state."

## J-U12 — Declared `name` on server inputs (edit_bug's `name="submit"`)

- **Rules.** B5: "Literal HTML copied unchanged". The `name` row says "logical name only
  = the control `ID`", except that `HtmlInputRadioButton` takes the "declared `name`".
  Nothing says what happens to a declared `name` on other server inputs.
- **Site.** `edit_bug.aspx:113` and `:239`: `<input runat="server" type="submit"
  id="submit_button2|submit_button" name="submit" …>`.
- **Wrong translation.** ASP.NET ignores a declared `name` on `HtmlInputControl`s other
  than radios and renders the `UniqueID`. A translator who keeps `name="submit"` creates a
  form element named `submit`, which shadows `form.submit`. `EditBug.js:37`
  (`$("form[name=aspnetForm]").submit()`, run by the click handler after F1's inline
  onclick) then cannot submit, so the browser save that F1 exists to fix is lost. The judge
  does not see it, because it posts with `submit(null)`.
- **Fix (recommended).** Rulebook, B5 name row: "A declared `name` on a server
  `input`/`select`/`textarea` other than a radio is dropped; `name` = `ID`."

## J-U13 — `HttpContext.Current()` during `Application_OnStart`

- **Rules.** B6: `Application_OnStart` runs "inside a startup pseudo-context whose
  `Request()` throws `HttpException`". §3 [C34]: `HttpContext.Current()` is "request-bound
  `ThreadLocal`, `null` off a request thread".
- **Site.** `Global.asax.cs:46`: `Util.set_context(HttpContext.Current)`. Then
  `util.cs:54-61` expects `HttpException` from `context.Request`.
- **Wrong translation.** At `ApplicationReadyEvent` no request thread exists, so C34 makes
  `Current()` return null. `Util.context` is then null, and any `Util.context.Request` read
  is an NPE that the `HttpException` catch does not handle.
- **Fix (recommended).** Rulebook, §3 `HttpContext.Current()`: "…also bound to the
  startup pseudo-context for the duration of `Application_OnStart` (B6)."

## J-U14 — Request validation granularity (confidence: medium)

- **Rule.** B3: "reading a form/query/cookie value that contains `<`+letter … throws
  `HttpRequestValidationException`". That is lazy and per value (4.5 semantics).
- **Site.** `Web.config` `httpRuntime requestValidationMode="2.0"` (B3 cites it).
  In 2.0 mode, the first access to a collection validates **every** value in it (keys
  starting with `__` excepted). The framework itself reads `Request.Form` (POST) or
  `Request.QueryString` (GET) in `DeterminePostBackMode`.
- **Wrong translation.** On a validated page, a dangerous value in a field the code never
  reads is a 500 on the original but passes on the port.
- **Fix (recommended).** Rulebook, B3: "On validated pages, the first access to
  `Form`/`QueryString`/`Cookies`, including the runtime's postback read (B2 step 2),
  validates all values of that collection except keys starting with `__`." `TODO(port)` to
  confirm against .NET 4.5 when the original runs (D-001).

## J-U15 — BugList.ascx:128 inventory rows contradict C3 and C1

- **Rules.** §2A `string.Join` [C3]: `Strings.join(sep, xs)`; "`java.lang.String.join` is
  not used". §2A [C1]: declared `IEnumerable<T>` → `java.util.List<T>`.
- **Rows.** The three `Controls/BugList.ascx | __Page.__body | L128` rows (LAMBDA_DEFAULT,
  LINQ_STREAM ×2) prescribe `String.join(", ", StreamSupport.stream(GetVisibleColumns()
  .spliterator(), false)….toArray(String[]::new))` and treat `GetVisibleColumns()` as
  `Iterable`.
- **Wrong translation.** It uses the banned `java.lang.String.join` and a non-`List` return
  type for `BugList.ascx.cs:33-36`, contradicting C1.
- **Fix (recommended).** Inventory: `GetVisibleColumns()` returns `List<String>`:
  `SelectedQuery == null ? new ArrayList<>() :
  new ArrayList<>(Arrays.asList(SelectedQuery.VisibleColumnNames()))` (computed
  property → method). Region text:
  `Strings.join(", ", GetVisibleColumns().stream().map(c -> Strings.Format("'{0}'", c)).toList())`,
  emitted by `__scriptN()` (J-U2).

## J-U16 — Thread rows bypass `Threads.start`

- **Rules.** §1 Async row and §2A threads table [C2, C22]: written via
  `Threads.start(proc)`, for example
  `admin.aspx.cs:43 … → Threads.start(() -> search.IndexAll());`.
- **Rows.** `admin.aspx.cs | admin.ReindexAllBugs | L43` and
  `edit_queued_notifications.aspx | L37` prescribe
  `new Thread(…); t.setDaemon(false); t.start();`.
- **Fix (recommended).** Inventory: `Threads.start(() -> search.IndexAll());` and
  `Threads.start(Bug::threadproc_notifications);`.

## J-U17 — install.aspx C7 rows deviate from the C7 template

- **Rule.** §2A [C7]: the generated clause runs "the same body with `ex` bound to the
  abort", wrapped in `try { … } finally { throw ex; }`.
- **Rows.** `install.aspx | L32` and `| L46` prescribe
  `catch (ResponseEndException e) { Response.Write("…" + "Thread was being aborted." + "…"); throw e; }`.
  That hard-codes the message, renames the variable and omits `finally`.
- **Fix (recommended).** Inventory: use the C7 form verbatim:
  `catch (ResponseEndException ex) { try { Response.Write("<font color=red><b>" + Strings.cs(ex.getMessage()) + "</b></font>"); } finally { throw ex; } }`.

## J-U18 — `SelectedValue` set before `DataBind`, and event validation

- **Rule.** B4: "A posted value not among the restored items of a select-type control →
  500". There is no rule for `SelectedValue` assigned before the items exist.
- **Site.** `bugs.aspx.cs:67-76`: `query.SelectedValue = Request["queryId"]` (or the
  default) **before** `query.DataBind()`. S2 judges `?queryId` selection
  (`specs/S2.md` "bugs.aspx").
- **Wrong translation.** A runtime that applies `SelectedValue` immediately finds no
  items and either throws or ignores it, so the S2 selection is lost. .NET caches the value
  and applies it at `DataBind`. If it is still not found there, .NET throws
  `ArgumentOutOfRangeException` (500), which is the original's behavior for a stale
  `queryId`.
- **Fix (recommended).** Rulebook, §3 `ListControl`: "`SelectedValue(v)` before
  `DataBind` is cached and applied at `DataBind`. A value missing then →
  `ArgumentOutOfRangeException` ('… has a SelectedValue which is invalid because it does
  not exist in the list of items.')".

---

### Checked and found consistent (no finding)

- REDIRECT_END (17 rows) and TRANSFER (19 rows) agree with B3. No UI `Redirect(url,
  false)`, `Server.Execute` or `Response.Flush` exists. Only `install.aspx:20` has
  End/Redirect/Transfer lexically inside a `try` with a general catch, and no try body
  calls a same-file method that ends the response (so C7 has one site).
- Judge `Form` and `Scenario` fixtures work with B5 for edit_bug: `user_hit_submit`
  hidden/disabled, inline `onclick` on `#submit_button`, `name=ID`, `aspnetForm`,
  `submit(null)`, and `primeAspNetSession`'s search post.
- Session/Application rows (SESSION_USER 21, CACHE_SHARED_MUTABLE 10, APP_CONFIG 17)
  match C10/C32/C34. `Util.context.Application` → `ApplicationState.get()` in MainMenu.
- No locals are shared across separate top-level regions in any markup file, so
  per-region methods do not break scoping.
