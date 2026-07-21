# VS-028 멱등 좋아요 등록·취소

## 사용자 결과

로그인 사용자는 세 공개 범위의 게시 아이디어에 좋아요를 중복 없이 등록·취소하고, 현재 좋아요 수와 자신의 상태를 즉시 확인할 수 있다.

## 근거와 전달 전략

- 백로그: VS-028, 명시 선행 VS-010, `idea` resource lock
- Fast Build lane parent: VS-017 PR #60, branch `codex/vs-017-timeline`, Head `4e229ac9cff80441284531a46721eb71ae851f20`
- Stack: VS-012 → VS-016 → VS-017 → VS-028, depth 4 checkpoint
- 기능 명세 REACT-01: 로그인 사용자의 좋아요 등록·취소는 멱등해야 한다.

## 포함 범위

- `PUT /api/v1/ideas/{ideaId}/like`
- `DELETE /api/v1/ideas/{ideaId}/like`
- 사용자·아이디어당 하나의 DB unique 제약과 충돌 안전 upsert
- 반복·동시 등록/취소의 멱등 처리
- 게시된 PUBLIC·SEMI_PUBLIC·MATCHING 아이디어에 동일 적용
- 응답과 아이디어 상세에 `liked`, `likeCount` 제공
- 새 UTC Flyway Migration, JPA 매핑, OpenAPI와 ERD 갱신

## 제외 범위

- 좋아요 사용자 목록
- 조회수 집계와 반복 제한: VS-029
- 랭킹 계산 반영: VS-038

## 인수 조건과 테스트 목록

- [ ] 처음 등록과 반복 등록 모두 200이며 최종 행은 하나다.
- [ ] 처음 취소와 반복 취소 모두 200이며 최종 행은 없다.
- [ ] 같은 사용자의 동시 등록에도 중복 행이 생기지 않는다.
- [ ] 세 공개 범위의 게시 아이디어에 좋아요할 수 있다.
- [ ] Draft·Archived·없는 아이디어는 `404 IDEA_NOT_FOUND`다.
- [ ] 인증 누락·위조는 `401 INVALID_ACCESS_TOKEN`이다.
- [ ] 상세 응답은 공개 범위 필드 정책을 유지하며 현재 좋아요 수와 조회자의 상태를 제공한다.
- [ ] OpenAPI·DB unique 제약·JPA·ERD가 일치한다.
- [ ] depth 4 checkpoint 전체 테스트가 통과한다.
