# VS-025 피드백 Cursor 목록

## 사용자 결과

아이디어 상세 방문자는 삭제되지 않은 피드백을 채택 우선, 최신 작성순으로 안정적으로 이어 조회할 수 있다.

## 근거와 전달 전략

- 백로그: VS-025, 선행 VS-024, `feedback` resource lock
- Fast Build: VS-024 PR #53의 `codex/vs-024-feedback-create` Head `e9fcef4380fc8b0b5a92f1078e660b5b92292813` 기반 Stacked PR
- 기능 명세 FB-03: 채택된 피드백을 목록 상단에 표시
- 안정 순서: `accepted 여부 DESC, createdAt DESC, id DESC`

## 포함 범위

- `GET /api/v1/ideas/{ideaId}/feedbacks?cursor=&size=`
- 게시된 세 공개 범위 아이디어의 공개 가능한 피드백 조회
- 삭제된 피드백 제외
- 작성자의 공개 프로필 아이디와 구조화 피드백 필드 반환
- 기본 20개, 최대 100개, URL-safe Cursor와 `size + 1` 조회
- OpenAPI 계약 갱신

## 제외 범위

- 피드백 수정·삭제·이력 생성: VS-026
- 채택 처리·기여·보상: VS-027
- 내 피드백 통합 조회: VS-053
- 신고·차단·운영자 기능

## 인수 조건과 테스트 목록

- [ ] 채택 피드백이 먼저 나오고 각 그룹은 최신 작성순이다.
- [ ] 같은 작성 시각에도 Cursor 페이지 사이 중복·누락이 없다.
- [ ] 삭제된 피드백은 노출하지 않는다.
- [ ] 빈 목록은 빈 `items`, null `nextCursor`, `hasNext=false`를 반환한다.
- [ ] Draft·없는 아이디어는 `404 IDEA_NOT_FOUND`다.
- [ ] 잘못된 Cursor와 1~100 밖 size는 `400 VALIDATION_ERROR`다.
- [ ] 공개 응답에 내부 작성자 ID와 이메일은 노출하지 않는다.
- [ ] OpenAPI에 Cursor·size 및 200·400·404 계약이 노출된다.
