# VS-036 회수 가능 대기 잔액 수동 지급

## 사용자 결과

로그인 사용자는 Seed Unit 회수 때 즉시 받지 못해 고정된 대기 잔액을 직접 지급 요청하고, 당일 회수 지급 한도와 지갑 여유분 안에서 Point로 받을 수 있다.

## 근거와 선행 조건

- 백로그: VS-036, 선행 VS-035, resource lock `unit`·`wallet`
- 실제 Merge 증거: VS-035 PR #76, merge commit `082e629db0d3ed807f3b0e11f5919392f3d0ab1a`
- 정책: 고정 실현액 재평가 금지, Asia/Seoul 하루 총 회수 지급 500P, 지갑 상한 2,000P, 자동 지급 제외

## 포함 범위

- `POST /api/v1/me/pending-recovery/payout`
- 현재 사용자의 지갑·대기 잔액 잠금
- 기존 Lot 회수의 즉시 지급액과 수동 지급액을 합산한 일일 500P 제한
- 지갑 여유분과 대기 잔액 범위 내 지급 및 대기 잔액 차감
- 수동 지급 기록과 `CREDIT/PENDING_RECOVERY_PAYOUT` append-only 원장
- 순차·동시 재요청의 중복 지급 방지
- 새 UTC Flyway Migration, JPA 매핑, OpenAPI와 ERD 갱신

## 제외 범위

- 대기 잔액 자동 지급
- 회수 가격·실현액 재평가
- Point 만료·양도·현금화·외부 거래
- 운영자 지급·정정 API

## API와 데이터 영향

- 요청 본문 없이 현재 지급 가능한 최대 금액을 지급한다.
- 응답은 `payoutId`, `paidAmount`, `balanceAfter`, `pendingRecoveryBalance`, `policyDate`, `paidAt`을 반환한다.
- 지급 가능액이 0이면 부작용 없이 `paidAmount=0`, `payoutId=null`을 반환한다.
- `pending_recovery_payouts`는 실제 지급 건별 금액·지급 후 잔액·정책 날짜·시각을 보존한다.
- 지급 원장은 payout ID를 출처로 사용해 지급 기록과 일대일 대응한다.

## 인수 조건과 테스트 목록

- [ ] 이미 고정된 대기 잔액을 가격 재계산 없이 지급하고 같은 금액만 차감한다.
- [ ] 기존 Lot 회수 즉시 지급액과 당일 수동 지급액 합계가 500P를 넘지 않는다.
- [ ] 지갑이 2,000P를 넘지 않으며 여유가 없으면 대기액이 유지된다.
- [ ] 대기 잔액이 없거나 지급 가능액이 0인 재요청은 기록·원장을 추가하지 않는다.
- [ ] Asia/Seoul 다음 정책 날짜에는 새 500P 한도를 적용한다.
- [ ] 동시 요청도 지갑·대기 잔액·지급 기록·원장을 중복 반영하지 않는다.
- [ ] 인증·OpenAPI·DB 제약과 전체 회귀 테스트가 통과한다.

## 미결정 사항

없음. 수동 요청은 현재 지급 가능한 최대액을 원자적으로 지급하며 자동 지급은 후속 기능으로 유지한다.
