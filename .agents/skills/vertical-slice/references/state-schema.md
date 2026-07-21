# Task State Schema

New task state files use schema version 2 at `docs/workflow/slices/TASK-ID.yml`. Existing schema version 1 files remain valid historical records.

## Version 2 Statuses

```text
NOT_STARTED
PLANNING
IMPLEMENTING
CREATING_PR
AWAITING_PR_REVIEW
APPLYING_REVIEW_CHANGES
BLOCKED
AWAITING_USER_MERGE
MERGED_AWAITING_SYNC
COMPLETED_SYNCED
```

Normal delivery moves forward in the listed order, except that review and repair may repeat between `AWAITING_PR_REVIEW` and `APPLYING_REVIEW_CHANGES`. Any active state may become `BLOCKED`; recovery returns it to its last safe active state.

## Required Evidence

- `task.dependencies` and `task.resource_locks` must match the backlog.
- `evidence.dependencies_satisfied` is set only after checking actual GitHub merges.
- New Fast Build state files include `delivery`. A Stacked PR records its parent task and PR,
  base ref and Head SHA, stack root, depth, and merge order. Existing version 2 files without this
  optional mapping remain valid.
- Red, focused, and full test evidence records command, conclusion, and time without full logs or secrets.
- PR states require PR number and URL.
- `AWAITING_USER_MERGE` remains valid for historical records, but new autonomous PRs normally remain `AWAITING_PR_REVIEW` in the committed state file. Live CI, unresolved threads, approval, and mergeability are read directly from GitHub.
- Any later code push clears all `review.merge_approval` fields except `command`.
- Merge and synchronization states require their corresponding commit evidence.

Do not commit a transition only to mirror CI, review-count, approval, Merge, or synchronization observations. Those commits change the PR head, rerun CI, and make approval evidence stale. An actual GitHub Merge is sufficient to release locks; completion-state documentation can be batched later.

## History

Append every status change with `at`, `from`, `to`, `command`, and `note`. Do not edit historical entries. The last `to` must equal the current status.

Validate after every change:

```bash
ruby .agents/skills/vertical-slice/scripts/validate_state.rb docs/workflow/slices/TASK-ID.yml
```
