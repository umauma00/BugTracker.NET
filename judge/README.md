# Parity judge (prompt 00b)

The single harness that decides "is the Java port done?". It evaluates the
original (ASP.NET WebForms on IIS) and the port (Spring Boot 4 + Thymeleaf) on
equal terms, through the **public surface only**: HTTP (HTML forms, JSON API)
and the shared SQL Server database. It imports nothing from either
implementation.

> **Status: draft, not yet validated (deviation D-001).** The original cannot
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
| — | `__VIEWSTATE`, `__EVENTVALIDATION`, CSRF tokens | Framework plumbing, not behavior |

Anything else — dates, ids, ordering — is asserted unless a scenario spec says
otherwise and explains why. Adding a rule is a judge change and needs review.

## Inherited behavior

Tests annotated `@Inherited` pin behavior that is probably a defect in the
original (e.g. every login attempt rewrites the admin password to `admin` —
`src/BugTracker.Web/Security/Authenticate.cs:215`). The port reproduces it until
parity is signed off; prompt 06 may fix it as a flagged change that updates the
test in the same commit. The judge never weakens an expectation because it
looks wrong.

## Validation plan (prompt 00b step 3, deferred by D-001)

| Check | When | Pass condition |
|---|---|---|
| Compiles, self-check passes | now | `mvn -B test` green with 0 scenarios executed (all skipped) |
| Clean run against the port | Step 5 (port boots) | All scenarios pass, or every failure triaged as port bug vs judge bug |
| Mutation run against the port | Step 5 | Each injected mutation in `specs/MUTATIONS.md` makes ≥1 scenario fail |
| Clean + mutation run against the original | whenever a Windows + SQL Server host exists | N/N pass on the original; each mutation caught. Until then, parity with the original is **unverified** |
