# VS-054 Company 관심과 문의

## 사용자 결과

회사 이메일 인증을 마친 Company는 마이페이지에서 자신이 관심 등록한 아이디어와 자신이 시작한 기업 문의 스레드를 한 번에 확인한다.

## 근거와 선행 조건

- 백로그: VS-054, 선행 VS-044·VS-048, resource lock `company`, `messaging`
- 실제 Merge 증거: VS-044 PR #74와 VS-048 PR #80이 GitHub에서 Merge됨
- 조회 주체는 `users.role=COMPANY`이며 `company_profiles.verified_at`이 설정된 사용자다.
- 관심과 문의는 기존 `company_interests`, `message_threads`를 기준으로 조회한다.

## 포함 범위

- `GET /api/v1/me/company-activity`
- 본인 Company Profile의 관심 아이디어 최신순 목록
- 본인 Company Profile의 문의 스레드 갱신 최신순 목록
- 관심 항목의 `ideaId`, `title`, `interestedAt`
- 문의 항목의 `threadId`, `ideaId`, `ideaTitle`, `updatedAt`
- 인증·Company 권한 검증, OpenAPI 계약

## 제외 범위

- 읽음 상태, 미읽음 수, 마지막 메시지 미리보기
- 회사 이메일·도메인과 아이디어 문제·해결책 등 상세 내용
- 일반 사용자의 받은 문의 목록
- Cursor 페이지네이션, 검색, 필터, 알림
- 관심 등록·취소와 메시지 전송 계약 변경

## API와 데이터 영향

- 인증이 없거나 잘못된 토큰은 `401 INVALID_ACCESS_TOKEN`이다.
- 일반 사용자 또는 인증되지 않은 Company는 `403 VERIFIED_COMPANY_REQUIRED`다.
- 활동이 없으면 빈 `interests`, `inquiries` 목록을 반환한다.
- 기존 테이블과 인덱스만 사용하므로 새 Migration과 ERD 변경은 없다.

## 인수 조건과 테스트 목록

- [ ] 인증 Company가 본인의 관심 아이디어와 문의 스레드만 조회한다.
- [ ] 관심은 `interestedAt`, 문의는 `updatedAt` 최신순이다.
- [ ] 관심 항목은 아이디어 ID·제목·관심 시각만 반환한다.
- [ ] 문의 항목은 스레드 ID·아이디어 ID·제목·갱신 시각만 반환한다.
- [ ] 읽음·미읽음과 회사 이메일·아이디어 상세 필드는 없다.
- [ ] 다른 회사의 관심·문의는 노출되지 않는다.
- [ ] 활동이 없으면 두 빈 목록을 반환한다.
- [ ] 일반 사용자·미인증 회사·유효하지 않은 인증은 거부된다.
- [ ] OpenAPI와 집중 테스트가 통과한다.

## 미결정 사항

없음. 페이지네이션과 마지막 메시지 요약은 후속 기능으로 둔다.
