# Work process

- Tasks are GitHub issues, one at a time. Use [the backlog index](tasks.md) to find the issue and its prerequisites; finish or resolve the current issue before starting another.
- Read the acceptance criteria before starting and before closing. Also read the goal, description, and relevant project guidance. If the issue has no explicit acceptance criteria, PM establishes checkable criteria from its goal and description before implementation. Resolve routine product ambiguity using the owner delegation below, preserving explicit answers and settled scope.
- Commit regularly at meaningful checkpoints with clear messages. Keep unrelated changes out of the task's commits and preserve existing work.
- After each work session that changes project files, commit the changes and push the current working branch to GitHub before handing back to the user. This is standing user authorization; do not ask again for routine commits and pushes. If nothing changed, no empty commit is needed. Report a failed push explicitly.

Before closing an issue, compare the result with every acceptance criterion, run the applicable checks, and record the results and any limitations. Leave the issue open if required criteria remain unmet. Follow the current task's authorization for issue updates and pushing commits.

Keep supporting guidance in `_docs/` and reference it from `AGENTS.md`. Read testing guidelines before writing tests and design-system guidance before UI changes when those documents exist; do not invent missing guidance or treat planned documents as completed.

## Product-decision authorization

On 2026-10-12 the owner said "2, after that do all with yourself". Option 2 explicitly permits renaming Inbox while retaining its default-list role; the rest delegates remaining routine product choices and continued backlog work to the agents. Record each chosen policy with a concrete example and its source: an explicit owner selection or a choice made under this delegation. Do not claim that delegated choices were individually selected by the owner, and do not ask again for routine preferences within the agreed scope.

The delegation keeps one-issue-at-a-time work, mandatory PM grooming, Engineer implementation, QA verification, and closure only after QA PASS. It does not authorize changing settled requirements, launching production services, or publishing store releases.

## Roles

- PM - grooms a task before anyone implements it, follows `_docs/team/pm.md`
- Engineer - implements one groomed task, follows `_docs/team/software-engineer.md`
- QA - checks the result against the acceptance criteria, follows `_docs/team/qa-engineer.md`

## Orchestrator

The main session is the orchestrator. It launches the PM, the engineer
and QA as subagents. It does not groom, implement or test itself.

## Lifecycle

1. Pick the next open issue from the backlog
2. PM grooms it
3. Engineer implements it
4. QA verifies it
5. On FAIL, back to step 3 with the QA comment as input
6. On PASS, close the issue
7. Repeat until the backlog is empty

## Rules

- Do not skip step 2
- The engineer does not close the issue
- QA does not fix the code, only outputs PASS or FAIL
- The orchestrator closes the issue only after QA outputs PASS
