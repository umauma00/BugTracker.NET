# Parity judge (prompt 00b)

The single harness that decides "is the Java port done?". It evaluates the
original (ASP.NET WebForms on IIS) and the port (Spring Boot 4 + Thymeleaf) on
equal terms, through the **public surface only**: HTTP (HTML forms, JSON API)
and the shared SQL Server database. It imports nothing from either
implementation.

> **Status: draft, not yet validated (deviation D-001); F1-F5 expectations per D-003 (see "Sanctioned fixes").** The original cannot
> be run in the migration environment (no Windows / IIS), and SQL Server could
> not be installed there either (network policy denied the Microsoft package
> host). So every expected value is *read out of the original source* and
> carries a `@SourceRef` citation. Validation (step 3 of prompt 00b) is
> deferred — see "Validation plan" below.

## Running

```bash
cd judge
JUDGE_TARGET=new \
JUDGE_BASE_URL=http://localhost:8080/ \
JUDGE_DB_URL='jdbc:sqlserver://localhost:1433;databaseName=btnet_judge;encrypt=false' \
JUDGE_DB_USER=sa JUDGE_DB_PASSWORD='...' \
mvn -B test
```

- Same command for the original: point `JUDGE_BASE_URL` at IIS / IIS Express,
  `JUDGE_TARGET=old`. `JUDGE_TARGET` is a label; it never changes an assertion.
- Both implementations must use the database named in `JUDGE_DB_URL`. Each test
  starts by rebuilding it from `src/BugTracker.Web/setup.sql` (the original's
  own setup script).
- Unconfigured runs **skip** every scenario; they never pass.
- Scenarios run serially (shared database).

## Layout

| Path | What |
|---|---|
| `src/test/java/judge/core/` | Harness: config, DB reset/queries, browser-like HTTP session, form driver, normalization, annotations |
| `src/test/java/judge/scenarios/` | One class per scenario (S1–S9) |
| `src/test/java/judge/JudgeSelfCheckTest.java` | Fails if any scenario test lacks a `@SourceRef` |
| `specs/` | Each scenario in plain words, with the source lines that justify every expectation and the inherited quirks it pins |

## Fixtures (`judge.core.Scenario`)

| Fixture | What |
|---|---|
| `loginAdmin(web)` | Logs in as admin/admin once and asserts it worked. No priming: since F5 (D-003) no login rewrites user 1's password, so the seeded row (setup.sql:168, hash of `admin` per the RULEBOOK B7 acceptance vector) is used as is. The fixture writes nothing and computes no hash; a failure here means the port misses the acceptance vector |
| `insertLoginableUser(name, us_admin, org)` | Inserts a user whose password is `admin` by copying user 1's seeded salt+hash (the hash is username-independent). Usable for non-admins since F4 |
| `formAfterSubmitButtonClick(page, buttonId)` | F1 fixture: asserts the rendered disabled hidden `user_hit_submit` and the button's inline `onclick`, removes `disabled`, returns the form as the browser posts it after the click |
| `primeAspNetSession(web)` | Makes the ASP.NET session non-empty with one search postback (`Session["bugs"]`, search.aspx.cs:620). Call it after login and **before the first postback** to any page that sets `ViewStateUserKey = Session.SessionID` (edit_bug.aspx.cs:32, add_attachment.aspx.cs:15, the admin edit/delete pages). ASP.NET InProc issues a new SessionID per request until something is stored in Session, so an unprimed postback fails view-state MAC (500) on the original. Harmless on the port. Not needed after a GET of an existing `edit_bug.aspx?id=N` (stores `Session["project"]`, edit_bug.aspx.cs:302) |
| `newSession()` | Fresh logged-out session on the same database |

## Judge contract (binding on the port — goes into RULEBOOK.md in Step 1)

The UI is redesigned, so the judge cannot compare markup. It compares data
addressed through these stable handles, which the port **must preserve**:

1. **URLs.** Every original page path (`bugs.aspx`, `edit_bug.aspx?id=N`, …)
   answers in the port, case-insensitively, with the same query parameters.
   (Also needed for links already sent in notification e-mails.)
2. **Form field logical names.** The part of `name` after the last `$`
   (`ctl00$body$short_desc` → `short_desc`). The port renders plain
   `name="short_desc"`. Plumbing fields (`__VIEWSTATE`, CSRF tokens) are
   submitted back untouched and never asserted.
3. **Element ids of data-bearing elements** that a scenario reads (message
   spans such as `msg`, data tables). Each scenario spec lists the ids it uses.
4. **JSON API shapes** of the Web API controllers (`api/…`): property names,
   types, status codes.
5. **Database effects.** Same tables, same columns, same values written —
   including inherited quirks marked `@Inherited`.

## Normalization rules (every place the judge deliberately stops looking)

| Rule | What is ignored | Why |
|---|---|---|
| N1 | Whitespace runs (incl. `&nbsp;`) collapsed, trimmed | Markup is redesigned; whitespace is layout |
| N2 | Markup inside table cells (links, icons) → text | Same |
| N3 | Path case, base path | IIS is case-insensitive; deployment root differs |
| N4 | Server-control id prefix `ctl00_body_` | Original's Web.config `clientIDMode="AutoID"` (line 38) mangles ids inside master pages; the port renders the plain id |
| — | `__VIEWSTATE`, `__EVENTVALIDATION`, CSRF tokens | Framework plumbing, not behavior |

Anything else — dates, ids, ordering — is asserted unless a scenario spec says
otherwise and explains why. Adding a rule is a judge change and needs review.

## Seed patches (judge decisions)

`Db.reset()` runs `src/BugTracker.Web/setup.sql` with two patches, because the
script alone does not produce a database the original can run on:

| Patch | Problem in the original's seed | Fix |
|---|---|---|
| P1 | `setup.sql:168` inserts admin with an explicit `us_id` into an identity column without `SET IDENTITY_INSERT` → SQL Server rejects it; admin missing, other ids shift | Wrap that one statement in `IDENTITY_INSERT ON/OFF` |
| P2 | EF maps `queries.qu_columns` (`Models/Mapping/queryMap.cs:29`); setup.sql never creates it → every read of `queries` fails | Add the column as the database project defines it and run `BugTracker.Database/Script.PostDeployment.sql` |

## Inherited behavior

Tests annotated `@Inherited` pin behavior that is probably a defect in the
original (e.g. the seeded non-admin users crash on login because of their NULL
salt). The port reproduces it until parity is signed off; prompt 06 may fix it
as a flagged change that updates the test in the same commit. The judge never
weakens an expectation because it looks wrong. The one exception is the five
defects the human sanctioned as fixed, below.

## Sanctioned fixes (D-003)

RULEBOOK §0.1 (option (b), gate 00b) specifies five original defects (F1-F5) as
**fixed** in the port. The tests that used to pin them as `@Inherited` now assert
the fixed behavior, derived from the original source's evident intent (each keeps
its `@SourceRef` lines, now citing the lines that define the intended values) and
carry the javadoc line `Sanctioned fix F<n> (RULEBOOK §0.1, D-003)` instead of
`@Inherited`. They therefore **fail on the original by design**; the original is the
spec only for everything else. Every other `@Inherited` quirk is unchanged.

| Fix | Test (class.method) | Change |
|---|---|---|
| F1 save trigger | `S3.submitButtonClickCreatesBug` | replaces `browserPostWithoutUserHitSubmitSavesNothing`: simulated click on `#submit_button` creates the bug |
| | `S3.projectAutopostbackWithoutButtonClickSavesNothing` | the old assertions, now the autopostback half of F1 |
| | `S3.submitButtonClickUpdatesExistingBug`, `S3.anyUserHitSubmitValueEqualToOneSaves` | new: click on an existing bug; "any value equals 1" rule |
| | `S8.formPostNeedsTheButtonClickToSaveAndNotify` | replaces `plainFormPostNeitherSavesNorNotifies` |
| F2 history insert | `S3.changingStatusAndPriorityUpdatesBugAndWritesHistory` | replaces `...ThenFailsOnHistory`: 200 and two `update` posts (`Changed priority from low to high`, `Changed status from new to in progress`) |
| F3 queue insert | `S8.qualifyingSubscriberGetsQueuedNotification` | replaces `...MakesEditFailAndQueuesNothing`: 200 and one `not sent` row |
| | `S8.existingQueueRowIsReplaced` | replaces `existingQueueRowIsNotReplaced` |
| | `S8.selfNotificationIsQueued` | replaces `selfNotificationAlsoFails` |
| F4 non-admin login | `S1.nonAdminWithCorrectPasswordLogsInAndLandsOnBugs` | replaces `...FailsWithServerError`: lands on `bugs.aspx`, API 200 |
| | `S1.plainUserSeesNoAdminMenuEntries`, `S1.projectAdminSeesUsersEntryInsteadOfAdmin`, `S1.userOfOrgWithoutSearchHasNoSearchMenu` | new: role-dependent menu |
| | `S6.createdNonAdminUserCanLogInWithChosenPassword`, `S6.plainUserIsRefusedFromAdminPages`, `S6.projectAdminReachesUsersAndCategoriesButNotAdminOnlyPages` | new: UI-created user login; authorization (unblocks M-S6-5's second half) |
| | `S9.searchPageDependsOnOrgSearchPermissionForNonAdmins` | new: non-admin search gate |
| F5 admin rewrite | `S1.loginAttemptsNeverRewriteAdminPassword` | replaces `failedAdminAttemptRewritesAdminPassword` |
| | `S1.seededNonAdminLoginFailsWithServerError` | still `@Inherited` (NULL-salt crash); only its "admin rewritten" assertion flipped |
| | all scenarios (`loginAdmin`) | fixture no longer needs the retry/priming; see Fixtures |

Not expressible: the nondeterministic `qn_retries` / `qn_last_exception` of a fresh
queue row (a worker thread updates them), `#submit_button2` (needs
`DisplayAnotherButtonInEditBugPage=1`, a runtime setting the judge cannot change), and
any non-admin's bug-list contents (permission claims including preserved quirks).
Mutations changed in `specs/MUTATIONS.md`: M-S1-2, S3-M1, S3-M2, S8-M1 inverted;
M-S1-5, M-S9-5 new; M-S6-5's non-admin half now testable.

## Validation plan (prompt 00b step 3, deferred by D-001)

| Check | When | Pass condition |
|---|---|---|
| Compiles, self-check passes | now | `mvn -B test` green with 0 scenarios executed (all skipped) |
| Clean run against the port | Step 5 (port boots) | All scenarios pass, or every failure triaged as port bug vs judge bug |
| Mutation run against the port | Step 5 | Each injected mutation in `specs/MUTATIONS.md` makes ≥1 scenario fail |
| Clean + mutation run against the original | whenever a Windows + SQL Server host exists | N/N pass on the original; each mutation caught. Until then, parity with the original is **unverified** |
