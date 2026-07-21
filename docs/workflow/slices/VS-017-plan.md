# VS-017 아이디어 성장 타임라인

## 사용자 결과

사용자는 게시된 아이디어가 언제 공개되고 업데이트됐는지 시간순으로 확인할 수 있다. 작성자는 보관된 아이디어의 기록도 계속 확인할 수 있으며, 내부 버전 내용은 노출되지 않는다.

## 근거와 전달 방식

- 백로그: VS-017, 선행 VS-010, resource lock `idea`
- Fast Build parent: VS-016 PR #57, `codex/vs-016-archive`, Head `1bf9a93ff02e993181d87872e20b9b509548e446`
- Stack root VS-012, depth 3, 병합 순서 VS-012 → VS-016 → VS-017
- Merge되지 않은 parent 기반이므로 parent 변경 시 집중 테스트와 diff를 재검증한다.

## 포함 범위

- `GET /api/v1/ideas/{ideaId}/timeline`
- 게시·수정 이벤트를 발생 시각 오름차순으로 조회
- 이벤트 유형, 공개 프로필 아이디, 발생 시각 반환
- 아이디어 수정과 `UPDATED` 이벤트를 같은 트랜잭션으로 저장
- 게시된 아이디어는 공개 범위와 관계없이 타임라인 조회 허용
- 보관 아이디어는 작성자만 조회 허용
- OpenAPI, ERD, 새 UTC Flyway Migration 갱신

## 제외 범위

- 버전 스냅샷 내용·변경 필드·변경 이유 공개
- 피드백 채택 이벤트 생성: VS-027
- 기업 관심 이벤트 생성: VS-044
- 타임라인 수정·삭제 API

## API와 데이터 계약

- 응답은 `events` 배열이며 `type`, `actorProfileId`, `occurredAt`만 제공한다.
- 공개된 아이디어의 Guest 조회를 허용하고 인증 헤더가 있으면 유효성을 검증한다.
- Draft와 비작성자의 Archived 아이디어는 `404 IDEA_NOT_FOUND`로 숨긴다.
- 동일 시각은 이벤트 UUID 오름차순으로 안정 정렬한다.
- `idea_timeline_events.event_type`에 `UPDATED`를 추가한다.

## 인수 조건과 테스트 목록

- [ ] 게시 이벤트를 Guest가 시간순으로 조회한다.
- [ ] 게시 후 수정하면 `PUBLISHED`, `UPDATED`가 같은 아이디어에 순서대로 반환된다.
- [ ] 타임라인은 버전 내용·내부 사용자 UUID를 노출하지 않는다.
- [ ] Draft와 비작성자의 Archived 아이디어를 찾을 수 없는 것으로 처리한다.
- [ ] 작성자는 Archived 아이디어의 기존 타임라인을 조회한다.
- [ ] 수정 저장 실패 시 UPDATED 이벤트도 남지 않고, 이벤트 저장 실패 시 수정도 롤백된다.
- [ ] OpenAPI와 DB 제약·JPA·ERD가 일치한다.

## 미결정 사항

없음. 사용자에게 버전 이력 상세는 제공하지 않고 마지막 업데이트 시각과 성장 이벤트만 제공한다.
