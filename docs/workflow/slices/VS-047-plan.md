# VS-047 기업 문의 스레드 생성

## 사용자 결과

회사 이메일 인증을 마친 Company는 공개형·반공개형·매칭형으로 게시된 아이디어의 작성자에게 1:1 문의 스레드를 시작한다. 같은 아이디어·Company·작성자 조합으로 다시 요청해도 기존 스레드를 반환한다.

## 근거와 선행 조건

- 백로그: VS-047, 선행 VS-008·VS-010, resource lock `messaging`
- 실제 Merge 증거: VS-008 PR #45가 `3da0b57f270b85780800e521ad92338dcd9e11fd`, VS-010 PR #48이 `72ab1b6fc8c0148bb09493864d280d709914b80b`로 Merge됨
- 인증 Company는 `users.role=COMPANY`와 `company_profiles.verified_at`을 모두 만족한다.
- 문의 대상은 `PUBLISHED` 상태이며 공개 범위가 `PUBLIC`, `SEMI_PUBLIC`, `MATCHING` 중 하나인 아이디어다.

## 포함 범위

- `POST /api/v1/ideas/{ideaId}/message-thread`
- 현재 서버 상태 기준 Company 역할과 회사 인증 완료 검증
- 세 공개 범위의 게시 아이디어에 동일한 문의 시작 권한 적용
- 아이디어·회사 프로필·게시자 조합당 하나의 `message_threads` 저장
- 순차·동시 중복 요청의 멱등 처리
- 새 UTC Flyway Migration, JPA 매핑, OpenAPI와 ERD 갱신

## 제외 범위

- 메시지 작성·조회와 스레드 목록: VS-048·VS-054
- 읽음·미읽음·실시간·첨부·수정·삭제
- 상세 열람 요청, 차단·신고·운영자 확인
- 아이디어 상세 정보 반환 또는 매칭형 상세 공개

## API와 데이터 영향

- 최초 생성은 `201 Created`, 같은 조합의 재요청은 `200 OK`와 동일한 스레드 ID를 반환한다.
- 응답은 스레드 ID, 아이디어 ID, 생성 시각만 반환하며 회사 이메일과 아이디어 상세를 노출하지 않는다.
- 인증이 없으면 `401 INVALID_ACCESS_TOKEN`, 인증 Company가 아니거나 회사 인증이 완료되지 않았으면 `403 VERIFIED_COMPANY_REQUIRED`다.
- 존재하지 않거나 게시되지 않은 아이디어는 `404 IDEA_NOT_FOUND`로 동일 응답한다.
- DB 유일 제약 `(idea_id, company_profile_id, author_id)`이 최종 중복 방지선이다.

## 인수 조건과 테스트 목록

- [ ] 인증 Company는 PUBLIC·SEMI_PUBLIC·MATCHING 아이디어에 각각 문의 스레드를 생성한다.
- [ ] 세 공개 범위 모두 같은 안전한 응답 필드만 반환한다.
- [ ] 같은 조합을 다시 요청하면 동일 ID를 반환하고 DB 행은 하나다.
- [ ] 같은 조합의 동시 요청에도 DB 행은 하나다.
- [ ] 일반 사용자와 미인증 회사 프로필은 거부된다.
- [ ] 인증이 없거나 잘못된 토큰은 거부된다.
- [ ] Draft·없는 아이디어는 존재 여부를 더 노출하지 않고 거부된다.
- [ ] OpenAPI, 새 Migration, ERD와 JPA `ddl-auto: validate`가 일치한다.
- [ ] 집중 테스트와 고위험 전체 테스트가 통과한다.

## 미결정 사항

없음. 메시지와 Thread 목록 계약은 후속 슬라이스에서 확정한다.
