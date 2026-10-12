# Groomed issue records

On 2026-10-12, issues #1 through #40 in [TDMK](https://github.com/xiaodlb/TDMK/issues) were reviewed in numerical order. Each issue was read, prepared with the four-section template, published, and verified before proceeding to the next issue. The already-groomed #4 was reverified; #24 received a follow-up clarification to avoid making the grid depend on its unfinished editor.

Each numbered Markdown file is the corresponding GitHub issue body, not its implemented deliverable. For example, `4.md` specifies the later creation of `_docs/data-model.md`; it does not substitute for that data model. When changing a specification, keep the GitHub issue and this snapshot consistent.

## Verification

- All 40 local snapshots matched the published issue bodies exactly after grooming.
- Every issue has Goal, Acceptance criteria, Out of scope, and Constraints, in that order.
- Every out-of-scope bullet links to an existing follow-up issue.
- Issue titles and open/closed state were not modified; all 40 remain open at the time of this review.
- No application code or dependencies were changed, and no new issues were needed during this pass. Existing #40 retains the deferred sync planning work.

## Readiness notes

- #1 already has scaffold implementation and passing local test/build results; phone launch verification remains outstanding in `_docs/task-1-validation.md`.
- #2 and #3 collect owner-approved behavior decisions one question at a time. They must produce the required rules before #4 and behavior-dependent implementation can proceed.
- Required behavior/data-model guides are linked as prerequisites. Missing decisions block the affected implementation rather than being filled with silent assumptions.
- #24 supplies the notes-grid navigation contract; #25 implements the editor and full return flow. #22 depends on #25 for functional Home note taps, so numerical order is not an implementation order.
- #40 is post-v1 planning and does not block the offline first version.

Use [the backlog index](../tasks.md) and each issue's constraints to select one task with satisfied prerequisites, following [the process](../process.md).
