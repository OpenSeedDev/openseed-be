# VS-033 Seed Unit 구매 중복·동시성 보호

## 사용자 결과

로그인 사용자는 같은 구매 요청을 재전송하거나 여러 구매를 동시에 보내도 Point가 중복 차감되지 않고, 잔액과 구매 한도가 항상 보존되는 상태로 Seed Unit을 구매할 수 있다.

## 근거와 선행 조건

- 백로그: VS-033, 선행 VS-032, `unit`·`wallet` resource lock
- 실제 Merge 증거: VS-032 PR #50, merge commit `535d258ea66545359f1ad501e57f9aca5545b5dd`
- 제품 명세: 구매 시 지갑을 잠그고 잔액·일일·아이디어별 한도를 검사하며 중복 요청 키로 재전송을 멱등 처리한다.

## 포함 범위

- 구매 API의 필수 `Idempotency-Key` 헤더 계약
- 사용자별 요청 키 유일 제약과 Lot에 요청 키 보존
- 같은 키·같은 요청의 순차 및 동시 재전송을 동일 구매 결과로 처리
- 같은 키를 다른 아이디어·Unit·확인 가격에 재사용하면 충돌로 거부
- 사용자 지갑 행 잠금 뒤 멱등성·잔액·일일·아이디어별 한도를 재검사해 동시 구매 직렬화
- 새 Flyway Migration, JPA 매핑, OpenAPI와 ERD 갱신

## 제외 범위

- 구매 취소와 요청 키 만료·정리
- 보유 Lot 조회와 회수: VS-034~036
- 여러 사용자 사이의 전역 직렬화

## API와 데이터 영향

- `POST /api/v1/ideas/{ideaId}/unit-purchases`는 1~100자의 비어 있지 않은 `Idempotency-Key`를 요구한다.
- 같은 사용자의 같은 키와 같은 구매 입력은 기존 Lot과 최초 구매 직후 잔액을 반환하고 추가 차감·원장을 만들지 않는다.
- 같은 키를 다른 구매 입력에 사용하면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.
- `seed_unit_lots.purchase_request_key`와 `(user_id, purchase_request_key)` 유일 제약을 추가한다.

## 인수 조건과 테스트 목록

- [ ] 동일 키의 순차 재전송은 같은 Lot을 반환하고 Point·원장·Lot을 한 번만 반영한다.
- [ ] 동일 키의 동시 재전송은 모두 같은 Lot을 반환하고 한 번만 차감한다.
- [ ] 서로 다른 키의 동시 구매에도 잔액이 음수가 되지 않고 일일·활성 원금 300P 한도를 넘지 않는다.
- [ ] 같은 키를 다른 입력에 재사용하면 `409 IDEMPOTENCY_KEY_REUSED`이고 추가 부작용이 없다.
- [ ] 누락·공백·101자 초과 키는 `400 VALIDATION_ERROR`다.
- [ ] OpenAPI에 필수 `Idempotency-Key` 헤더와 409 계약이 노출된다.
- [ ] PostgreSQL 유일 제약, 집중 테스트와 전체 회귀 테스트가 통과한다.
