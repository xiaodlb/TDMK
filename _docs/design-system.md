# Implemented UI foundation

Issue #15's compact foundation follows the neutral minimal scope. Tokens/error choices below use the owner's delegation; no dynamic color/library/redesign is added.

`AppTheme` shares Material3 Typography and color roles across every current destination. Saved System resolves against current device night mode; Light/Dark resolve independently. Persist the choice, not its resolved value. The existing Room singleton is observed and updated off the main thread. Failed save retains previous selection/theme and shows a polite inline error; selecting the intended option retries. Example: saved Light, failed Dark update → Light remains saved/selected and other data unchanged. Initial read failure shows Retry and no selectable controls until loaded.

| Role | Light | Dark |
| --- | --- | --- |
| Background/surface | #FAFAF9 | #14171A |
| Surface container/navigation | #F0F1F2 | #1F2428 |
| Primary / on-primary | #333C45 / #FFFFFF | #D7E0E6 / #1C242A |
| On-surface / supporting text | #202327 / #555B60 | #E8ECEF / #B7C0C7 |
| Selected container / text | #E4E8EB / #22292F | #354049 / #E2E9EE |
| Outline / subtle outline | #727B83 / #D0D6DA | #8C979F / #3F484F |

Typography: headlineLarge destination heading, titleMedium Appearance heading, bodyLarge content/control labels, standard Material3 app-bar/navigation/button roles. Preserve font scaling. ShellSpacing tokens: page24dp, vertical32dp, gap16dp (row gap8dp), appearance row minimum56dp, corner12dp. Shell content scrolls in short/enlarged layouts. Full-width appearance rows own radio semantics/one label; glyphs/nav icons are decorative. Standard Material3 focus/ripple plus selected fill/text remain visible.

Keep #14 Back/saved-route behavior and explicit safeDrawing status/cutout/navigation insets. Status icons follow resolved appearance. Native navigation uses contrasting neutral scrims (#333C45 light, #111418 dark) with white icons, avoiding the API36 emulator's pale-icon/light-scrim rendering; update this with appearance. No opening/recreation resets saved choice. Testing guide remains absent. [Validation](task-15-validation.md) records actual checks.

Issue #17 task details uses the shared surface role and standard Material3 fields/radio/button semantics in a scrollable ModalBottomSheet with safe top gap and IME padding. Explicit Save commits; cancel/dismiss discards; pending writes prevent dismissal. See [task 17 validation](task-17-validation.md) for current implementation and evidence.

Issue #18 adds ordered checklist text fields, labeled checkbox rows and Delete buttons with minimum 48dp touch height. Named confirmation stages deletion; the sheet explains that Save applies task/checklist changes together. New entries use a labeled input and Add item button. Validation shares the polite error live region. See [task 18 validation](task-18-validation.md).

Issue #19 adds One time/Daily/Weekly/Monthly radio choices and a three-date nominal preview under the date field. Recurring details show enabled/paused status, frequency, anchor and moved original slot. Explicit This occurrence/Whole series radio rows choose Save scope; One time on a series confirms pause, Resume confirms catch-up, and Delete uses scope followed by permanent-deletion confirmation. The controls use the existing scrollable sheet and polite error region. See [task 19 validation](task-19-validation.md).
