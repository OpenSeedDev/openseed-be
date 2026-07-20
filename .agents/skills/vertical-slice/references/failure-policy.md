# Failure and Lesson Policy

실패를 두 저장소로 분리하여 다음 슬라이스에서 같은 실수를 예방한다.

## Workflow Failures

`docs/workflow/lessons/workflow-failures.yml`에 기록한다.

다음 문제를 포함한다.

- 잘못된 상태 전이
- 승인 없이 다음 단계 실행
- 잘못된 브랜치 또는 동기화 실패
- CI·GitHub·도구 호출 순서 문제
- 상태 파일과 실제 Git·PR 상태 불일치
- 환경이나 자동화 설정 문제

## Implementation Lessons

`docs/workflow/lessons/implementation-lessons.yml`에 기록한다.

다음 문제를 포함한다.

- 비즈니스 규칙 누락
- 테스트 누락 또는 잘못된 테스트
- API 계약 오류
- DB·Migration·트랜잭션 오류
- 권한·보안 문제
- PR 리뷰로 발견된 구현 문제
- 리팩터링 과정의 회귀 문제

## Recording Rules

- 비밀번호, 토큰, 개인정보, 전체 로그를 기록하지 않는다.
- 증거는 명령, 파일, 테스트 이름, PR 댓글 ID처럼 재현 가능한 최소 정보만 남긴다.
- 같은 원인은 `fingerprint`로 식별하고 기존 항목의 `recurrence_count`를 증가시킨다.
- 해결되지 않은 항목은 `status: active`, 해결된 항목은 `status: resolved`로 기록한다.
- 현재 슬라이스와 관련된 `active` 항목을 다음 슬라이스 계획 전에 읽는다.
- 반복 횟수가 2회 이상이면 회귀 테스트나 자동 검증 규칙을 제안한다.
- 반복 횟수가 3회 이상이면 사용자 승인 후 테스트, 스크립트, `AGENTS.md`, Skill 또는 CI에 방지책을 반영한다.
- PR 리뷰 수정은 가능하면 회귀 테스트를 추가하고 `review_comment_id`를 기록한다.

## Required Workflow Failure Fields

```text
id
fingerprint
status
occurred_at
slice_id
stage
command
symptom
root_cause
resolution
prevention
recurrence_count
promoted_to
evidence
```

## Required Implementation Lesson Fields

```text
id
fingerprint
status
occurred_at
slice_id
category
review_comment_id
symptom
root_cause
fix
regression_test
applies_to
recurrence_count
promoted_to
evidence
```
