# Task row and quick-entry choices (#16)

Under the owner's routine-choice delegation: supporting row metadata is the current persisted list name and readable High/Medium/Low priority, plus ISO scheduled date only when saved. No checklist/reminder/editor controls are added. Example: an undated Medium task in renamed General shows “Medium priority · General”, with no invented date/time.

The production Tasks destination is a minimal real Anytime host until #20 adds its tab arrangement. The reusable host accepts Anytime/Today/Home/custom-list contexts and delegates eligibility/order to TaskViews. Entry uses a custom list's stable ID only in that context; other contexts omit the ID so the repository resolves the stable default list. Example: title-only entry from Today persists an undated Medium task in renamed General and does not force a row into Today; it appears in General/Anytime.

Save and IME Done share one synchronous pending guard. Successful database commit clears the draft for the next entry; cancel clears it without saving. Validation/write failure retains the editable title with an inline polite error and Save retries. Pending controls are disabled to prevent duplicate submission. A separate checkbox toggles completion; the rest of the row requests details with the stable ID. #17 owns the editor; the current activity records the request and reports that details are coming soon. Repository completion gate failures remain unchanged data and display “Complete all subtasks first.”

The testing/API guides added at 6d37af2 were read and applied (the groomed issue predates those guides). Existing process, design tokens, real persistence/Flow and device checks apply. No schema or dependencies changed.


Engineer validation on 2026-10-13 (API36 TDMK_API36 emulator-5554):

- `:app:testDebugUnitTest`: 19/19 PASS, including the new context eligibility regression and existing date/completion/recurrence rules.
- `:app:assembleDebug`: PASS. Schema remains 6; no dependency/schema changes.
- Full `:app:connectedDebugAndroidTest`: 43/43 PASS on the implemented production code. After evidence-harness refinements and the additional database-busy submission check, the final focused `TaskEntryTest` result is recorded below.
- Actual isolated on-disk DB/Compose checks cover custom-list entry, renamed stable default from Today, null optional fields/Medium priority, matching reactive row appearance and draft reset, reopen through a second provider, separate details/completion IDs, complete/reopen, unchanged unchecked-child failure, Unicode-whitespace rejection/cancel, injected SQLite insert failure retaining draft and retry, and repeated button/IME submission during a held transaction creating one row. Existing TaskRepositoryTest also rejects invalid Unicode.
- Keyboard matrix: shared AppTheme/Surface, real API36 configuration at 1.0/2.0 font and portrait/landscape, light/dark. Entry/error, Save/Cancel and long-row completion/details targets are reached by scrolling with IME enabled. Original PNGs are in `evidence/task-16/`; controls and row screenshots show separate scroll positions when the enlarged keyboard limits the viewport. Screenshots are visually inspected; semantics assertions are not substituted for rendering.
- Earlier validation failures: nullable lifecycle calls corrected; wrong debug-key environment corrected using repository ANDROID_USER_HOME without clearing app data; test-host recreation/Surface/IME setup corrected; editable draft assertion compares string content rather than AnnotatedString annotations. These failures are not claimed as passes.

This is Engineer evidence, not independent QA approval. Issue #16 remains open for QA. #17 details editor, #20 full Tasks tabs and #22 dashboard remain deferred as groomed.

Final focused TaskEntryTest: 4/4 PASS (49s), including settled IME matrix and database-busy duplicate guard. Build/test source corresponds to the issue implementation commit; supporting validation/evidence is committed with it.

Final evidence-only matrix rerun: PASS (58s). Re-requested IME after each scroll and waited for settled visibility directly before capture; all 24 final PNGs show the keyboard. Inspected all final images plus full-size normal portrait controls and dark landscape row. Entry/error/buttons and completion/details remain reachable by scrolling; long title wraps and priority/list text is readable. Earlier transient captures were replaced.
