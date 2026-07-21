# 자동 수직 슬라이스 워크플로우

현재는 모든 VS 구현 PR을 먼저 준비하는 임시 Fast Build 모드다. 사용자는 Build 단계가
끝난 뒤 PR을 stack 선행 순서대로 리뷰하고 병합을 승인한다. 모든 VS 작업에 구현 PR이
생기면 같은 수동 tick이 자동으로 기존 리뷰·복구·병합 흐름으로 돌아온다.

```mermaid
flowchart TD
  A["사용자: 다음 tick 실행"] --> B["GitHub 구현 PR 동기화"]
  B --> C{"모든 VS에 PR이 있는가?"}
  C -- "아니요" --> D["최대 3개 Delivery Lane 선택"]
  D --> E["main 또는 Parent PR Branch에서 구현"]
  E --> F["Red → Green → 집중·위험 테스트 → Stacked PR"]
  F --> B
  C -- "예" --> G["기존 수동 리뷰·복구·병합 모드"]
  G --> H["선행 PR부터 리뷰와 /merge-approved"]
  H --> I["보호 규칙 아래 Merge·Child base 갱신"]
```

## 사용자가 하는 일

1. Fast Build 동안에는 `/merge-approved`를 남기지 않고 PR 생성을 기다린다.
2. Build 종료 안내 후 stack 선행 순서로 PR을 리뷰한다.
3. 수정이 필요하면 일반 리뷰 댓글이나 인라인 댓글을 남긴다.
4. 현재 코드로 병합해도 되면 PR 일반 댓글에 정확히 `/merge-approved`를 남긴다.

코드 변경 Push가 발생하면 이전 병합 승인은 낡은 것으로 처리한다. 변경된 코드를 다시 확인한 뒤 새 `/merge-approved` 댓글을 남긴다.

## 자동 처리 순서

1. Build 단계에서는 Merge되었거나 열린 구현 PR이 있는 VS 작업을 계산한다.
2. Ready 작업을 최대 3개 Delivery Lane에서 고른다.
3. Merge되지 않은 선행 작업은 해당 PR branch를 base로 Stacked PR을 만든다.
4. 리뷰는 `리뷰 tick 실행`에서만 우선 처리한다.
5. 모든 VS에 PR이 생기면 기존 수동 리뷰·복구·병합 순서로 자동 전환한다.

## 안전 장치

- Stacked 후속 작업은 집중 테스트를 통과한 열린 선행 PR만 base로 사용한다.
- 같은 lane의 다음 작업만 resource lock을 승계하고 다른 lane과 같은 lock을 공유하지 않는다.
- 서로 다른 미병합 stack이 필요한 fan-in 작업은 시작하지 않는다.
- Stack 깊이 4에서 전체 테스트 checkpoint와 리뷰·병합을 기다린다.
- 동일 실패는 최대 3회 복구하고, 이후 해당 PR만 차단한다.
- `/merge-approved`, 해결된 리뷰 대화, 성공한 필수 검사, 충돌 없음이 모두 충족돼야 Merge된다.
- 관리자 우회, 강제 Push, `main` 직접 Push는 허용하지 않는다.
- GitHub PR 상태가 실행 중 상태의 최종 기준이다.

## 상태 확인

Ready 작업을 로컬에서 확인하려면 다음 명령을 사용한다. 자동 실행기는 GitHub에서 얻은 Merge 및 활성 작업 JSON을 함께 전달한다.

```bash
ruby .agents/skills/vertical-slice/scripts/select_ready_tasks.rb \
  docs/workflow/backlog.yml \
  --merged '["VS-006"]' \
  --active '[]' \
  --open '[{"id":"VS-009","resource_locks":["idea"],"head_ref":"codex/vs-009-draft","pr_number":29,"stack_depth":1}]'
```

백로그와 규칙 자체는 다음 명령으로 검증한다.

```bash
ruby .agents/skills/vertical-slice/scripts/validate_backlog.rb docs/workflow/backlog.yml
ruby .agents/skills/vertical-slice/scripts/validate_backlog_test.rb
ruby .agents/skills/vertical-slice/scripts/check_merge_guard_test.rb
```
