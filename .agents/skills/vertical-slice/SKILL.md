---
name: vertical-slice
description: Manage SeedRank backend vertical slices through a stateful TDD, approval, Git, PR-review, failure-recovery, and merge-synchronization workflow. Use when the user issues a VS-NNN command such as 시작, 테스트 승인, 변경 승인, 리뷰 가져와, 수정 계획 승인, 리뷰 다시 확인, 머지 완료, 상태, or 계속, or asks to start, resume, inspect, recover, or review a vertical slice.
---

# Vertical Slice Workflow

## Core Rules

- 루트 `AGENTS.md`를 먼저 읽고 따른다.
- 사용자 명령에서 `VS-NNN` 식별자를 추출한다.
- `docs/workflow/slices/VS-NNN.yml`을 해당 슬라이스의 유일한 상태 기준으로 사용한다.
- 상태 파일이 없으면 `VS-NNN 시작`에서만 새로 생성한다.
- 사용자의 명시적인 승인 없이 승인 단계를 통과하지 않는다.
- 실패한 단계에서는 상태를 다음 단계로 변경하지 않는다.
- PR Merge는 수행하지 않고 항상 사용자가 직접 Merge하도록 기다린다.
- 각 작업을 시작하기 전에 현재 브랜치, 작업 트리, 상태 파일을 확인한다.
- 다른 슬라이스나 사용자의 기존 변경을 함께 수정하지 않는다.

## Commands

| 명령 | 수행할 작업 |
|---|---|
| `VS-NNN 시작` | 정책·백로그·교훈을 확인하고 기능 설명, 범위, 인수 조건, 테스트 목록까지만 작성한다. |
| `VS-NNN 테스트 승인` | 승인된 테스트 목록을 기준으로 실패 테스트 작성부터 구현·리팩터링·전체 검증·문서 갱신까지 진행한다. |
| `VS-NNN 변경 승인` | 승인된 변경을 커밋하고 Push한 뒤 PR을 생성하고 CI를 확인한다. |
| `VS-NNN 리뷰 가져와` | PR 리뷰, 인라인 댓글, 대화, CI 결과를 수집하고 수정 계획만 작성한다. |
| `VS-NNN 수정 계획 승인` | 승인된 리뷰 수정만 반영하고 테스트·문서·커밋·Push·CI 확인을 수행한다. |
| `VS-NNN 리뷰 다시 확인` | 새 리뷰와 미해결 대화 및 CI를 다시 확인한다. |
| `VS-NNN 머지 완료` | 실제 Merge 여부를 확인하고 로컬 `main` 동기화와 머지된 브랜치 정리를 수행한다. |
| `VS-NNN 상태` | 현재 상태, 완료 단계, 다음 허용 명령, 실패 여부를 요약한다. |
| `VS-NNN 계속` | 마지막 안전 상태와 실패 기록을 확인한 뒤 중단된 단계부터 재개한다. |

명령의 슬라이스 ID와 상태 파일의 ID가 다르면 중단하고 사용자에게 알린다.

## State Transitions

다음 상태만 사용한다.

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

허용 전이는 다음과 같다.

```text
NOT_STARTED
→ PLANNING
→ AWAITING_TEST_APPROVAL
→ IMPLEMENTING
→ AWAITING_CHANGE_APPROVAL
→ CREATING_PR
→ AWAITING_PR_REVIEW
```

리뷰가 있으면 다음 흐름을 반복한다.

```text
AWAITING_PR_REVIEW
→ AWAITING_REVIEW_PLAN_APPROVAL
→ APPLYING_REVIEW_CHANGES
→ AWAITING_REVIEW_RECHECK
→ AWAITING_REVIEW_PLAN_APPROVAL 또는 AWAITING_USER_MERGE
```

수정할 리뷰가 없고 CI가 성공하면 다음으로 이동한다.

```text
AWAITING_PR_REVIEW
→ AWAITING_USER_MERGE
→ MERGED_AWAITING_SYNC
→ COMPLETED_SYNCED
```

현재 상태에서 허용되지 않는 명령을 받으면 실행하지 말고 현재 상태와 허용되는 다음 명령을 알려준다.

## Start a Slice

`VS-NNN 시작`을 받으면 다음 순서로 수행한다.

1. 로컬 `main`과 `origin/main`이 동기화됐는지 확인한다.
2. 작업 트리가 깨끗한지 확인한다.
3. 백로그에서 `VS-NNN`의 요구사항과 선행 조건을 확인한다.
4. 이전 워크플로우 실패와 구현 교훈을 확인한다.
5. `codex/vs-NNN-short-name` 브랜치를 생성한다.
6. 상태 파일을 만들고 `PLANNING`을 기록한다.
7. 다음 내용을 작성한다.
   - 사용자 가치
   - 포함 범위
   - 제외 범위
   - 정책과 권한
   - API 계약 초안
   - 데이터 변경
   - 인수 조건
   - 성공·실패·경계·권한·트랜잭션 테스트 목록
   - Swagger·ERD·Migration 변경 예상
   - 미결정 사항
8. 구현 파일은 작성하지 않는다.
9. 상태를 `AWAITING_TEST_APPROVAL`로 변경한다.
10. 사용자에게 테스트 목록 승인을 요청한다.

## Implement with TDD

`VS-NNN 테스트 승인`을 받으면 승인 범위가 상태 파일과 일치하는지 확인하고 다음 순서로 수행한다.

1. 상태를 `IMPLEMENTING`으로 변경한다.
2. 승인된 동작을 검증하는 실패 테스트를 먼저 작성한다.
3. 테스트가 의도한 이유로 실패하는지 확인한다.
4. 테스트를 통과시키는 최소 구현을 작성한다.
5. 관련 테스트를 통과시킨다.
6. 테스트가 통과하는 상태에서 리팩터링한다.
7. `./gradlew clean test`를 실행한다.
8. API 변경 시 OpenAPI 문서를 갱신한다.
9. DB 변경 시 새 Flyway Migration과 ERD를 갱신한다.
10. 변경 파일과 diff를 검토한다.
11. 상태를 `AWAITING_CHANGE_APPROVAL`로 변경한다.
12. 구현 결과와 테스트 증거를 사용자에게 제시하고 변경 승인을 요청한다.

실패 테스트가 실제로 실패한 증거 없이 최소 구현으로 넘어가지 않는다.

## Commit, Push, and Create PR

`VS-NNN 변경 승인`을 받으면 다음 순서로 수행한다.

1. 승인 이후 추가된 예상 밖의 변경이 없는지 확인한다.
2. 비밀정보와 불필요한 파일을 검사한다.
3. 승인된 파일만 Stage한다.
4. 커밋을 생성한다.
5. 현재 기능 브랜치를 Push한다.
6. `main` 대상 PR을 생성한다.
7. PR 본문에 다음 내용을 기록한다.
   - 사용자 기능
   - 인수 조건
   - 테스트 결과
   - API 변경
   - DB와 Migration 변경
   - 문서 변경
   - 알려진 제한
8. GitHub CI 완료를 확인한다.
9. CI가 성공하면 상태를 `AWAITING_PR_REVIEW`로 변경한다.
10. CI가 실패하면 상태를 유지하고 실패 내용을 기록한다.

## Handle PR Reviews

`VS-NNN 리뷰 가져와` 또는 `VS-NNN 리뷰 다시 확인`을 받으면 다음을 확인한다.

- 일반 PR 댓글
- 리뷰 요약
- 인라인 리뷰 댓글
- 미해결 대화
- 변경 요청
- CI 결과

이미 처리한 댓글은 댓글 ID로 구분하여 다시 계획하지 않는다.

수정할 내용이 있으면 다음을 작성하고 `AWAITING_REVIEW_PLAN_APPROVAL`로 이동한다.

- 댓글별 문제 요약
- 수정 대상 파일
- 수정 방법
- 추가하거나 변경할 테스트
- 영향 범위
- 문서·Migration 변경 여부

수정할 내용이 없고 모든 대화가 해결됐으며 CI가 성공하면 `AWAITING_USER_MERGE`로 이동한다.

## Apply Review Changes

`VS-NNN 수정 계획 승인`을 받으면 다음 순서로 수행한다.

1. 승인된 댓글과 수정 항목을 확인한다.
2. 상태를 `APPLYING_REVIEW_CHANGES`로 변경한다.
3. 필요한 회귀 테스트를 먼저 작성하거나 수정한다.
4. 승인된 범위만 구현한다.
5. 관련 테스트와 전체 테스트를 실행한다.
6. 필요한 문서를 갱신한다.
7. 수정 내용을 커밋하고 같은 PR 브랜치에 Push한다.
8. CI 완료를 확인한다.
9. 처리한 댓글 ID와 커밋을 상태 파일에 기록한다.
10. 상태를 `AWAITING_REVIEW_RECHECK`로 변경한다.
11. 사용자에게 다시 리뷰하도록 요청한다.

## Complete after User Merge

`VS-NNN 머지 완료`를 받으면 다음 순서로 수행한다.

1. GitHub에서 PR 상태가 실제 `MERGED`인지 확인한다.
2. 확인 전에는 브랜치를 삭제하거나 상태를 완료로 바꾸지 않는다.
3. 상태를 `MERGED_AWAITING_SYNC`로 변경한다.
4. 작업 트리가 깨끗한지 확인한다.
5. 로컬 `main`으로 전환한다.
6. `git pull --ff-only origin main`으로 동기화한다.
7. 로컬과 원격 커밋이 같은지 확인한다.
8. Merge된 기능 브랜치만 로컬과 원격에서 정리한다.
9. 머지 후 `main` CI를 확인한다.
10. 상태를 `COMPLETED_SYNCED`로 변경한다.
11. 다음 슬라이스를 시작할 수 있음을 알린다.

## Failure Handling

단계가 실패하면 다음을 수행한다.

1. 즉시 다음 단계로의 전이를 중단한다.
2. 현재 안전 상태를 유지한다.
3. 실패 단계, 명령, 원인, 증거, 재시도 조건을 상태 파일에 기록한다.
4. 워크플로우 문제는 워크플로우 실패 기록에 추가한다.
5. 구현·테스트·Migration·PR 리뷰 문제는 구현 교훈 기록에 추가한다.
6. 안전한 진단과 수정 계획을 제시한다.
7. 사용자가 `VS-NNN 계속`을 요청하면 전제 조건을 다시 검사한 뒤 실패 지점부터 재개한다.
8. 동일한 실패가 반복되면 회귀 테스트, 검증 스크립트 또는 프로젝트 규칙 강화를 제안한다.

실패를 숨기거나 성공으로 기록하지 않는다.

## Approval Boundaries

다음 표현만 해당 단계의 승인으로 취급한다.

```text
VS-NNN 테스트 승인
VS-NNN 변경 승인
VS-NNN 수정 계획 승인
```

일반적인 `오케이`, `다음`, `계속해`를 중요한 승인으로 임의 해석하지 않는다. 승인 범위가 불분명하면 현재 결과를 유지하고 정확한 승인 명령을 요청한다.

## Required Evidence

상태 전이 전 다음 증거를 확인하고 상태 파일에 기록한다.

- 실행한 테스트 명령과 결과
- 현재 브랜치와 커밋
- 변경 파일 목록
- Migration 적용 결과
- PR URL과 번호
- CI 실행 URL과 결과
- 처리한 리뷰 댓글 ID
- Merge 커밋
- 동기화된 `main` 커밋

## Bundled Resources

- 상태 파일을 생성하거나 수정하기 전에 [references/state-schema.md](references/state-schema.md)를 읽는다.
- 새 상태 파일은 [assets/slice-state.yml](assets/slice-state.yml)을 복사하여 만든다.
- 상태 파일을 수정한 뒤 `ruby .agents/skills/vertical-slice/scripts/validate_state.rb <state-file>`을 실행한다.
- 워크플로우 실패 또는 구현 교훈을 기록할 때 [references/failure-policy.md](references/failure-policy.md)를 읽는다.
- 상태 검증이 실패하면 다음 단계로 진행하지 않는다.
