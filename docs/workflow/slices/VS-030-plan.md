# VS-030 지갑과 Point 원장 조회

## 사용자 결과

로그인 사용자는 자신의 현재 Point 잔액과 회수 가능 대기 잔액을 확인하고, 가입 보너스부터 시작하는 변경 불가능한 Point 원장을 최신순으로 이어 조회할 수 있다.

## 근거와 선행 조건

- 백로그: VS-030, 선행 VS-001, `wallet` resource lock
- 실제 Merge 증거: VS-001 기능 PR #15가 `e0ee1fb88d290e80de665feee7e7d614210d93ca`로 Merge됨
- VS-001이 사용자별 `PointWallet`과 가입 300P `PointLedger`를 같은 트랜잭션으로 생성한다.
- 명세: `GET /me/wallet`, `GET /me/point-ledgers`; 원장은 Append-only이며 목록은 Cursor 계약을 사용한다.

## 포함 범위

- `GET /api/v1/me/wallet`: `balance`, `pendingRecoveryBalance`, `updatedAt`
- `GET /api/v1/me/point-ledgers`: `(createdAt, id)` 내림차순 Cursor 목록
- 원장 항목: `id`, `type`, `originalAmount`, `paidAmount`, `expiredAmount`, `balanceAfter`, `sourceType`, `sourceId`, `createdAt`
- Bearer Access Token과 활성 세션·사용자 검증
- 타인의 지갑과 원장 비노출
- DB에서 `point_ledgers` UPDATE/DELETE를 거부하는 Append-only 제약
- OpenAPI, ERD, UTC Flyway Migration 갱신

## 제외 범위

- 아이디어 게시·피드백·채택 Point와 초과분 소멸: VS-031
- 일일 첫 접속 100P: VS-056
- Point 차감과 Seed Unit 구매: VS-032
- 회수·대기 잔액 생성 및 지급: VS-035~036
- Point 만료·양도·현금화·외부 거래

## API와 Cursor 계약

- 지갑 조회는 인증된 내부 사용자 ID의 지갑 하나만 반환한다.
- 원장 기본 크기는 20, 허용 범위는 1~100이다.
- Cursor는 마지막 항목의 `createdAt`과 `id`를 URL-safe Base64로 인코딩한다.
- 다음 페이지 조건은 `createdAt < cursor.createdAt OR (createdAt = cursor.createdAt AND id < cursor.id)`이다.
- 응답은 `items`, `nextCursor`, `hasNext`를 사용하며, 다음 항목 존재 확인을 위해 `size + 1`건을 조회한다.
- 누락·위조·잘못된 Cursor와 범위 밖 size는 `400 VALIDATION_ERROR`다.
- 유효하지 않은 인증은 `401 INVALID_ACCESS_TOKEN`이다.

## 인수 조건과 테스트 목록

- [ ] 가입 직후 지갑 조회가 `300P`, 대기 잔액 `0P`를 반환한다.
- [ ] 가입 보너스 원장이 모든 금액·잔액·출처·시각 필드를 반환한다.
- [ ] 동일 시각 원장을 포함해 Cursor 페이지 사이에 중복·누락이 없다.
- [ ] 마지막 페이지는 `hasNext=false`, `nextCursor=null`이다.
- [ ] size 경계 1과 100을 허용하고 0, 101을 거부한다.
- [ ] 잘못된 Cursor를 `400 VALIDATION_ERROR`로 거부한다.
- [ ] 인증 누락·위조 Token을 `401 INVALID_ACCESS_TOKEN`으로 거부한다.
- [ ] 다른 사용자의 지갑·원장을 반환하지 않는다.
- [ ] 원장 UPDATE와 DELETE가 PostgreSQL에서 실패한다.
- [ ] OpenAPI에 두 경로, Bearer 인증, 조회·오류 계약이 노출된다.
- [ ] 기존 가입·인증과 전체 테스트가 회귀하지 않는다.

## 데이터 변경

- 기존 지갑·원장 컬럼과 가입 데이터는 변경하지 않는다.
- 새 UTC Flyway Migration은 `point_ledgers` 수정·삭제를 거부하는 trigger function과 trigger만 추가한다.
- 향후 원장 유형과 출처 확장은 해당 변경을 만드는 슬라이스가 별도 Migration으로 수행한다.
