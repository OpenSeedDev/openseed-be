# Slice State Schema

슬라이스 상태 파일은 `docs/workflow/slices/VS-NNN.yml`에 저장한다. 하나의 파일은 하나의 슬라이스만 나타낸다.

## Required Fields

```text
schema_version
slice.id
slice.title
status
branch
base_commit
current_commit
approvals
review
evidence
failure
history
created_at
updated_at
```

## Status

다음 값만 허용한다.

```text
NOT_STARTED
PLANNING
AWAITING_TEST_APPROVAL
IMPLEMENTING
AWAITING_CHANGE_APPROVAL
CREATING_PR
AWAITING_PR_REVIEW
AWAITING_REVIEW_PLAN_APPROVAL
APPLYING_REVIEW_CHANGES
AWAITING_REVIEW_RECHECK
AWAITING_USER_MERGE
MERGED_AWAITING_SYNC
COMPLETED_SYNCED
```

## Approval Gates

- `IMPLEMENTING` 이후에는 `approvals.test.approved`가 `true`여야 한다.
- `CREATING_PR` 이후에는 `approvals.change.approved`가 `true`여야 한다.
- `APPLYING_REVIEW_CHANGES`와 `AWAITING_REVIEW_RECHECK`에서는 `approvals.review_plan.approved`가 `true`여야 한다.
- 각 승인은 승인 시각과 승인 범위의 SHA-256 값을 기록한다.
- 새 리뷰 수정 계획을 만들면 이전 `review_plan` 승인을 초기화한다.

## Evidence Gates

- `AWAITING_PR_REVIEW` 이후에는 PR 번호와 URL을 기록한다.
- `AWAITING_USER_MERGE`에서는 CI 결과가 `success`여야 한다.
- `MERGED_AWAITING_SYNC` 이후에는 Merge 커밋을 기록한다.
- `COMPLETED_SYNCED`에서는 동기화된 `main` 커밋을 기록한다.
- 테스트 증거에는 명령, 결과, 실행 시각을 요약한다. 전체 로그나 비밀값은 기록하지 않는다.

## History

상태가 바뀔 때마다 `history` 끝에 다음 형식으로 추가한다.

```yaml
- at: "2026-07-20T12:00:00+09:00"
  from: PLANNING
  to: AWAITING_TEST_APPROVAL
  command: "VS-001 시작"
  note: "기능 설명과 테스트 목록 작성 완료"
```

- 기존 이력을 수정하거나 삭제하지 않는다.
- `history`의 마지막 `to` 값은 현재 `status`와 같아야 한다.
- 실패만 기록할 때에는 상태 이력을 추가하지 않는다.

## Create and Validate

1. `assets/slice-state.yml`을 `docs/workflow/slices/VS-NNN.yml`로 복사한다.
2. ID, 제목, 브랜치, 커밋, 시각을 실제 값으로 변경한다.
3. 상태가 바뀌면 승인·증거·이력을 함께 갱신한다.
4. 다음 명령으로 검증한다.

```bash
ruby .agents/skills/vertical-slice/scripts/validate_state.rb docs/workflow/slices/VS-NNN.yml
```

검증이 실패하면 상태를 전이하거나 Git 작업을 진행하지 않는다.
