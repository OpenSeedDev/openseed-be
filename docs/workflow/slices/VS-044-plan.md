# VS-044 기업 관심 등록·취소·전체 공개 목록

## 사용자 결과

회사 이메일 인증을 마친 Company는 공개 범위와 관계없이 게시된 아이디어에 관심을 등록하거나 취소한다. Guest를 포함한 누구나 관심 기업 목록에서 회사명과 관심 등록 시각만 확인한다.

## 근거와 선행 조건

- 백로그: VS-044, 선행 VS-008·VS-010, resource lock `company`, `idea`
- 실제 Merge 증거: VS-008 PR #45와 VS-010 PR #48이 GitHub에서 Merge됨
- 기업 관심은 `users.role=COMPANY`와 `company_profiles.verified_at`을 모두 만족한 사용자만 변경한다.
- 대상은 `PUBLISHED` 상태인 `PUBLIC`, `SEMI_PUBLIC`, `MATCHING` 아이디어다.

## 포함 범위

- `PUT /api/v1/ideas/{ideaId}/company-interest`
- `DELETE /api/v1/ideas/{ideaId}/company-interest`
- `GET /api/v1/ideas/{ideaId}/company-interests`
- 순차·동시 중복 등록과 반복 취소의 멱등 처리
- 관심 등록·취소 시 공개 목록 수와 아이디어 타임라인 반영
- 새 UTC Flyway Migration, JPA 매핑, OpenAPI와 ERD 갱신

## 제외 범위

- 회사 활동 마이페이지: VS-054
- 랭킹 계산·메인 카드 반영: VS-038~VS-040
- 상세 열람 요청, 알림, 분석 이벤트, 관리자 기능
- 회사 이메일·도메인·사용자 ID와 아이디어 상세 정보 노출

## API와 데이터 영향

- 등록·취소는 현재 상태와 공개 관심 수를 반환한다.
- 공개 목록은 최신 관심 시각·ID 역순이며 각 항목에 `companyName`, `interestedAt`만 반환한다.
- 인증이 없으면 `401 INVALID_ACCESS_TOKEN`, 인증 Company가 아니면 `403 VERIFIED_COMPANY_REQUIRED`다.
- 존재하지 않거나 게시되지 않은 아이디어는 `404 IDEA_NOT_FOUND`로 동일 응답한다.
- DB 유일 제약 `(idea_id, company_profile_id)`이 최종 중복 방지선이다.

## 인수 조건과 테스트 목록

- [ ] 인증 Company는 세 공개 범위의 게시 아이디어에 관심을 등록·취소한다.
- [ ] 같은 회사의 반복·동시 등록은 관심 한 건만 저장한다.
- [ ] 반복 취소는 성공하며 관심 수가 음수가 되지 않는다.
- [ ] 일반 사용자·미인증 회사와 유효하지 않은 인증은 변경할 수 없다.
- [ ] Draft·Archived·없는 아이디어는 동일한 404로 거부된다.
- [ ] Guest가 세 공개 범위의 목록을 조회할 수 있다.
- [ ] 목록에는 회사명·관심 등록 시각 외 필드가 없다.
- [ ] 등록·취소는 아이디어 타임라인에 한 번씩 기록된다.
- [ ] OpenAPI, 새 Migration, ERD와 JPA `ddl-auto: validate`가 일치한다.
- [ ] 집중 테스트와 공개 범위·인가·DB 고위험 전체 테스트가 통과한다.

## 미결정 사항

없음. 랭킹 입력과 회사 활동 목록은 각 후속 슬라이스에서 현재 관심 테이블을 기준으로 구현한다.
