# VS-001 PR 리뷰 수정 계획

## 대상 PR

- PR: https://github.com/OpenSeedDev/openseed-be/pull/15
- 확인 시각: 2026-07-20T18:58:59+09:00
- CI: `Build and Test` 성공

## 리뷰 댓글 3613409768

- 위치: `src/main/java/com/seedrank/common/config/TimeConfig.java:15`
- 댓글: 대한민국/서울 시간대를 사용하도록 수정
- 상태: 미해결

### 문제 요약

현재 공용 `Clock` Bean이 `Clock.systemUTC()`를 사용한다. 리뷰 정책에 따라 애플리케이션의 기준 Zone ID를 대한민국 표준시인 `Asia/Seoul`로 명시해야 한다.

### 수정 방법

1. `TimeConfig`에 `ZoneId`를 사용한다.
2. 공용 Clock을 `Clock.system(ZoneId.of("Asia/Seoul"))`로 생성한다.
3. 시간대 문자열을 상수로 정의해 오타와 중복을 방지한다.

### 회귀 테스트

- `TimeConfigTest`를 먼저 추가한다.
- 주입된 Clock의 Zone ID가 정확히 `Asia/Seoul`인지 검증한다.
- 기존 `SignupIntegrationTest`를 다시 실행해 가입 시각 저장과 응답이 회귀하지 않았는지 확인한다.
- `./gradlew clean test`로 전체 테스트를 실행한다.

### 영향 범위

- 공용 Clock을 주입받는 현재·후속 기능의 날짜 계산 기준 Zone ID가 `Asia/Seoul`이 된다.
- 현재 가입 기능은 `clock.instant()`과 PostgreSQL `timestamptz`를 사용하므로 같은 실제 시점을 저장한다.
- `Instant` 기반 API 응답 형식은 변경하지 않는다.

### 문서·DB 영향

- OpenAPI 변경 없음
- ERD 변경 없음
- Flyway Migration 변경 없음
- 구현 과정에서 발견된 시간대 정책은 구현 교훈에 기록한다.

## 승인 명령

위 계획만 승인하려면 정확히 다음과 같이 입력한다.

`VS-001 수정 계획 승인`

승인 전에는 리뷰 수정 코드와 테스트를 작성하지 않는다.
