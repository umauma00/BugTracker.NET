
## S2/S7/S8

### S2 — Bug list + query API
| # | Mutation in the port | Must fail |
|---|---|---|
| S2-M1 | Emit JSON in camelCase / rename a column key (e.g. `reportedBy` or `reported_by`), or serialize dates with an offset/`Z` | `S2_BugListTest.apiAllBugsQueryReturnsEveryColumnOfEveryRow` |
| S2-M2 | Compare `sortOrder` case-insensitively (so `asc` really sorts ascending) | `S2_BugListTest.apiLowercaseAscSortsDescending` |
| S2-M3 | Return `recordsFiltered` = number of rows on the page, or apply paging before the count | `S2_BugListTest.apiFilters`, `S2_BugListTest.apiPaging` |
| S2-M4 | Treat `length=-1` as "no rows" / literal FETCH -1 | `S2_BugListTest.apiPaging` |
| S2-M5 | Order the query dropdown by id instead of description | `S2_BugListTest.bugsPageQueryDropdown` |

### S7 — Print / export
| # | Mutation in the port | Must fail |
|---|---|---|
| S7-M1 | Drop the UTF-8 BOM, or use LF line endings, in the CSV export | `S7_PrintExportTest.exportExcelIsCsvWithBom`, `tasksExcelExport` |
| S7-M2 | Include the bug id column in the tasks export (i.e. stop skipping column 0) | `S7_PrintExportTest.tasksExcelExport` |
| S7-M3 | Format a 12:00:00 timestamp with its time | `S7_PrintExportTest.exportNoonTimestampLosesItsTime` |
| S7-M4 | Show history ("update") posts on print_bug without the history_inline cookie, or sort comments ascending | `S7_PrintExportTest.printOneBug` |
| S7-M5 | Stop marking printed bugs as seen (no bug_user row) | `S7_PrintExportTest.printDetailsPrintsEveryBugOfTheQuery`, `printOneBug` |

### S8 — Notification queue
| # | Mutation in the port | Must fail |
|---|---|---|
| S8-M1 | **(inverted, D-003 / F3)** Reintroduce the `N''not sent'` syntax error, or skip the queue insert, so the edit 500s / nothing is queued | `S8_NotificationQueueTest.qualifyingSubscriberGetsQueuedNotification`, `selfNotificationIsQueued`, `existingQueueRowIsReplaced` |
| S8-M2 | Keep the older queued row (skip the `delete ... where qn_bug = @bug and qn_to = @to`), or run the delete and insert as separate statements that leave two rows | `S8_NotificationQueueTest.existingQueueRowIsReplaced` |
| S8-M3 | Drop the `us_enable_notifications = 1` or the editor-exclusion condition from the subscriber query (a row is then queued where none may be) | `S8_NotificationQueueTest.subscriberWithNotificationsDisabledGetsNothing`, `editorIsNotNotifiedByDefault` |
| S8-M4 | `actn=delete` deletes every row regardless of status | `S8_NotificationQueueTest.deleteRemovesOnlyUnsent` |
| S8-M5 | Fix the "Delete unsent notifications" link | `S8_NotificationQueueTest.renderedDeleteLinkDeletesNothing` |

## S3/S4/S5

| ID | Mutation a port could plausibly make | Tests that must fail |
|---|---|---|
| S3-M1 | **(inverted, D-003 / F1)** (a) Save on any POST, ignoring `user_hit_submit`; (b) do not render `#user_hit_submit` or render it enabled; (c) drop/alter the inline `onclick` on `#submit_button`; (d) read only the form (not the query string) or only the first value | (a) `S3_EditBugTest.projectAutopostbackWithoutButtonClickSavesNothing`, `anyUserHitSubmitValueEqualToOneSaves`; (b)(c) `submitButtonClickCreatesBug`, `submitButtonClickUpdatesExistingBug` (+ `S8...formPostNeedsTheButtonClickToSaveAndNotify`); (d) `anyUserHitSubmitValueEqualToOneSaves` / every S8 edit |
| S3-M2 | **(inverted, D-003 / F2)** Keep the malformed `AddChange` SQL (500), skip the history insert, or write another text/order (e.g. status before priority, `bp_comment_search` set) | `S3_EditBugTest.changingStatusAndPriorityUpdatesBugAndWritesHistory` |
| S3-M3 | Store the comment raw, not HTML-decoded, or leave `bg_tags` NULL on update | `S3_EditBugTest.createBugRedirectsToItAndWritesRows`, `commentOnExistingBugAddsPost` |
| S3-M4 | Mark a bug as seen only when it exists and is viewable (no `bug_user` row for id 0/999/forbidden) | `S3_EditBugTest.createBugRedirectsToItAndWritesRows`, `missingShortDescriptionIsRejected`, `noPermissionIsRefused`, `invalidAndUnknownIdsAreReported` |
| S3-M5 | Let the admin role bypass project permissions, or default the new-bug status to 0 / project to the first project | `S3_EditBugTest.readOnlyPermissionShowsStaticFields`, `noPermissionIsRefused`, `newBugFormHasSourceDefaults` |
| S4-M1 | Use the uploaded file's sniffed or extension type instead of the part's Content-Type, or fix the "successfully upload" typo | `S4_AttachmentTest.uploadStoresFilePostAndBlob` |
| S4-M2 | Re-encode the download (text charset, or trim/transform bytes), or use `inline` as the default disposition | `S4_AttachmentTest.downloadReturnsStoredBytes`, `emptyContentTypeFallsBackToExtension` |
| S4-M3 | Check the empty file before the size limit with a different message, use `>=` for the limit, or let ReadOnly users upload | `S4_AttachmentTest.emptyFileIsRejected`, `oversizeFileIsRejected`, `readOnlyUserAndMissingIdCannotUpload` |
| S5-M1 | Return 401 JSON instead of the cookie-middleware 302 to `default.aspx?ReturnUrl=` | `S5_BugFromEmailTest.unauthenticatedPostIsRedirectedToLogin` |
| S5-M2 | Store e-mail comments as type `comment`, or skip updating `bg_last_updated_date` on a received post | `S5_BugFromEmailTest.jsonPostCreatesBugWithDefaults`, `mimeReplyWithTrackingIdAppendsComment` |
| S5-M3 | Normalize the comment header block's line endings (all `\r\n` or all `\n`), or keep the client's `shortDescription` instead of the MIME subject | `S5_BugFromEmailTest.mimeMessageFromServiceCreatesBug` |
| S5-M4 | Return 404 for a missing `BugId` instead of creating a new bug, or link JSON attachments to the comment post | `S5_BugFromEmailTest.jsonBugIdAppendsOrFallsBackToNewBug`, `jsonAttachmentIsStored` |

## S1/S6/S9

### S1 — login
- **M-S1-1** Login redirect honours `returnUrl` (or any `url`, incl. absolute) instead of local `url` only → `S1_LoginTest.loginIgnoresReturnUrl`, `loginHonoursLocalUrlParameterOnly` fail.
- **M-S1-2** **(inverted, D-003 / F5)** Keep the admin rewrite (`update_user_password(1,"admin")` in the password check) → `loginAttemptsNeverRewriteAdminPassword`, `seededNonAdminLoginFailsWithServerError` fail (and `unknownUserGetsSameMessageAndDoesNotTouchAdmin` if the rewrite is reached for unknown users).
- **M-S1-5** (new, F4) Leave the non-admin sign-in crashing, or sign in without the `ProjectAdmin`/`User` roles, or show `admin.aspx` to everyone → `nonAdminWithCorrectPasswordLogsInAndLandsOnBugs`, `plainUserSeesNoAdminMenuEntries`, `projectAdminSeesUsersEntryInsteadOfAdmin`, `userOfOrgWithoutSearchHasNoSearchMenu`, `createdNonAdminUserCanLogInWithChosenPassword` fail.
- **M-S1-3** Lockout uses `>=` instead of `>` (or locks after the check, so a locked attempt still rewrites admin) → `tenFailuresDoNotLock` / `elevenFailuresLockTheAccount` fail.
- **M-S1-4** Distinct messages for unknown/inactive user, or API returns 401 / a JSON body → `unknownUserGetsSameMessageAndDoesNotTouchAdmin`, `inactiveUserGetsSameMessageAndDoesNotTouchAdmin`, `apiLoginFailureReturns403Empty` fail.

### S6 — admin CRUD
- **M-S6-1** New user stored with `us_forced_project` NULL (instead of 0) or unchecked default flags honoured → `createUserWritesRowAndShowsItInList`, `createUserIgnoresUncheckedDefaultFlags` fail.
- **M-S6-2** Blank password on edit overwrites the password (hash of "") → `editUserUpdatesRowAndKeepsPasswordWhenBlank` fails.
- **M-S6-3** Port "fixes" the discarded level-3 permission update → `savingReporterUserDowngradesReporterPermission` fails.
- **M-S6-4** `delete_user` deletes the users row but not `project_user_xref` rows, or deletes despite bug references → `deleteUserRemovesUserAndProjectRows`, `deleteUserReferencedByBugIsRefused` fail.
- **M-S6-5** Admin pages authorise any authenticated user / skip auth on POST.
  - "skip auth on POST" → `anonymousAdminPagesRedirectToLogin`, `anonymousDeletePostDeletesNothing` fail.
  - "authorise any authenticated user" (testable since F4, D-003: non-admins can sign in) → `plainUserIsRefusedFromAdminPages`, `projectAdminReachesUsersAndCategoriesButNotAdminOnlyPages` fail.

### S9 — search
- **M-S9-1** Description criterion built without escaping `%`/`_` (or with a bound parameter that still treats them as wildcards) → `likeEscapesSqlWildcards` fails.
- **M-S9-2** Results ordered by description or ascending id → `descriptionSearchIsCaseInsensitiveNewestFirst`, `orLogicUnionsCriteria` fail.
- **M-S9-3** `and_or` radio ignored (always AND) or `[not assigned]` mapped to `IS NULL` → `orLogicUnionsCriteria`, `listboxCriteriaWithAndLogic` fail.
- **M-S9-5** (new, F4) Ignore `og_can_search` for non-admins (always show the form) or deny it for everyone → `searchPageDependsOnOrgSearchPermissionForNonAdmins` fails.
- **M-S9-4** Date column rendered with time for noon timestamps / culture format → `noonTimestampIsShownAsDateOnly` fails.
