# VS-026 피드백 수정·삭제·이력

## 사용자 결과

피드백 작성자는 자신의 구조화 피드백을 수정하거나 삭제할 수 있고, 서비스는 변경 전 내용과 감사 시각을 내부 이력으로 보존한다.

## 근거와 전달 전략

- 백로그: VS-026, 명시 선행 VS-024, `feedback` resource lock
- Fast Build lane parent: VS-025 PR #59, branch `codex/vs-025-feedback-list`, Head `ad30c0ce09c5cc99d9446311fc6e30cd8bc87a60`
- Stack: VS-024 → VS-025 → VS-026, depth 3
- 기능 명세 FB-02: 작성자만 수정·삭제하고 이전 내용과 시각을 보존

## 포함 범위

- `PUT /api/v1/feedbacks/{feedbackId}`
- `DELETE /api/v1/feedbacks/{feedbackId}`
- 활성 Bearer 사용자와 작성자 소유권 검증
- 수정 시 유형·본문·선택 근거 전체 교체 및 생성과 같은 정규화·검증
- 삭제는 `deleted_at`을 기록하는 soft delete
- 수정·삭제 전에 변경 전 전체 스냅샷과 감사 시각 저장
- 피드백 행 잠금으로 동시 변경 이력 순서 보존
- 새 UTC Flyway Migration, JPA 매핑, OpenAPI와 ERD 갱신

## 제외 범위

- 사용자용 피드백 이력 조회 API
- 채택·Contribution·보상·타임라인: VS-027
- 신고·차단·운영자 기능

## 인수 조건과 테스트 목록

- [ ] 작성자가 피드백을 수정하면 정규화된 최신 내용과 `editedAt`을 반환한다.
- [ ] 수정 전 유형·본문·근거와 감사 시각이 내부 이력에 보존된다.
- [ ] 작성자가 삭제하면 204를 반환하고 삭제 전 스냅샷과 `deletedAt`을 보존한다.
- [ ] 삭제된 피드백은 VS-025 목록에서 제외된다.
- [ ] 다른 사용자, 없는 피드백, 이미 삭제된 피드백은 `404 FEEDBACK_NOT_FOUND`로 숨긴다.
- [ ] 인증 누락·위조는 `401 INVALID_ACCESS_TOKEN`이다.
- [ ] 잘못된 수정 입력은 `400 VALIDATION_ERROR`이며 현재 내용과 이력을 변경하지 않는다.
- [ ] OpenAPI에 PUT·DELETE와 200·204·400·401·404 계약이 노출된다.
