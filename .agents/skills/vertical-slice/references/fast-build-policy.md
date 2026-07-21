# Fast Build Policy

Fast Build는 사용자가 리뷰를 나중에 일괄 수행하는 동안 모든 `VS-*` 구현 PR을
최대한 빠르게 준비하기 위한 임시 전달 모드다. `OPS-*`와 `E2E-*`에는 적용하지 않는다.

## 진입과 종료

- `docs/workflow/backlog.yml`의 `settings.delivery_mode`가 `fast_build`일 때 활성화한다.
- 예약 실행이 아니라 사용자의 `다음 tick 실행` 명령으로만 한 tick을 실행한다.
- 모든 `VS-*` 작업이 GitHub에서 Merge됐거나 열린 구현 PR을 가지면 Build 단계를 종료한다.
- 종료 후 같은 수동 tick은 리뷰 반영, CI 복구, 최신 main 동기화와 선행 순서 병합만 수행한다.
- 종료를 기록하기 위한 상태 전용 Commit이나 PR을 만들지 않는다. GitHub 상태에서 매 tick 계산한다.

## Build 단계

1. 새 기능 구현을 실행 가능한 리뷰 수정과 병합보다 우선한다.
2. Merge된 작업뿐 아니라 집중 테스트를 통과하고 열린 구현 PR이 있는 작업도 후속 작업의
   구현 의존성을 만족한 것으로 본다.
3. Merge되지 않은 선행 작업이 있으면 최신 선행 PR 브랜치를 base로 Stacked PR을 만든다.
4. 명시적 선행 작업이 모두 Merge됐더라도 같은 resource lock의 이전 열린 PR이 있으면
   그 PR을 lane parent로 사용한다. 이 인공적인 직렬화로 같은 영역의 병렬 충돌을 막는다.
5. 같은 lane에서는 lock을 승계할 수 있지만 서로 다른 lane은 같은 lock을 동시에 사용하지 않는다.
6. Stack 깊이는 `settings.max_stack_depth`를 넘지 않는다. 한도에 도달한 lane은 리뷰·병합
   checkpoint가 끝날 때까지 대기하고 다른 lane을 계속한다.
7. 여러 Merge되지 않은 의존성이 서로 다른 stack에 있으면 fan-in 작업을 시작하지 않는다.
8. 열린 PR, 대기 중 CI와 나중에 처리할 리뷰는 구현 worker 슬롯을 점유하지 않는다.
9. 한 tick은 Ready 작업을 정확히 하나만 선택한다. Coordinator가 그 작업을 직접 끝내고
   PR을 만든 뒤 종료하며 구현 Worker를 병렬 생성하지 않는다.
10. 다음 Ready 작업은 사용자의 다음 `다음 tick 실행`에서 새로 계산한다.

## 테스트

- TDD의 Red, 최소 구현, 리팩터링과 기능별 집중 테스트는 모든 작업에서 유지한다.
- 고위험 작업은 변경 영역의 위험 회귀 테스트 묶음을 로컬에서 실행한다.
- 로컬 전체 테스트는 stack checkpoint, 공통 빌드·설정 변경, 리뷰·병합 단계의 최종 안정화 때 실행한다.
- 각 PR의 최종 Head CI는 전체 회귀 테스트를 계속 실행한다.
- CI를 같은 tick에서 기다리지 않으며, 실패한 lane만 격리하고 다른 lane 구현을 계속한다.

## 리뷰와 병합 단계

- Build 단계에서는 일반 tick이 리뷰 수정을 선점하지 않는다. 사용자가 `리뷰 tick 실행`을
  요청한 경우에만 리뷰를 반영한다.
- 사용자는 Build 단계에서 `/merge-approved`를 남기지 않는다. main을 고정해 반복 충돌과
  승인 무효화를 줄인다.
- Build 종료 후 stack별 선행 PR부터 리뷰한다.
- parent가 Merge되면 바로 다음 child PR의 base를 최신 main으로 바꾸고 필요한 경우에만
  일반 merge로 동기화한다.
- 현재 Head CI, 해결된 thread, 충돌 없음과 정확한 `/merge-approved`를 확인한 뒤 기존 보호된
  auto-merge 절차를 사용한다.

## PR 계약

Stacked PR 본문에는 다음을 추가한다.

- stack parent 작업 ID, PR, branch와 Head SHA
- stack depth와 Merge 순서
- 아직 Merge되지 않은 코드를 기반으로 했다는 사실
- parent 변경 시 후속 PR을 재검증해야 한다는 제한

하나의 PR에는 계속 하나의 백로그 작업만 포함한다.
