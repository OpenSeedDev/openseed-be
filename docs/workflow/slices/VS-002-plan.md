# VS-002 로그인

## 1. 사용자 결과

가입된 활성 사용자가 이메일과 비밀번호로 로그인하면 보호 API에 사용할 Access Token을 받고, 이후 인증 갱신에 사용할 Refresh Token 세션이 안전하게 생성된다.

## 2. 범위

### 포함

- `POST /api/v1/auth/login`
- 이메일 정규화와 BCrypt 비밀번호 검증
- 활성 계정만 로그인 허용
- 짧은 수명의 JWT Access Token 발급
- 불투명 Refresh Token 발급과 해시 저장
- Refresh Token의 HttpOnly Cookie 전달
- 로그인 성공·실패 API/OpenAPI 계약
- 인증 세션 Flyway Migration과 ERD 갱신

### 제외

- Refresh Token 회전과 재사용 탐지: VS-003
- 로그아웃과 세션 무효화 API: VS-004
- 내 계정 조회: VS-005
- 회사 이메일 인증과 Company 권한 부여
- 비밀번호 재설정, 로그인 실패 잠금, 분산 Rate Limit, 소셜 로그인

## 3. 승인 시 확정할 인증 정책

- Access Token은 HMAC SHA-256 JWT이며 수명은 15분이다.
- JWT에는 `sub` 사용자 UUID, `role`, `iat`, `exp`, `jti`만 넣고 이메일·프로필 아이디는 넣지 않는다.
- JWT 서명 키는 환경 설정으로만 주입하며 최소 32바이트를 요구한다. 저장소에는 실제 키를 두지 않는다.
- Refresh Token은 암호학적으로 안전한 256비트 난수이며 수명은 14일이다.
- Refresh Token 원문은 DB와 로그에 저장하지 않고 SHA-256 해시만 `auth_sessions`에 저장한다.
- Refresh Token은 응답 본문이 아닌 `refresh_token` HttpOnly Cookie로 전달한다.
- Cookie는 `Path=/api/v1/auth`, `SameSite=Lax`, 운영 환경 `Secure=true`를 사용한다.
- 동일 사용자의 여러 기기·브라우저 동시 로그인을 허용한다.
- 존재하지 않는 이메일, 틀린 비밀번호, 비활성 계정은 모두 같은 `401 INVALID_CREDENTIALS`로 응답한다.
- 이메일은 VS-001과 동일하게 앞뒤 공백 제거·소문자 변환 후 조회한다.
- 현재 상태 모델에 `SUSPENDED`를 추가하고 해당 계정의 로그인을 거부한다.

## 4. API 계약

### 요청

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "member@example.com",
  "password": "password123"
}
```

### 성공

- 상태: `200 OK`
- `Set-Cookie`로 Refresh Token 전달

```json
{
  "accessToken": "JWT",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "userId": "내부 사용자 ID",
  "role": "USER"
}
```

### 실패

- 요청 필드 누락·형식 오류: `400 VALIDATION_ERROR`
- 이메일·비밀번호 불일치 또는 비활성 상태: `401 INVALID_CREDENTIALS`
- 오류 응답은 `code`, `message`, `requestId`, `fieldErrors` 형식을 유지한다.

## 5. 데이터 변경

- `users.status` 제약에 `SUSPENDED`를 추가한다.
- `auth_sessions`: id, userId, refreshTokenHash, expiresAt, createdAt, revokedAt
- Refresh Token 해시는 유일해야 하고 세션은 사용자와 외래 키로 연결한다.
- 로그인 세션 저장 실패 시 토큰을 성공 응답으로 반환하지 않는다.

## 6. 승인할 테스트 목록

### 성공·토큰

- [ ] 정규화된 이메일과 올바른 비밀번호로 로그인하면 `200 OK`다.
- [ ] Access Token은 유효한 서명과 15분 만료를 가진다.
- [ ] JWT에는 사용자 ID·역할·발급·만료·JTI만 있고 이메일·프로필 아이디가 없다.
- [ ] 응답은 Bearer 타입과 900초 만료를 반환한다.
- [ ] Refresh Token Cookie는 HttpOnly, Path, SameSite, 환경별 Secure 정책을 지킨다.
- [ ] DB에는 Refresh Token 원문이 아닌 SHA-256 해시와 14일 만료 세션만 저장된다.
- [ ] 동일 사용자의 연속 로그인은 서로 다른 토큰·세션을 생성한다.

### 실패·보안

- [ ] 이메일 또는 비밀번호 누락은 `400 VALIDATION_ERROR`다.
- [ ] 존재하지 않는 이메일은 `401 INVALID_CREDENTIALS`다.
- [ ] 틀린 비밀번호는 같은 상태·코드·메시지를 반환한다.
- [ ] `SUSPENDED` 계정도 같은 `401 INVALID_CREDENTIALS`를 반환한다.
- [ ] 실패 응답에서 계정 존재 여부를 구분할 수 없다.
- [ ] 요청·응답·로그·DB에 비밀번호와 Refresh Token 원문을 노출하지 않는다.
- [ ] 서명 키가 없거나 32바이트 미만이면 애플리케이션 시작이 실패한다.

### 트랜잭션·문서

- [ ] 인증 세션 저장 실패 시 `500`이며 Access/Refresh Token을 반환하지 않는다.
- [ ] PostgreSQL Testcontainers에서 Migration, 유일 제약, 외래 키를 검증한다.
- [ ] OpenAPI에 로그인 요청·성공·400·401 계약과 Cookie 설명을 반영한다.
- [ ] ERD에 User와 AuthSession 관계를 반영한다.
- [ ] 기존 VS-001 가입 테스트가 회귀하지 않는다.

## 7. 후속 보안 과제

- VS-003에서 Refresh Token 회전·재사용 탐지·만료 처리를 구현한다.
- VS-004에서 현재/전체 세션 폐기와 Cookie 삭제를 구현한다.
- OPS-02에서 CORS·CSRF·보안 Header·분산 로그인 Rate Limit을 확정한다.

## 8. 승인 명령

위 범위와 JWT·Refresh Token 정책 및 테스트 목록을 승인하려면 정확히 입력한다.

`VS-002 테스트 승인`

승인 전에는 실패 테스트와 구현 코드를 작성하지 않는다.
