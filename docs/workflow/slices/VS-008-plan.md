# VS-008 회사 인증 완료와 Company 권한

## 사용자 결과

회사 이메일로 받은 일회성 인증 토큰을 제출하면 회사 프로필이 인증되고 사용자의 역할이 즉시 `COMPANY`로 전환된다. 같은 토큰은 한 번만 사용할 수 있으며 만료·위조·재사용·무효화 토큰은 동일한 오류로 거부된다.

## 선행 조건

- VS-007 PR #41이 `2026-07-21T07:43:11Z`에 Merge되었고 Merge 커밋은 `c8e94e61e3ae7e60c00080168d94e94bb2c9a2ac`이다.
- 브랜치는 위 Merge 커밋인 최신 `origin/main`에서 생성했다.
- 백로그 선행 조건은 VS-007, resource lock은 `company`, `auth`다.

## 포함 범위

- `POST /api/v1/companies/verifications/confirm`
- URL-safe 토큰 원문을 SHA-256 해시로 조회
- 토큰 행 잠금과 재검증을 통한 일회성 소비
- 만료·위조·재사용·무효화 토큰의 동일 오류 계약
- `company_verifications.used_at`, `company_profiles.verified_at`, `users.role=COMPANY`의 단일 트랜잭션 변경
- 기존 활성 Access Token에서도 서버의 최신 사용자 역할을 기준으로 Company 권한 확인
- 내 계정, 로그인·갱신 응답에 최신 `COMPANY` 역할과 `VERIFIED` 상태 반영
- OpenAPI, ERD, 새 Flyway Migration

## 제외 범위

- 관리자 승인과 일반 사용자 이메일 인증
- 회사 인증 취소·회사 프로필 변경
- 기업 관심, 기업 문의와 별도의 Company 전용 업무 API
- 상세 인증 이력 조회와 운영자 화면

## API·데이터 영향

- 요청은 `{ "token": "..." }`, 성공은 `204 No Content`다.
- 토큰 누락·빈 값은 `400 VALIDATION_ERROR`다.
- 만료·위조·재사용·무효화 토큰은 구체적 원인을 노출하지 않고 `401 INVALID_COMPANY_VERIFICATION_TOKEN`이다.
- `users.role` DB 제약에 `COMPANY`를 추가하는 새 Migration을 적용한다.

## 인수 조건과 테스트 목록

- 유효 토큰은 한 번만 성공하며 토큰·프로필·사용자 역할을 같은 시각으로 갱신한다.
- 성공 직후 기존 Access Token의 `/api/v1/me`가 `role=COMPANY`, `companyVerificationStatus=VERIFIED`를 반환한다.
- 이후 로그인과 Refresh가 발급하는 Access Token의 역할도 `COMPANY`다.
- 위조·만료·재사용·무효화 토큰은 동일한 401 계약이며 DB 상태를 바꾸지 않는다.
- 같은 토큰의 동시 확인은 정확히 한 건만 성공한다.
- 토큰 원문과 회사 이메일은 응답·일반 오류에 노출되지 않는다.
- OpenAPI에 요청·204·400·401 계약이 노출된다.
- PostgreSQL Testcontainers에서 Migration, `ddl-auto: validate`, 집중 테스트와 전체 테스트가 통과한다.
