# VS-022 선택·편집 AI 후보를 Draft로 전환

## 사용자 결과

성공한 AI Job의 소유자는 후보 5개 중 하나를 선택하고 화면에서 편집한 최종 내용을 자신의
Idea Draft로 저장한다. 선택은 게시·보상·버전 생성을 일으키지 않는다.

## 전달 방식과 선행 조건

- 전역 Fast Build 첫 PR: `codex/vs-fast-build-trunk` 기반, stack root `VS-GLOBAL`, depth 1
- base Head: `014eeb37e4cc32760f9cdc78f55fb7db2ad0c2aa`
- VS-020 PR #62의 후보 결과가 전역 통합 브랜치에 포함되어 있다.
- 기존 Stack과 전역 Stack의 최종 병합 전에는 base 변경 시 다시 검증한다.

## 포함 범위

- `POST /api/v1/ai/idea-jobs/{jobId}/draft` 소유자 전용 API
- 1~5 후보 번호와 사용자가 편집한 최종 7개 Idea 필드 검증
- `SUCCEEDED` Job과 저장된 정규화 후보 결과만 선택 허용
- 선택 Job과 후보 번호를 내부 Idea 출처로 보존
- Job당 Draft 하나만 허용하고 순차·동시 중복 선택 방지
- Draft 생성과 AI 출처 저장을 하나의 트랜잭션으로 처리
- 새 Flyway Migration, ERD와 OpenAPI 갱신

## 제외 범위

- AI 결과 자동 게시, 최초 버전, 검증 질문, 게시 보상
- 후보 재생성·삭제·목록과 선택 취소
- 실패 Job 수동 작성 양식과 최종 실패 정책: VS-023
- AI Provider 원본, Prompt, 입력 snapshot 노출

## API·데이터 계약

- 요청은 `candidateNumber` 1~5와 편집 완료한 `title`, `category`, `summary`, `problem`,
  `targetCustomer`, `solution`, `businessModel`을 받는다.
- 모든 내용 필드는 공백 제거 후 비어 있지 않아야 하며 기존 AI 후보 최대 길이를 따른다.
- 응답은 기존 Idea Draft 응답이며 AI Job 내부 정보와 Provider 원본을 포함하지 않는다.
- `ideas.source_ai_job_id`는 Job당 하나로 유일하고 `source_ai_candidate_number`와 함께 저장한다.

## 인수 조건과 테스트 목록

- [x] 소유자가 성공 Job의 후보를 편집해 Draft로 만들고 AI 출처를 내부에 보존한다.
- [x] 선택 결과는 `DRAFT`이며 게시·버전·타임라인·Point 원장을 만들지 않는다.
- [x] 다른 사용자와 없는 Job은 같은 404, 인증 실패는 401을 반환한다.
- [x] Pending·Processing·RetryWait·Failed Job과 결과 없는 성공 Job은 선택할 수 없다.
- [x] 후보 번호와 최종 내용 길이·공백 입력을 검증한다.
- [x] 같은 Job의 반복·동시 선택은 Draft를 하나만 남긴다.
- [x] 응답과 OpenAPI가 AI 원본·입력·Lease 정보를 노출하지 않는다.
- [x] AI Job·Idea Draft 위험 회귀가 통과하고 백로그·상태 검증은 PR 생성 전에 수행한다.
