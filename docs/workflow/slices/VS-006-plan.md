# VS-006 회사 프로필과 도메인 검증

## 사용자 결과

로그인 사용자는 회사명과 회사 이메일을 등록할 수 있고, 서버는 이메일과 도메인을 정규화한 뒤 무료 개인 메일 도메인을 거부한다. 등록 직후 회사 인증 상태는 `PENDING`이며 회사 이메일은 응답이나 로그에 노출하지 않는다.

## 선행 조건

- VS-001 기능 PR #15가 Merge되었고 Merge 커밋 `e0ee1fb88d290e80de665feee7e7d614210d93ca`가 기준 커밋의 조상이다.
- 백로그 선행 조건은 VS-001, resource lock은 `company`다.

## 포함 범위

- `POST /api/v1/companies/profile`
- Bearer Access Token 인증과 활성 세션·사용자 검증
- 회사명 공백 제거 및 길이 검증
- 회사 이메일 정규화, 도메인 추출과 소문자 ASCII 정규화
- 무료 개인 메일 도메인과 그 하위 도메인 차단
- 사용자당 회사 프로필 하나, 동일 회사 이메일 하나만 저장
- 회사 프로필 등록 후 내 계정의 회사 인증 상태 `PENDING`
- CompanyProfile Migration, ERD, OpenAPI 계약

## 제외 범위

- 인증 토큰 생성·해시·만료·재발송 무효화와 메일 발송: VS-007
- 인증 링크 확인, `verifiedAt` 기록, Company 역할 부여: VS-008
- 기업 관심과 1:1 문의
- 관리자 승인, 무료 도메인 운영 관리 API

## API·데이터 계약

- 요청: `companyName`, `companyEmail`
- 성공: `201 Created`; `companyProfileId`, `companyName`, 정규화된 `companyDomain`, `verificationStatus=PENDING`, `createdAt`
- 회사 이메일은 성공·오류 응답에 포함하지 않는다.
- 무료 메일은 `400 COMPANY_EMAIL_DOMAIN_NOT_ALLOWED`, 중복 프로필·이메일은 `409 COMPANY_PROFILE_ALREADY_EXISTS`, 인증 실패는 `401 INVALID_ACCESS_TOKEN`이다.
- `company_profiles.verified_at`은 후속 인증 완료를 위해 nullable로 두되 이번 슬라이스에서는 항상 null이다.

## 인수 조건과 테스트 목록

- 유효한 사용자가 회사 프로필을 등록하면 정규화된 회사명·도메인과 `PENDING`을 반환하고 DB에는 정규화된 이메일을 저장한다.
- 대문자 도메인과 앞뒤 공백을 정규화한다.
- Gmail, Naver, Daum, Kakao, Outlook, Hotmail, Yahoo, iCloud, Proton 계열 무료 도메인 및 하위 도메인을 거부한다.
- 잘못된 이메일, 빈 회사명, 길이 초과 요청을 `400`으로 거부한다.
- 인증 누락·위조·폐기 세션을 `401`로 거부한다.
- 한 사용자의 중복 등록과 동일 회사 이메일의 중복 등록을 `409`로 거부한다.
- 응답과 오류에 회사 이메일·인증 정보가 노출되지 않는다.
- 등록 전 `GET /api/v1/me`는 `NOT_STARTED`, 등록 후에는 `PENDING`이다.
- OpenAPI에 요청·성공·400·401·409 계약과 Bearer 보안이 노출된다.
- PostgreSQL Testcontainers에서 새 Migration과 `ddl-auto: validate`가 통과한다.
