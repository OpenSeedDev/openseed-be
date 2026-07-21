---
name: vertical-slice
description: Autonomously deliver SeedRank backend backlog tasks as dependency-aware vertical-slice PRs, process PR review feedback, recover failures, and honor the /merge-approved user gate. Use for backlog orchestration, VS/OPS implementation, PR review polling, merge readiness, or workflow recovery.
---

# Autonomous Vertical Slice

## Establish Context

1. Read the repository `AGENTS.md`.
2. Read `docs/workflow/backlog.yml` and validate it.
3. Read active workflow failures and implementation lessons.
4. Query GitHub for open and merged task PRs; GitHub is authoritative for runtime state.
5. Never infer a merged dependency from a local state value alone.

Read [references/autonomous-policy.md](references/autonomous-policy.md) before orchestrating more than one task or handling reviews. Read [references/state-schema.md](references/state-schema.md) before creating or changing a state file.
When `settings.delivery_mode` is `fast_build`, read
[references/fast-build-policy.md](references/fast-build-policy.md) before selecting or delivering work.

## Run an Orchestrator Tick

Perform work in this order:

1. Reconcile task PRs, merged PRs, CI results, comments, reviews, and unresolved threads.
2. Process actionable review feedback for open PRs before starting more work. In Fast Build's
   Build stage, defer this step unless the user explicitly requested a review tick.
3. Detect exact `/merge-approved` comments by the configured approver against the live PR head; do not push an approval-evidence-only commit.
4. Enable or complete merge only when the approval, checks, resolved threads, dependency, and conflict gates pass.
5. Confirm actual GitHub merges and immediately release dependency and resource locks without waiting for a completion-state PR.
6. Select ready tasks with `select_ready_tasks.rb`, up to the configured worker limit. Fast Build
   passes open implementation PRs separately so a stacked successor can be selected. The current
   project setting selects exactly one task per tick.
7. Deliver the selected task in one isolated worktree. Do not run another implementation task or
   implementation sub-agent concurrently in single-task mode.
8. Leave ambiguous or repeatedly failing tasks blocked while continuing unrelated tasks.

In normal mode, do not select a task when any dependency is not actually merged, another active task
holds one of its locks, or an open PR already represents it. Fast Build uses the stricter lane and
stack rules in its policy instead of requiring every implementation dependency to be merged.

## Deliver One Task

1. Create a clean worktree from the selected base and a correctly named branch. The selected base is
   latest `origin/main` in normal mode and the designated parent PR branch for a Fast Build stack.
2. Create the task state from `assets/slice-state.yml`; record dependency evidence and status `PLANNING`.
3. Read the feature specification, backlog row, applicable policies, and lessons.
4. Write a compact task plan containing user value, scope, exclusions, API/data impact, acceptance criteria, and test list.
5. Move directly to `IMPLEMENTING`; no user approval is required.
6. Write tests first and run them to capture an intentional Red result.
7. Implement the smallest production change that makes focused tests pass.
8. Refactor while tests remain green.
9. Run focused tests locally. In normal mode, also run `./gradlew test` once for migrations,
   authentication/authorization, visibility, Point/Unit accounting, concurrency, or shared
   error/configuration changes. In Fast Build, run the related risk suite and reserve the local full
   suite for stack checkpoints, shared build/config changes, and final review stabilization.
10. Update OpenAPI, ERD, and a new Flyway migration when applicable.
11. Inspect the diff and scan for secrets and unrelated changes.
12. Validate the task state and backlog.
13. Commit and push, create a draft PR, record its URL and number, set `AWAITING_PR_REVIEW`, then make one final state commit and mark the PR ready.
14. Return after the final push. Do not wait synchronously for CI; the next coordinator tick reconciles it and repairs deterministic failures up to three attempts.
15. Do not push CI, review-count, approval, merge, or completion evidence when no implementation or review-fix content changed.

The PR body must include the user outcome, acceptance criteria, Red/Green/full-test evidence, API and DB changes, dependency evidence, known limitations, and the automatic-review policy. A Stacked PR must also include its parent PR, base branch, stack depth, merge order, and parent-change risk.

## Process Reviews

For every open task PR, collect general comments, submitted reviews, inline comments, unresolved threads, and CI results.

- Identify comments by stable GitHub IDs and never process the same revision twice.
- For an actionable defect, add or adjust a regression test first, implement the correction, run focused tests, update documents, commit, push, and reply with evidence. Run local full tests only for the high-risk categories in Deliver One Task; final-head CI supplies the normal full regression run.
- Record implementation mistakes under `docs/workflow/lessons/implementation-lessons.yml`.
- If feedback is ambiguous, contradictory, expands product scope, or requires new authority, mark only that task `BLOCKED` and ask on the PR.
- Never treat `/merge-approved` as a request to ignore unresolved review feedback.
- After every push, re-fetch reviews because line positions and CI conclusions may have changed.

## Reach Merge Readiness

Treat the live PR as `AWAITING_USER_MERGE` only when all conditions hold:

- focused and full tests passed;
- required CI succeeded for the current head commit;
- no active failure exists;
- all dependencies were confirmed merged before the branch was created;
- no unresolved review thread remains;
- the configured approver posted an exact `/merge-approved` comment on this PR;
- the PR is mergeable and has no conflict.

Read the comment ID, URL, author, timestamp, and approved head SHA from GitHub at decision time. Do not commit this observation to the PR branch. A later push disables auto-merge and requires a new `/merge-approved` comment for the new head.

The repository Merge Guard validates static task readiness; GitHub required checks, thread resolution, strict up-to-date policy, and protected auto-merge validate live readiness. Never bypass them or force-merge.

## Recover Failures

1. Stop advancement of the affected task.
2. Preserve the last safe status and record stage, command, evidence, retry condition, and attempt count.
3. Diagnose and retry a deterministic failure no more than three times.
4. Promote recurring causes to a regression test, validation script, CI rule, or agent rule.
5. Mark the task `BLOCKED` after the third failed recovery or when human policy input is required.
6. Continue work whose dependency and resource locks are independent.

Read [references/failure-policy.md](references/failure-policy.md) before recording a failure or lesson.

## Validate

Run these checks after workflow-file changes and before every task PR:

```bash
ruby .agents/skills/vertical-slice/scripts/validate_backlog.rb docs/workflow/backlog.yml
ruby .agents/skills/vertical-slice/scripts/validate_state.rb docs/workflow/slices/TASK-ID.yml
ruby .agents/skills/vertical-slice/scripts/check_merge_guard_test.rb
ruby .agents/skills/vertical-slice/scripts/validate_backlog_test.rb
ruby .agents/skills/vertical-slice/scripts/select_backend_test_scope_test.rb
```

Do not advance, commit, or merge a task when its required validation fails.
