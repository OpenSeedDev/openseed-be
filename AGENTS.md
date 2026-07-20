# SeedRank Backend Agent Rules

## Communication

- 기본 응답과 작업 기록은 한국어로 작성한다.
- 중요한 정책이 불명확하면 구현하지 말고 사용자에게 확인한다.
- 기존 사용자 변경 사항과 관련 없는 파일은 수정하지 않는다.

## Vertical Slice Workflow

- `VS-NNN 시작`, 승인, 계속, 상태, 리뷰 관련 명령을 받으면 반드시 `.agents/skills/vertical-slice/SKILL.md`를 읽고 따른다.
- 하나의 슬라이스는 테스트, API, 비즈니스 로직, DB, 문서까지 실제 동작하는 상태로 완성한다.
- `VS-NNN 시작` 단계에서는 기능 설명과 테스트 목록까지만 작성하고 구현을 기다린다.
- 테스트 목록 승인 전에는 테스트 코드와 구현 코드를 작성하지 않는다.
- 변경 내용 승인 전에는 커밋, Push, PR 생성을 진행하지 않는다.
- PR 리뷰 수정 계획 승인 전에는 리뷰 내용을 구현하지 않는다.
- PR Merge는 항상 사용자가 직접 수행한다.
- 사용자가 `VS-NNN 머지 완료`라고 알려주면 원격 상태를 확인하고 로컬 `main`을 `--ff-only`로 동기화한다.
- 현재 상태 파일을 기준으로 다음 동작을 결정하며 단계를 임의로 건너뛰지 않는다.

## Development

- Java 21, Spring Boot, Gradle Wrapper를 사용한다.
- 기능별 패키지와 수직 슬라이스 구조를 우선한다.
- 전체 TDD 방식으로 실패 테스트부터 작성한다.
- 필요한 최소 구현으로 테스트를 통과시킨 뒤 리팩터링한다.
- 전체 검증 명령은 `./gradlew clean test`를 기본으로 한다.
- PostgreSQL 통합 테스트는 Testcontainers를 사용한다.
- JPA 스키마는 `ddl-auto: validate`를 유지한다.
- 적용된 Flyway migration 파일은 수정하지 않고 새 버전 파일을 추가한다.
- API 변경 시 OpenAPI 문서를 함께 갱신한다.
- DB 변경 시 Migration과 ERD를 함께 갱신한다.
- 실제 비밀번호, 토큰, API 키, 인증서를 저장소에 기록하지 않는다.

## Git and Pull Requests

- `main`에 직접 개발하지 않는다.
- 기능 브랜치는 `codex/vs-NNN-short-name` 형식을 사용한다.
- 하나의 PR에는 하나의 수직 슬라이스만 포함한다.
- 커밋과 Push는 사용자가 변경 내용을 승인한 이후에만 수행한다.
- PR 생성 후 CI와 리뷰 상태를 확인한다.
- 리뷰 댓글은 수정 계획으로 정리하고 사용자 승인 후 반영한다.
- Merge가 확인된 브랜치만 정리한다.