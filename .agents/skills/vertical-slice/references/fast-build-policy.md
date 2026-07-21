# Fast Build Policy

Fast Build는 사용자가 리뷰를 나중에 일괄 수행하는 동안 모든 `VS-*` 구현 PR을
하나의 전역 Stack으로 준비하기 위한 임시 전달 모드다. `OPS-*`와 `E2E-*`에는 적용하지 않는다.

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
3. 최초 실행에서 현재 열린 VS Stack의 leaf branch들을 `settings.global_stack_branch`에
   순서대로 통합한다. 이 통합 기준 브랜치는 PR로 Merge하지 않는 개발 전용 base다.
4. 통합 기준 브랜치가 아직 없으면 기능을 구현하지 않는다. 별도 worktree에서 최신 main을
   시작점으로 각 leaf를 일반 Merge하고 양쪽 기능을 보존한 뒤 전체 테스트와 검증을 통과해 Push한다.
5. 전역 Stack의 첫 새 VS PR은 통합 기준 브랜치를 base로 하고, 이후 PR은 직전 전역 VS PR
   branch를 base로 한다. 모든 새 PR의 `stack_root`는 `VS-GLOBAL`이다.
6. 모든 작업이 같은 전역 Stack에 있으므로 서로 다른 기존 Stack의 fan-in 의존성도 구현할 수 있다.
7. Stack 깊이는 `settings.max_stack_depth`를 넘지 않으며, `full_test_checkpoint_size`마다
   전체 테스트를 실행한다. Checkpoint는 구현을 멈추거나 중간 Merge를 요구하지 않는다.
8. 열린 PR, 대기 중 CI와 나중에 처리할 리뷰는 구현 worker 슬롯을 점유하지 않는다.
9. 한 tick은 Ready 작업을 정확히 하나만 선택한다. Coordinator가 그 작업을 직접 끝내고
   PR을 만든 뒤 종료하며 구현 Worker를 병렬 생성하지 않는다.
10. 다음 Ready 작업은 사용자의 다음 `다음 tick 실행`에서 새로 계산한다.

## 통합 기준 브랜치

- 이름: `settings.global_stack_branch`
- 시작점: 통합 시점의 최신 `origin/main`
- 입력: 각 기존 열린 VS Stack의 leaf Head만 사용한다. Leaf는 해당 Stack의 parent 변경을 포함한다.
- 순서: leaf가 포함한 가장 이른 백로그 order, 작업 ID 순으로 결정한다.
- 충돌: 공통 문서·ERD·Migration·오류 처리와 테스트 fixture의 양쪽 기능을 모두 보존한다.
- 검증: 집중 회귀, `./gradlew test`, 백로그·Merge Guard·상태 검증을 실행한다.
- Push: 최초 생성 시 한 번만 일반 Push한다. Feature child PR 생성 후에는 이동시키거나 강제 Push하지 않는다.
- 통합 브랜치 자체를 main에 Merge하지 않는다.

## 테스트

- TDD의 Red, 최소 구현, 리팩터링과 기능별 집중 테스트는 모든 작업에서 유지한다.
- 고위험 작업은 변경 영역의 위험 회귀 테스트 묶음을 로컬에서 실행한다.
- 로컬 전체 테스트는 stack checkpoint, 공통 빌드·설정 변경, 리뷰·병합 단계의 최종 안정화 때 실행한다.
- 각 PR의 최종 Head CI는 전체 회귀 테스트를 계속 실행한다.
- CI를 같은 tick에서 기다리지 않으며, 현재 작업 실패만 격리한다. 전역 Stack을 안전하게
  이어갈 수 있을 때 다음 수동 tick에서 후속 구현을 계속한다.

## 리뷰와 병합 단계

- Build 단계에서는 일반 tick이 리뷰 수정을 선점하지 않는다. 사용자가 `리뷰 tick 실행`을
  요청한 경우에만 리뷰를 반영한다.
- 사용자는 Build 단계에서 `/merge-approved`를 남기지 않는다. main을 고정해 반복 충돌과
  승인 무효화를 줄인다.
- Build 종료 후 기존 Stack의 parent부터 leaf까지 먼저 리뷰·병합한다.
- 기존 Stack이 모두 Merge되면 전역 Stack의 첫 기능 PR base를 main으로 변경한다.
- 이후 전역 Stack child를 순서대로 main에 연결해 리뷰·병합한다.
- 현재 Head CI, 해결된 thread, 충돌 없음과 정확한 `/merge-approved`를 확인한 뒤 기존 보호된
  auto-merge 절차를 사용한다.

## PR 계약

전역 Stacked PR 본문에는 다음을 추가한다.

- stack parent 작업 ID, PR, branch와 Head SHA
- stack depth와 Merge 순서
- 아직 Merge되지 않은 코드를 기반으로 했다는 사실
- parent 변경 시 후속 PR을 재검증해야 한다는 제한
- `stack_root: VS-GLOBAL`과 전체 전역 Merge 순서

하나의 PR에는 계속 하나의 백로그 작업만 포함한다.
