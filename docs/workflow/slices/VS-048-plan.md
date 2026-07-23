# VS-048 읽음 없는 텍스트 메시지

## 사용자 결과

기업 문의 스레드의 Company와 아이디어 게시자는 비동기 텍스트 메시지를 보내고 안정적인 Cursor로 시간순 조회한다. 응답에는 메시지 ID, 텍스트, 발신자 ID, 발신 시각만 있으며 읽음 상태와 미읽음 수는 제공하지 않는다.

## 근거와 선행 조건

- 백로그: VS-048, 선행 VS-047, resource lock `messaging`
- 실제 Merge 증거: VS-047 PR #51이 `402e56cb8c9786eff799167388fb30259dff9dae`로 Merge됨
- Stack parent: VS-044 PR #74, Head `6e6a04d55d267f17016b470c9f153e9d3d865609`
- VS-047의 문의 스레드는 공개형·반공개형·매칭형 아이디어 모두에서 생성된다. 메시지 권한은 공개 범위가 아니라 스레드 참여자로 결정한다.

## 포함 범위

- `POST /api/v1/message-threads/{threadId}/messages`
- `GET /api/v1/message-threads/{threadId}/messages?cursor=&size=`
- Company 사용자와 아이디어 게시자만 해당 스레드의 전송·조회 허용
- 공백 제거 후 1~2000자의 텍스트 메시지
- 발신 시각·ID 기준 오름차순 Cursor 목록
- 메시지 저장 시 스레드 `updated_at` 갱신
- 새 UTC Flyway Migration, JPA 매핑, OpenAPI와 ERD 갱신

## 제외 범위

- 읽음 상태, 미읽음 수, 실시간 전송, 입력 중 표시, 알림
- 첨부, 메시지 수정·삭제
- 상세 열람 요청, 사용자 차단·신고, 운영자 확인과 관리자 페이지
- 스레드 목록과 Company 마이페이지 통합: VS-054

## API·데이터 계약

- 메시지 생성은 `201 Created`이며 `id`, `content`, `senderId`, `sentAt`을 반환한다.
- 목록은 같은 필드의 `items`, `nextCursor`, `hasNext`를 반환하고 읽음 관련 필드는 반환하지 않는다.
- 참여자가 아니거나 없는 스레드는 존재 여부를 구분하지 않고 `404 MESSAGE_THREAD_NOT_FOUND`로 응답한다.
- 인증이 없거나 잘못된 토큰은 `401 INVALID_ACCESS_TOKEN`이다.
- 빈 텍스트, 2000자 초과, 잘못된 Cursor 또는 1~100 밖의 size는 `400 VALIDATION_ERROR`다.
- `message_thread_messages`는 스레드와 발신자 FK, `(thread_id, sent_at, id)` 조회 인덱스를 가진다.

## 인수 조건과 테스트 목록

- [ ] Company 참여자는 모든 공개 범위에서 만들어진 Thread에 메시지를 보낸다.
- [ ] 아이디어 게시자도 같은 Thread에 답장한다.
- [ ] 일반 사용자 등 비참여자는 메시지를 전송·조회하지 못한다.
- [ ] 없는 Thread와 비참여 Thread는 같은 404 계약을 반환한다.
- [ ] 빈 텍스트와 2000자 초과 텍스트는 저장 없이 거부된다.
- [ ] Cursor 목록은 같은 발신 시각에도 중복·누락 없이 시간순으로 조회된다.
- [ ] 응답·OpenAPI·DB에 읽음 상태와 미읽음 수가 없다.
- [ ] 메시지 저장 시 Thread의 갱신 시각이 발신 시각으로 변경된다.
- [ ] 메시징·인증·권한·DB 집중 회귀와 상태·백로그 검증이 통과한다.

## Fast Build 검증 제한

로컬에서는 메시징과 인증·권한·DB 변경 영향의 집중 테스트를 실행한다. 미병합 Stack parent 기반 긴급 PR이므로 로컬 전체 테스트는 생략하며, 최종 `main` 통합 Head의 CI에서 전체 회귀를 반드시 통과해야 배포할 수 있다.
