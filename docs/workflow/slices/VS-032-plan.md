# VS-032 Seed Unit 구매와 Lot

## 사용자 결과

로그인 사용자는 게시된 다른 사용자의 아이디어에서 현재 Unit 가격과 결제 후 Point 잔액을 미리 확인하고, 확인한 가격이 유지되는 경우 양의 정수 Unit을 구매해 24시간 잠금 Lot으로 보유할 수 있다.

## 근거와 선행 조건

- 백로그: VS-032, 선행 VS-010·VS-030, `unit`·`wallet` resource lock
- 실제 Merge 증거: VS-010 PR #48 (`72ab1b6fc8c0148bb09493864d280d709914b80b`), VS-030 PR #30 (`9454302d4cd93f7df13a03f5737dd98d452c4b6c`)
- 승인 정책: 양의 정수 Unit, 현재가 재확인, 자기 아이디어 금지, 1회 100P·하루 300P·아이디어별 활성 원금 300P, Point 차감·원장·24시간 잠금 Lot 원자 처리

## 포함 범위

- `POST /api/v1/ideas/{ideaId}/unit-purchase-preview`: 현재가, Unit 수, 총 결제 Point, 예상 잔액, 잠금 해제 예상 시각, 비금전성 안내
- `POST /api/v1/ideas/{ideaId}/unit-purchases`: 확인 가격을 재검증하고 Point 차감·DEBIT 원장·LOCKED Lot 생성
- 게시된 아이디어와 활성 인증 사용자 검증
- 자기 아이디어, 잔액 부족, 1회·일일·아이디어별 활성 원금 한도 오류 계약
- 새 UTC Flyway Migration, JPA 매핑, OpenAPI와 ERD 갱신

## 제외 범위

- 동일 요청 멱등 키와 동시 구매 직렬화: VS-033
- 보유 Lot 목록과 현재 가치 조회: VS-034
- 24시간 후 Lot 전체 회수와 대기 잔액 지급: VS-035~036
- 부분 회수·양도·현금화·외부 거래

## API와 데이터 영향

- Preview 요청은 `units`를, 구매 요청은 `units`와 `confirmedUnitPrice`를 받는다.
- 가격이 달라지면 `409 PRICE_CHANGED`, 본인 아이디어는 `403 SELF_UNIT_PURCHASE`, 잔액 부족은 `409 INSUFFICIENT_POINT`, 구매 한도 초과는 `409 PURCHASE_LIMIT_EXCEEDED`다.
- 구매 원장은 `type=DEBIT`, `sourceType=UNIT_PURCHASE`, `sourceId=lotId`, `originalAmount=paidAmount=principal`, `expiredAmount=0`으로 기록한다.
- Lot은 `units`, `purchasePrice`, `principal`, `purchasedAt`, 정확히 24시간 뒤 `unlockedAt`, `LOCKED` 상태를 보존한다.

## 인수 조건과 테스트 목록

- [ ] Preview가 현재가·총액·예상 잔액·24시간 잠금·비금전성 안내를 반환한다.
- [ ] 구매가 지갑 차감, DEBIT 원장, LOCKED Lot을 한 트랜잭션으로 생성한다.
- [ ] 0 이하 Unit과 요청 형식 오류를 `400 VALIDATION_ERROR`로 거부한다.
- [ ] 인증 누락·위조 토큰을 `401 INVALID_ACCESS_TOKEN`으로 거부한다.
- [ ] Draft·없는 아이디어를 `404 IDEA_NOT_FOUND`로 숨긴다.
- [ ] 본인 아이디어 구매를 `403 SELF_UNIT_PURCHASE`로 거부한다.
- [ ] 확인 가격과 현재가가 다르면 `409 PRICE_CHANGED`로 거부하고 부작용이 없다.
- [ ] Point가 부족하면 `409 INSUFFICIENT_POINT`로 거부하고 부작용이 없다.
- [ ] 1회 100P 초과, 하루 300P 초과, 아이디어별 활성 원금 300P 초과를 `409 PURCHASE_LIMIT_EXCEEDED`로 거부한다.
- [ ] OpenAPI에 두 경로와 인증·성공·오류 계약이 노출된다.
- [ ] PostgreSQL 제약과 전체 회귀 테스트가 통과한다.
