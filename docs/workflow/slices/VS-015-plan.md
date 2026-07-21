# VS-015 내 아이디어 목록

## 사용자 결과

로그인 사용자는 자신이 작성한 Draft·Published·Archived 아이디어를 한 곳에서 최신 수정순으로 보고, 상태별로 좁혀 조회할 수 있다.

## 근거와 선행 조건

- 백로그: VS-015, 선행 VS-009, resource lock `idea`
- 선행 PR #29가 2026-07-21에 실제 Merge됐고 Merge 커밋 `087d640d87b5283e7ae952e7ad00bc63a5091344`이 기준 커밋의 조상임을 확인했다.
- 열린 task PR #31·#38·#43은 각각 `ops`, `ai-job`, `point-policy` 잠금이며 `idea` 잠금과 겹치지 않는다.
- 게시·보관 상태 전환 자체는 VS-010·VS-016 범위다.

## 범위

### 포함

- `GET /api/v1/me/ideas`
- `status` 선택 필터: `DRAFT`, `PUBLISHED`, `ARCHIVED`
- `updatedAt DESC, id DESC` Cursor 페이지네이션, 기본 20개·최대 100개
- 작성자 소유권과 활성 Bearer 세션 검증
- 목록 카드용 ID, 상태, 제목, 카테고리, 한 줄 요약, 생성·수정 시각 반환
- OpenAPI, ERD, 상태 값 제약을 확장하는 새 Flyway Migration

### 제외

- 게시·보관·수정 상태 전환
- 공개 범위에 따른 타 사용자 목록과 공개 목록
- 본문·검증 질문·버전·Point·기업 관심·좋아요 수
- 카테고리·검색·정렬 선택 기능

## API와 데이터 계약

- `status`를 생략하면 작성자의 모든 상태를 조회한다.
- 목록은 최신 수정순이며 같은 수정 시각은 UUID 내림차순으로 고정한다.
- 응답은 `items`, `nextCursor`, `hasNext`를 가진다.
- `size`는 1~100이며 기본값은 20이다.
- 잘못된 상태·Cursor·페이지 크기는 `400 VALIDATION_ERROR`다.
- 인증 누락·위조·폐기 세션은 `401 INVALID_ACCESS_TOKEN`이다.
- 다른 작성자의 아이디어는 어떤 상태에서도 노출하지 않는다.

## 인수 조건과 테스트 목록

- 작성자는 본인의 Draft·Published·Archived 아이디어만 최신 수정순으로 조회한다.
- 상태 필터는 요청한 상태만 반환하고 생략하면 세 상태를 모두 반환한다.
- Cursor 페이지 사이에 중복·누락이 없고 같은 수정 시각도 안정적으로 처리한다.
- 목록 응답은 카드 필드만 포함하고 문제·해결책·수익 모델·인증 정보를 노출하지 않는다.
- 빈 목록은 빈 `items`, `nextCursor: null`, `hasNext: false`다.
- 잘못된 상태·Cursor·size는 400, 인증 누락은 401이다.
- OpenAPI에 Bearer 인증, 필터·Cursor·size와 200/400/401 계약이 노출된다.
- 새 Migration과 JPA `ddl-auto: validate` 및 집중·전체 테스트가 통과한다.

## 미결정 사항

없음. 게시와 보관을 생성하는 API는 후속 슬라이스에서 구현하고, 이번 테스트는 DB fixture로 세 상태 조회 계약을 검증한다.
