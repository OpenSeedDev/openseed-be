# SeedRank Backend Agent Rules

## Communication

- 기본 응답과 작업 기록은 한국어로 작성한다.
- 기존 사용자 변경 사항과 관련 없는 파일은 수정하지 않는다.
- 정책이 불명확하면 해당 PR만 `BLOCKED`로 표시하고 다른 독립 작업은 계속한다.

## Autonomous Vertical Slice Workflow

- 백로그 구현, PR 리뷰 반영, 복구 작업에는 `.agents/skills/vertical-slice/SKILL.md`를 반드시 사용한다.
- 자동 백로그 실행은 `.codex/agents/vertical-slice-coordinator.toml`의 `vertical-slice-coordinator`가 조정한다.
- 각 Ready 작업 구현과 리뷰 수정은 `.codex/agents/vertical-slice-worker.toml`의 `vertical-slice-worker`에게 작업 하나씩 위임한다.
- `docs/workflow/backlog.yml`을 작업 순서와 의존성의 기준으로 사용한다.
- 사용자는 생성된 PR을 리뷰하고, 병합 준비가 끝난 PR에 `/merge-approved` 댓글만 남긴다.
- 기능·테스트 계획 승인, 변경 승인, 리뷰 수정 계획 승인을 기다리지 않는다.
- 각 슬라이스는 계획, 실패 테스트, 최소 구현, 리팩터링, 집중 테스트, 위험 기반 전체 테스트, 문서, 커밋, Push, PR까지 자동으로 완성한다.
- PR 리뷰의 실행 가능한 지적은 회귀 테스트와 함께 자동 반영하고 같은 PR에 Push한다.
- 질문이 필요한 리뷰는 해당 PR만 `BLOCKED`로 두며 독립 PR 처리를 중단하지 않는다.
- 일반 모드에서는 선행 작업이 GitHub에서 실제 Merge된 뒤에만 후속 작업을 시작한다.
- 단, `backlog.yml`의 `delivery_mode: fast_build` 동안에는 열린 선행 구현 PR을 base로
  Stacked PR을 만들 수 있다. 상세 규칙은 Skill의 Fast Build Policy를 따른다.
- 동시에 최대 3개 작업자를 사용하고, 작업별 Git worktree와 브랜치를 분리한다.
- 동일한 `resource_locks`를 가진 작업은 동시에 실행하지 않는다.
- 하나의 PR에는 하나의 백로그 작업만 포함한다.
- 자동화는 PR을 임의로 병합하지 않는다. `pado0711`의 정확한 `/merge-approved` 댓글과 필수 검사를 모두 확인해야 한다.
- GitHub의 PR·CI·리뷰·Merge 상태를 런타임 최종 기준으로 사용하고, 관찰 결과만 기록하려는 상태 전용 Push를 하지 않는다.
- 실제 Merge 직후 후속 작업 잠금을 해제하며 `COMPLETED_SYNCED` 전용 PR을 만들거나 기다리지 않는다.
- Fast Build는 모든 `VS-*`에 구현 PR이 생길 때까지만 사용한다. 이후 같은 수동 tick은
  리뷰·복구·선행 순서 병합 중심의 기존 워크플로우로 자동 전환한다.

## Development

- Java 21, Spring Boot, Gradle Wrapper를 사용한다.
- 기능별 패키지와 수직 슬라이스 구조를 우선한다.
- 전체 TDD 방식으로 실패 테스트부터 작성하고 Red 증거를 남긴다.
- 필요한 최소 구현으로 테스트를 통과시킨 뒤 리팩터링한다.
- Red·Green·리팩터링 중에는 관련 테스트만 실행한다.
- 일반 기능 변경은 최종 코드 SHA의 GitHub `Build and Test`를 전체 회귀 검증으로 사용한다.
- 일반 모드에서는 Migration, 인증·인가, 공개 범위, Point·Unit 정합성, 동시성,
  공통 오류·설정 변경에 로컬 `./gradlew test`도 한 번 실행한다.
- Fast Build에서는 위 고위험 변경도 관련 위험 회귀 테스트 묶음을 우선하고, 로컬 전체
  테스트는 stack checkpoint와 리뷰·병합 직전 안정화에서 실행한다.
- 문서·상태·워크플로우 전용 변경은 Java 전체 테스트 대신 백로그·상태·워크플로우 검증만 실행한다.
- PostgreSQL 통합 테스트는 Testcontainers를 사용한다.
- JPA 스키마는 `ddl-auto: validate`를 유지한다.
- 적용된 Flyway migration은 수정하지 않는다. 새 migration은 충돌을 피하도록 UTC 시각 버전을 사용한다.
- API 변경 시 OpenAPI 문서를, DB 변경 시 Migration과 ERD를 함께 갱신한다.
- 실제 비밀번호, 토큰, API 키, 인증서를 저장소에 기록하지 않는다.

## Git and Pull Requests

- `main`에 직접 개발하지 않는다.
- 기능 브랜치는 `codex/vs-NNN-short-name`, 운영 브랜치는 `codex/ops-NN-short-name` 형식을 사용한다.
- 일반 작업은 최신 `origin/main`, Fast Build Stacked 작업은 선택된 parent PR branch에서
  독립 worktree로 시작한다.
- 커밋·Push·PR 생성은 자동으로 수행한다.
- PR 생성 후 CI, 리뷰 댓글, 인라인 대화, 의존성, 충돌 상태를 주기적으로 확인한다.
- CI 완료를 같은 Coordinator tick에서 기다리지 않고 다음 tick에서 동기화한다.
- 리뷰 댓글은 댓글 ID로 멱등 처리하고 처리 결과를 PR 답글로 남긴다.
- 최대 3회 자동 복구 후에도 실패하면 해당 PR만 차단하고 재현 증거를 기록한다.
- Merge된 브랜치만 정리하고 최신 `main`을 동기화한 뒤 새로 해제된 후속 작업을 시작한다.
