# Autonomous Orchestration Policy

## Authority and Runtime State

- `docs/workflow/backlog.yml` defines task metadata, order, dependencies, and resource locks.
- GitHub PR and merge state defines whether work is queued, active, awaiting review, or merged.
- `docs/workflow/slices/TASK-ID.yml` records auditable evidence for one implementation PR.
- A state file cannot declare a dependency merged unless GitHub confirmed it before branch creation.
- CI, reviews, approval comments, mergeability, and Merge state are live GitHub facts. Do not create a commit solely to mirror those facts.

## Concurrency

- Use one coordinator and at most `settings.max_parallel_workers` task workers.
- Give each worker a dedicated Git worktree, branch, state file, and PR.
- Do not edit one task worktree from another worker.
- Do not dispatch two tasks sharing any `resource_locks` value.
- Recompute ready work after each merge, new PR, block, or lock release.

## Scheduling

Select tasks deterministically by ascending `order`, then ID. A task is ready only when:

1. every dependency is in the configured initial merged set or has a GitHub PR whose state is `MERGED`;
2. it has no open or merged implementation PR already associated with it;
3. no active work holds a shared resource lock;
4. an available worker slot exists.

Do not create speculative dependent PRs. A future task may be planned, but its branch and implementation begin only after all dependencies merge.

## Review Polling

- Poll open task PRs at the configured interval.
- Review processing has priority over starting new tasks.
- Treat a review as processed only after its comment ID and resulting commit are recorded.
- Rebase or merge `main` into an open branch only when needed to resolve an actual dependency or conflict. Run focused tests and use final-head CI for the full regression run; also run local full tests for high-risk changes.
- Approval is bound to the PR head SHA. Any code-changing push invalidates earlier approval.

## Merge Gate

Only the configured approver can authorize a merge by posting a comment whose trimmed body is exactly `/merge-approved`.

Before enabling merge, verify from GitHub:

- the approval comment was created for the current head SHA;
- all review threads are resolved;
- Build and Test and Vertical Slice Merge Guard pass;
- dependencies remain merged;
- the PR is mergeable;
- no active failure is recorded.

Do not write these observations back to the open PR branch. A `synchronize` event disables an existing auto-merge request so a later push always requires a fresh approval comment.

GitHub branch protection must remain enabled. Do not use administrator bypass, force push, or direct push to `main`.

## Merge Synchronization

- An actual GitHub Merge immediately satisfies downstream dependency checks and releases resource locks.
- Do not create or wait for a per-task completion-state PR.
- Historical state maintenance may be batched separately and must never block Ready task selection.

## Failure Isolation

Use `BLOCKED` only for the affected task. Retain its locks if parallel edits would make recovery unsafe; otherwise release locks explicitly. Other ready tasks continue. After three failed automatic attempts, leave a concise PR comment containing the failing check, reproduction command, last safe commit, and required human decision.
