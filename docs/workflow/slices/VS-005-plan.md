# VS-005 내 계정 조회

## 1. 사용자 결과

로그인 사용자는 자신의 안정적인 내부 사용자 ID, 현재 공개 프로필 아이디, 역할, 회사 인증 상태를 조회할 수 있다. 응답에는 이메일·비밀번호·토큰·인증 세션 같은 민감정보를 포함하지 않는다.

## 2. 근거와 선행 조건

- 백로그: VS-005 P1, 내 계정·회사 인증 상태 조회, 선행 VS-002
- 기능 명세: MVP는 로그인 사용자의 현재 프로필 아이디·역할·회사 인증 상태를 제공한다.
- VS-004: 세션 상태와 연결된 Access Token 인증 및 로그아웃 즉시 무효화가 구현되어 있다.
- 회사 프로필·이메일 인증·Company 권한은 후속 VS-006~008에서 구현한다.
- 워크플로우 실패와 구현 교훈을 확인했으며, OpenAPI 테스트에서는 문서 기능을 명시적으로 활성화한다.

## 3. 범위

### 포함

- `GET /api/v1/me`
- Bearer Access Token의 서명·만료·`sid`·활성 세션·사용자 상태 검증
- 인증된 사용자의 `userId`, `profileId`, `role`, `companyVerificationStatus` 반환
- 회사 인증 기능 도입 전 일반 사용자의 회사 인증 상태 `NOT_STARTED` 반환
- 성공·인증 실패·민감정보 비노출 API 테스트
- OpenAPI 계약 갱신

### 제외

- 공개 프로필 아이디 수정: VS-055
- 이메일·비밀번호 변경과 회원 탈퇴
- 프로필 이미지와 자기소개
- 회사 프로필 등록, 인증 메일 발송, 인증 완료와 Company 권한 부여: VS-006~008
- Point·Seed Unit·아이디어·피드백·기여를 포함하는 마이페이지 요약: VS-052
- 다른 사용자의 공개 프로필 조회

## 4. 승인 시 확정할 정책과 권한

- 유효한 `Authorization: Bearer <access-token>`을 필수로 받는다.
- VS-004의 `AccessTokenAuthenticator`를 재사용해 JWT뿐 아니라 연결된 인증 세션과 활성 사용자까지 검증한다.
- 누락·잘못된 형식·위조·만료·`sid` 누락·폐기된 세션의 Access Token은 모두 `401 INVALID_ACCESS_TOKEN`으로 통일한다.
- 응답의 `userId`는 다른 데이터가 연결되는 변경 불가능한 내부 UUID다.
- 응답의 `profileId`는 현재 공개 프로필 아이디이며 중복될 수 있다.
- 응답의 `role`은 현재 `USER`를 반환하고, VS-008 이후 `COMPANY`를 추가할 수 있는 계약으로 둔다.
- `companyVerificationStatus`는 `NOT_STARTED`, `PENDING`, `VERIFIED`의 안정적인 열거형 계약으로 정의한다. 이번 슬라이스에서는 회사 인증 데이터가 아직 없으므로 `NOT_STARTED`만 반환한다.
- 이메일, 비밀번호 해시, Access/Refresh Token, 인증 세션 ID와 상태는 반환하지 않는다.
- 조회 API는 서버 상태를 변경하지 않으며 Refresh Cookie를 요구하거나 갱신하지 않는다.

## 5. API 계약 초안

### 요청

```http
GET /api/v1/me
Authorization: Bearer <access-token>
```

### 성공

- 상태: `200 OK`

```json
{
  "userId": "4f916e24-c97e-4e52-8ff5-7810800385ab",
  "profileId": "seed_user",
  "role": "USER",
  "companyVerificationStatus": "NOT_STARTED"
}
```

### 실패

- 인증 정보 누락 또는 유효하지 않은 Access Token: `401 INVALID_ACCESS_TOKEN`
- 오류 응답은 공통 `code`, `message`, `requestId`, `fieldErrors` 형식을 유지한다.

## 6. 데이터·구현 예상 변경

- 새 테이블, 컬럼, Flyway Migration은 추가하지 않는다.
- `UserRepository`로 인증 주체의 최신 사용자 정보를 조회한다.
- `me` 기능 패키지에 Controller, Service, Response DTO와 회사 인증 상태 열거형을 둔다.
- OpenAPI에 Bearer 보안 요구사항, 성공 응답 필드, `401` 계약을 반영한다.
- ERD 구조 변경은 없으므로 ERD에는 변경 없음임을 확인한다.
- VS-006~008에서는 회사 프로필·인증 데이터에 따라 `PENDING`, `VERIFIED`를 계산하도록 확장한다.

## 7. 인수 조건

- 로그인 사용자는 자신의 내부 ID, 현재 프로필 아이디, 역할과 회사 인증 상태를 조회한다.
- 중복 프로필 아이디를 가진 사용자도 Access Token의 내부 사용자 ID를 기준으로 자기 계정만 조회한다.
- 로그아웃 또는 Refresh 회전으로 폐기된 세션의 Access Token은 사용할 수 없다.
- 정지된 사용자와 유효하지 않은 인증 정보는 동일한 `401 INVALID_ACCESS_TOKEN` 계약으로 거부한다.
- 응답과 일반 로그에 이메일·비밀번호·토큰·세션 식별자가 노출되지 않는다.
- 조회는 DB 상태를 변경하지 않는다.

## 8. 승인할 테스트 목록

### 정상 조회

- [ ] 유효한 Bearer Token으로 조회하면 `200 OK`를 반환한다.
- [ ] 응답의 `userId`, `profileId`, `role`이 인증된 사용자와 일치한다.
- [ ] 회사 인증 기능이 시작되지 않은 일반 사용자의 `companyVerificationStatus`는 `NOT_STARTED`다.
- [ ] 같은 프로필 아이디를 가진 두 사용자는 각 Access Token의 내부 사용자 ID에 해당하는 계정만 조회한다.
- [ ] 조회 전후 사용자와 인증 세션 데이터가 변경되지 않는다.

### 인증·권한·경계

- [ ] Authorization Header가 없으면 `401 INVALID_ACCESS_TOKEN`이다.
- [ ] Bearer 형식이 잘못되거나 Token이 위조·만료·`sid` 누락이면 `401 INVALID_ACCESS_TOKEN`이다.
- [ ] 로그아웃으로 폐기된 세션의 Access Token은 `401 INVALID_ACCESS_TOKEN`이다.
- [ ] Refresh Token 회전으로 폐기된 이전 세션의 Access Token은 거부하고 새 Access Token은 허용한다.
- [ ] `SUSPENDED` 사용자의 Access Token은 `401 INVALID_ACCESS_TOKEN`이다.
- [ ] 인증된 사용자 ID에 해당하는 User가 존재하지 않으면 `401 INVALID_ACCESS_TOKEN`이다.

### 보안·계약·회귀

- [ ] 성공 응답에 이메일, 비밀번호 해시, Access/Refresh Token, 인증 세션 ID가 없다.
- [ ] 실패 응답과 일반 로그에도 위 민감정보가 노출되지 않는다.
- [ ] OpenAPI에 `/api/v1/me`, Bearer 인증, `200` 응답 스키마와 `401` 계약이 노출된다.
- [ ] 새 Migration이 없고 기존 Flyway Migration과 `ddl-auto: validate`가 통과한다.
- [ ] VS-001~004 가입·로그인·갱신·로그아웃 테스트가 회귀하지 않는다.
- [ ] `./gradlew clean test` 전체 테스트가 통과한다.

## 9. 미결정 사항

없음. 위 응답 필드와 회사 인증 상태 열거형을 테스트 승인으로 확정한다.

## 10. 승인 명령

위 범위와 테스트 목록을 승인하려면 정확히 입력한다.

`VS-005 테스트 승인`

승인 전에는 테스트와 구현 코드를 작성하지 않는다.
