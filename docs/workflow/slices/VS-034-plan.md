# VS-034 보유 Seed Unit Lot 조회

## 사용자 결과

로그인 사용자는 자신이 현재 보유한 Seed Unit Lot을 최신 구매순으로 조회하고, 구매 원금·현재 평가값·차이, 잠금 해제 시각과 아이디어별 활성 보유량을 확인할 수 있다.

## 근거와 전달 방식

- 백로그: VS-034, 선행 VS-032, resource lock `unit`
- VS-032 PR #50은 merge commit `535d258ea66545359f1ad501e57f9aca5545b5dd`로 병합됨
- Fast Build lane parent: VS-033 PR #55, `codex/vs-033-purchase-concurrency`, Head `1bee85a3ae491a30ecb7a20691add4a139bd077e`
- Stack root VS-033, depth 2, 병합 순서 VS-033 → VS-034
- Merge되지 않은 parent 기반이므로 parent 변경 시 집중 테스트와 diff를 재검증한다.

## 포함 범위

- `GET /api/v1/me/unit-lots`
- 현재 `LOCKED`인 사용자 소유 Lot만 최신 구매순 Cursor 조회
- Lot의 아이디어·Unit·구매가·원금·현재가·평가값·차이·구매/잠금 해제 시각 반환
- 조회 시각 기준 회수 가능 여부 반환
- 아이디어별 사용자의 활성 Unit 합계 반환
- OpenAPI 계약 갱신

## 제외 범위

- Lot 회수·대기 잔액 지급: VS-035~036
- 회수 완료 Lot 이력 목록
- 가격 갱신 로직: VS-042
- 현금 가치·수익률·지분·배당 표현

## API 계약

- `cursor`는 구매 시각과 Lot UUID를 담은 opaque URL-safe 값이다.
- `size`는 1~100이며 기본값은 20이다.
- 응답은 `items`, `nextCursor`, `hasNext`, 비금전성 안내를 제공한다.
- 평가값은 `units × ideas.current_unit_price`, 차이는 평가값에서 구매 원금을 뺀 내부 Point 차이다.
- 비인증·위조 토큰은 `401 INVALID_ACCESS_TOKEN`, 잘못된 Cursor·size는 `400 VALIDATION_ERROR`다.

## 인수 조건과 테스트 목록

- [ ] 사용자 자신의 활성 Lot만 최신순으로 반환한다.
- [ ] 구매 원금·현재가·평가값·차이를 정확히 계산한다.
- [ ] 같은 아이디어의 여러 Lot은 동일한 전체 활성 Unit 합계를 반환한다.
- [ ] 잠금 해제 직전과 정확한 시각의 회수 가능 여부가 다르다.
- [ ] Cursor 경계와 같은 구매 시각에도 중복·누락이 없다.
- [ ] 타인의 Lot과 회수 완료 Lot은 노출하지 않는다.
- [ ] 인증·Cursor·size 오류와 OpenAPI 계약을 검증한다.

## 미결정 사항

없음. 모든 금액은 서비스 내부 Point 단위이며 현금·지분·배당·수익권이 아니다.
