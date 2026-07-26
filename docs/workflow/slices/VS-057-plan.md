# VS-057 기여자 Top 5

## 사용자 결과

메인 화면 방문자는 채택된 피드백 기여가 많은 사용자 5명을 현재 프로필 아이디와 주요 기여 카테고리까지 함께 확인할 수 있다.

## 근거와 선행 조건

- 백로그: VS-057, 선행 VS-027, `feedback` resource lock
- 실제 Merge 증거: VS-027 PR #73이 `e7a13dc7ec2c12f03a0b596b999b91e85644349b`로 Merge됨
- 기능 명세 HOME-06: 전체 Contribution 수 기준 Top 5
- 상세 기획: 동점은 최근 30일 Contribution 수와 마지막 기여 시각으로 결정
- 기간 계산은 주입된 `Clock`의 현재 시각부터 30일 전을 포함한다.

## 포함 범위

- 인증 없이 조회하는 `GET /api/v1/contributors/top`
- 전체 Contribution 수 내림차순
- 동점 시 최근 30일 Contribution 수, 마지막 기여 시각 내림차순
- 완전 동점의 안정적인 응답을 위한 사용자 ID 오름차순
- 현재 프로필 아이디, 전체·최근 30일 기여 수, 주요 기여 카테고리 반환
- 주요 카테고리는 전체 Contribution에서 가장 많은 카테고리이며 동률은 카테고리명 오름차순
- OpenAPI 익명 조회 계약

## 제외 범위

- 페이지네이션과 Top 5 이외 순위
- 기간·카테고리 필터
- 기여 상세 목록과 마이페이지
- 별도 집계 테이블·Migration

## API와 데이터 영향

- 응답은 최대 5개 배열이며 각 항목은 `profileId`, `totalContributionCount`, `recent30DayContributionCount`, `primaryCategory`를 반환한다.
- 기존 `contributions`, `ideas`, `users`를 읽는 집계 쿼리만 추가한다.
- 새로운 DB 테이블·컬럼·Migration은 없다.

## 인수 조건과 테스트 목록

- [ ] 전체 Contribution 수가 많은 순으로 최대 5명을 반환한다.
- [ ] 전체 수 동점은 최근 30일 수가 많은 사용자가 앞선다.
- [ ] 전체·최근 수 동점은 마지막 기여 시각이 최근인 사용자가 앞선다.
- [ ] 현재 시각 기준 정확히 30일 전 Contribution을 최근 집계에 포함한다.
- [ ] 현재 프로필 아이디 변경 결과를 조회 시점에 반환한다.
- [ ] 주요 카테고리는 전체 기여 최빈 카테고리이며 동률은 이름 오름차순이다.
- [ ] Contribution이 없으면 빈 배열을 반환한다.
- [ ] 익명 요청과 OpenAPI 200 계약을 제공한다.
