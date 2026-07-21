# VS-024 구조화 피드백 등록

## 사용자 결과

로그인 사용자는 게시된 아이디어에 유형과 100자 이상의 구체적인 의견을 남기고, 선택적으로 근거 URL 또는 설명을 함께 저장하며 활동 Point 결과를 확인할 수 있다.

## 근거와 선행 조건

- 백로그: VS-024, 선행 VS-011, `feedback` resource lock
- 실제 Merge 증거: VS-011 PR #49가 `482c811d2307a2224f63a6b55b95bab208ce88c2`로 Merge됨
- 기능 명세 FB-01·POINT-04: 유형과 100자 이상 의견 필수, 근거 URL·설명 선택, 20P 하루 5회
- VS-031의 `PointRewardService`와 `FEEDBACK_CREATED` 출처 정책을 재사용한다.

## 포함 범위

- `POST /api/v1/ideas/{ideaId}/feedbacks`
- 유형: 문제 공감, 대상 고객, 해결책, 수익 모델, 경쟁 서비스, 기타
- 앞뒤 공백 제거 후 100~2,000자 의견
- 선택 근거 URL과 근거 설명
- 활성 Bearer 사용자와 게시된 아이디어 검증
- 세 공개 범위의 게시 아이디어에 동일하게 등록
- Feedback 저장과 20P 활동 보상을 하나의 트랜잭션으로 처리
- 하루 5회 초과 시 피드백은 저장하되 보상 전액 소멸 원장을 기록
- 새 UTC Flyway Migration, JPA 매핑, OpenAPI와 ERD 갱신

## 제외 범위

- 피드백 목록과 채택 우선 Cursor: VS-025
- 수정·삭제와 이전 내용 이력: VS-026
- 채택·Contribution·100P·타임라인: VS-027
- 좋아요·조회 지표: VS-028~029
- 신고·차단·운영자 기능

## API와 데이터 영향

- 요청은 `type`, `content`, 선택 `evidenceUrl`, `evidenceDescription`을 받는다.
- 응답은 피드백 ID, 아이디어 ID, 작성자 내부 ID, 정규화된 내용·근거, 작성 시각과 보상 원래·실지급·소멸 금액을 반환한다.
- 인증 실패는 `401 INVALID_ACCESS_TOKEN`, Draft·없는 아이디어는 `404 IDEA_NOT_FOUND`, 입력 오류는 `400 VALIDATION_ERROR`다.
- `feedbacks`는 향후 채택·수정·삭제를 위해 `accepted_at`, `edited_at`, `deleted_at` nullable 컬럼을 선행 보존한다.

## 인수 조건과 테스트 목록

- [ ] 유형·100자 의견·선택 근거를 저장하고 20P를 지급한다.
- [ ] 의견과 근거 설명의 앞뒤 공백을 제거하며 근거는 생략할 수 있다.
- [ ] 정규화 후 정확히 100자를 허용하고 99자 이하·2,000자 초과를 거부한다.
- [ ] 지원하지 않는 유형과 http/https가 아닌 근거 URL을 거부한다.
- [ ] 인증 누락·위조 토큰을 `401 INVALID_ACCESS_TOKEN`으로 거부한다.
- [ ] Draft·없는 아이디어를 `404 IDEA_NOT_FOUND`로 숨긴다.
- [ ] PUBLIC·SEMI_PUBLIC·MATCHING 게시 아이디어에 등록할 수 있다.
- [ ] 하루 여섯 번째 피드백은 저장하되 20P 전액 소멸 원장을 남긴다.
- [ ] Point 지급 실패 시 Feedback도 롤백된다.
- [ ] OpenAPI에 경로, Bearer 인증, 요청 Enum과 201·400·401·404 계약이 노출된다.
- [ ] PostgreSQL 제약과 고위험 전체 테스트가 통과한다.
