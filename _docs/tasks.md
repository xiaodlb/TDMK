# TDMK issue backlog

All 39 implementation tasks have moved to [GitHub Issues](https://github.com/xiaodlb/TDMK/issues). The agreed stack is Kotlin, Jetpack Compose, Material 3, and Room/SQLite. Version one works offline; accounts and cloud sync are deferred.

Each issue includes its goal, description, and project context. Original task numbers are preserved in issue titles; references to task numbers use this index.

All 40 current issues were groomed sequentially using `_docs/task-template.md`. The matching issue-body snapshots are in [issues/](issues/); see [the grooming record](issues/README.md). Grooming defines the work and does not mark it implemented or close the issue.

Follow [the work process](process.md): work on one GitHub issue at a time, read acceptance criteria before starting and before closing, and commit regularly. Establish checkable criteria before implementation if an issue does not yet contain them.

- Task 1: [Set up an empty Android project with a passing test](https://github.com/xiaodlb/TDMK/issues/1)
- Task 2: [Document task defaults and date rules](https://github.com/xiaodlb/TDMK/issues/2)
- Task 3: [Document note, search, and backup rules](https://github.com/xiaodlb/TDMK/issues/3)
- Task 4: [Define the local data model](https://github.com/xiaodlb/TDMK/issues/4)
- Task 5: [Create the Room database foundation](https://github.com/xiaodlb/TDMK/issues/5)
- Task 6: [Add list persistence and the default Inbox](https://github.com/xiaodlb/TDMK/issues/6)
- Task 7: [Add task persistence](https://github.com/xiaodlb/TDMK/issues/7)
- Task 8: [Add subtask persistence](https://github.com/xiaodlb/TDMK/issues/8)
- Task 9: [Add separate note persistence](https://github.com/xiaodlb/TDMK/issues/9)
- Task 10: [Implement task date views and ordering](https://github.com/xiaodlb/TDMK/issues/10)
- Task 11: [Implement completion and log visibility](https://github.com/xiaodlb/TDMK/issues/11)
- Task 12: [Add recurring-template persistence](https://github.com/xiaodlb/TDMK/issues/12)
- Task 13: [Implement recurring occurrence generation](https://github.com/xiaodlb/TDMK/issues/13)
- Task 14: [Build the application navigation shell](https://github.com/xiaodlb/TDMK/issues/14)
- Task 15: [Add light and dark appearance settings](https://github.com/xiaodlb/TDMK/issues/15)
- Task 16: [Build task rows and quick entry](https://github.com/xiaodlb/TDMK/issues/16)
- Task 17: [Build the task details bottom sheet](https://github.com/xiaodlb/TDMK/issues/17)
- Task 18: [Add checklist editing to task details](https://github.com/xiaodlb/TDMK/issues/18)
- Task 19: [Add recurrence controls to task details](https://github.com/xiaodlb/TDMK/issues/19)
- Task 20: [Build the Tasks screen](https://github.com/xiaodlb/TDMK/issues/20)
- Task 21: [Build list management](https://github.com/xiaodlb/TDMK/issues/21)
- Task 22: [Build the Home dashboard](https://github.com/xiaodlb/TDMK/issues/22)
- Task 23: [Build the completion Log screen](https://github.com/xiaodlb/TDMK/issues/23)
- Task 24: [Build the Notes card grid](https://github.com/xiaodlb/TDMK/issues/24)
- Task 25: [Build the full-screen formatted note editor](https://github.com/xiaodlb/TDMK/issues/25)
- Task 26: [Add per-tab search](https://github.com/xiaodlb/TDMK/issues/26)
- Task 27: [Add individual task and note deletion](https://github.com/xiaodlb/TDMK/issues/27)
- Task 28: [Build Android reminder scheduling](https://github.com/xiaodlb/TDMK/issues/28)
- Task 29: [Add reminder controls to task details](https://github.com/xiaodlb/TDMK/issues/29)
- Task 30: [Restore reminders after system changes](https://github.com/xiaodlb/TDMK/issues/30)
- Task 31: [Add recurrence reconciliation background work](https://github.com/xiaodlb/TDMK/issues/31)
- Task 32: [Implement backup export](https://github.com/xiaodlb/TDMK/issues/32)
- Task 33: [Implement backup validation and restore](https://github.com/xiaodlb/TDMK/issues/33)
- Task 34: [Add backup and restore controls to Settings](https://github.com/xiaodlb/TDMK/issues/34)
- Task 35: [Handle foreground date changes](https://github.com/xiaodlb/TDMK/issues/35)
- Task 36: [Verify database upgrades preserve data](https://github.com/xiaodlb/TDMK/issues/36)
- Task 37: [Review accessibility and small-screen usability](https://github.com/xiaodlb/TDMK/issues/37)
- Task 38: [Run the first-version acceptance walkthrough](https://github.com/xiaodlb/TDMK/issues/38)
- Task 39: [Document local builds and prepare a reviewable APK](https://github.com/xiaodlb/TDMK/issues/39)

## Deferred follow-ups

- [#40: Define optional cloud sync contract (post-v1)](https://github.com/xiaodlb/TDMK/issues/40) - created while grooming #4; not part of the offline first version.
