# VS Global Fast Build Stack

모든 VS 기능 PR을 병합 전에 구현하기 위한 전역 직렬 Stack 운영 계약이다.

## 통합 기준 브랜치

- branch: `codex/vs-fast-build-trunk`
- 시작점: 생성 시점의 최신 `origin/main`
- 자체 PR: 만들지 않음
- 목적: 기존에 분리된 열린 VS Stack leaf의 코드를 한 개발 base에 모음
- 불변성: 첫 전역 기능 PR이 생성된 뒤에는 이동하거나 강제 Push하지 않음

초기 통합 대상 leaf 순서는 다음과 같다.

1. PR #64 — Idea Stack leaf
2. PR #63 — AI Stack leaf
3. PR #61 — Feedback Stack leaf
4. PR #54 — Metrics Stack leaf
5. PR #65 — Unit Stack leaf

Coordinator는 GitHub에서 실제 열린 상태와 Head SHA를 다시 확인한 뒤 통합한다. 이미 Merge되거나
대체된 leaf는 최신 GitHub 상태로 치환한다.

## 신규 구현

1. 첫 신규 VS PR은 통합 기준 브랜치를 base로 한다.
2. 이후 PR은 직전 `VS-GLOBAL` PR branch를 base로 한다.
3. 한 tick에는 작업 하나와 PR 하나만 만든다.
4. 모든 신규 상태 파일은 `strategy: global_stacked_pr`, `stack_root: VS-GLOBAL`을 기록한다.
5. 전역 depth가 4의 배수일 때 로컬 전체 테스트를 실행한다.
6. 중간 Merge 없이 `max_stack_depth: 64`까지 계속 구현한다.

## 최종 리뷰·병합 순서

먼저 기존 Stack을 각각 parent부터 leaf까지 병합한다.

1. Idea: #52 → #57 → #60 → #64
2. AI: #38 → #58 → #62 → #63
3. Feedback: #53 → #59 → #61
4. Metrics: #54
5. Unit: #55 → #65

기존 Stack 병합이 끝나면 전역 통합 브랜치의 내용은 main에 모두 포함된 상태가 된다.
전역 첫 기능 PR의 base를 main으로 변경하고 CI·리뷰를 다시 확인한 뒤, 이후 전역 child를
순서대로 main에 연결해 병합한다.

통합 기준 브랜치 자체는 main에 병합하지 않는다.
