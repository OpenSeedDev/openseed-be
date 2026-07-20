# VS-004 로그아웃·인증 무효화

## 1. 사용자 결과

로그인 사용자는 현재 로그인 세션만 종료하거나 자신의 모든 로그인 세션을 종료할 수 있다. 폐기된 세션에 연결된 Refresh Token과 Access Token은 로그아웃 직후부터 더 이상 인증에 사용할 수 없다.

## 2. 근거와 선행 조건

- 백로그: VS-004 P0, 로그아웃·인증 무효화, 선행 VS-003
- 기능 명세: 로그인·인증 갱신·로그아웃은 MVP 범위이며 토큰 원문과 개인정보를 응답·로그에 노출하지 않는다.
- VS-003: Refresh Token 회전, 패밀리, 재사용 탐지와 PostgreSQL 행 잠금이 구현되어 있다.
- VS-003 Merge 커밋과 상태 복구, Pre-merge Guard 적용이 완료됐다.

## 3. 범위

### 포함

- `POST /api/v1/auth/logout`: 현재 로그인 세션 패밀리 폐기
- `POST /api/v1/auth/logout-all`: 해당 사용자의 모든 로그인 세션 폐기
- 두 API의 Refresh Cookie 삭제
- Access Token에 인증 세션 ID인 `sid` 클레임 추가
- 서명·만료·세션 상태를 확인하는 Access Token 인증 컴포넌트
- 폐기된 세션에 연결된 Access Token의 즉시 거부
- 로그인·갱신·로그아웃 사이의 사용자 단위 동시성 정합성
- OpenAPI·ERD·Flyway Migration 갱신

### 제외

- 로그인 기기·세션 목록 조회와 개별 기기 원격 로그아웃
- Access Token `jti`별 블랙리스트
- 비밀번호 변경·이메일 변경·회원 탈퇴
- 로그아웃 알림, 감사 로그 화면, 관리자 강제 로그아웃
- CSRF·CORS·분산 Rate Limit·보안 Header
- VS-005의 내 계정 조회 API와 이후 업무 API

## 4. 승인 시 확정할 정책

### 4.1 현재 세션 로그아웃

- 요청 본문 없이 `refresh_token` HttpOnly Cookie를 사용한다.
- Cookie가 가리키는 한 행만이 아니라 동일한 `family_id` 전체를 현재 로그인 세션으로 본다.
- 패밀리의 활성 세션을 `LOGOUT` 사유로 폐기한다.
- Cookie 누락·임의 값·이미 폐기된 토큰에도 `204 No Content`를 반환해 토큰 존재 여부를 노출하지 않는다.
- 성공·반복·무효 요청 모두 `refresh_token` Cookie를 `Max-Age=0`으로 삭제한다.

### 4.2 모든 세션 로그아웃

- `Authorization: Bearer <access-token>`을 필수로 받는다.
- Access Token의 서명, 만료, `sub`, `sid`와 해당 세션의 활성 상태·소유자를 검증한다.
- 인증된 사용자의 모든 활성 `auth_sessions`를 `LOGOUT_ALL` 사유로 한 트랜잭션에서 폐기한다.
- 요청에 Refresh Cookie가 없어도 모든 세션 폐기는 수행하며 응답에는 항상 삭제 Cookie를 보낸다.
- Bearer Token 누락·위조·만료·`sid` 누락·폐기 세션은 `401 INVALID_ACCESS_TOKEN`으로 통일한다.

### 4.3 Access Token과 세션 연결

- 로그인과 갱신에서 발급하는 Access Token에 민감정보가 아닌 `sid`를 추가한다.
- Access Token 인증 시 JWT 서명과 만료만 확인하지 않고 `sid`의 활성 세션도 확인한다.
- Refresh Token 회전 후에는 이전 세션의 Access Token도 즉시 무효가 되고 새 세션의 Access Token만 유효하다.
- VS-004 배포 전에 발급되어 `sid`가 없는 Access Token은 거부한다. 유효한 Refresh Token으로 갱신하면 새 형식의 Access Token을 받을 수 있다.
- 이메일·프로필 아이디·Refresh Token과 그 해시는 JWT, 응답 본문, 일반 로그에 넣지 않는다.

### 4.4 동시성·트랜잭션

- 로그인, 갱신, 현재 세션 로그아웃, 전체 세션 로그아웃의 세션 변경은 사용자 행 잠금을 공통 직렬화 기준으로 사용한다.
- Refresh Token으로 사용자를 찾은 뒤 사용자 행을 잠그고 세션 상태를 다시 확인하여 TOCTOU를 방지한다.
- 전체 로그아웃과 동시에 진행된 기존 로그인·갱신은 잠금 순서에 따라 먼저 완료된 세션까지 폐기한다. 전체 로그아웃 완료 후 새로 시작한 로그인은 새 세션으로 인정한다.
- 폐기 도중 DB 오류가 발생하면 전체 변경을 Rollback한다.

## 5. API 계약 초안

### 현재 세션 로그아웃

```http
POST /api/v1/auth/logout
Cookie: refresh_token=...
```

응답:

- `204 No Content`
- `Set-Cookie: refresh_token=; Path=/api/v1/auth; Max-Age=0; HttpOnly; SameSite=Lax`
- 응답 본문 없음

### 모든 세션 로그아웃

```http
POST /api/v1/auth/logout-all
Authorization: Bearer <access-token>
Cookie: refresh_token=...   # 선택
```

응답:

- 성공: `204 No Content`와 Refresh Cookie 삭제
- 인증 실패: `401 INVALID_ACCESS_TOKEN`과 Refresh Cookie 삭제

## 6. 데이터·구현 예상 변경

- 새 테이블이나 컬럼은 추가하지 않는다.
- `V5__add_logout_revocation_reasons.sql`에서 `auth_sessions.revocation_reason` 제약에 `LOGOUT`, `LOGOUT_ALL`을 추가한다.
- `AuthSession`에 ID·사용자 ID 확인과 로그아웃 폐기 동작을 추가한다.
- 세션 Repository에 토큰 조회, 사용자별 활성 세션 폐기, 사용자 잠금에 필요한 쿼리를 추가한다.
- `TokenIssuer`에 `sid` 발급 및 Access Token 검증을 추가한다.
- 로그인·갱신 발급 흐름을 세션 ID가 포함된 Access Token 계약으로 변경한다.
- 로그아웃 Controller·Service와 `INVALID_ACCESS_TOKEN` 오류 처리를 추가한다.
- OpenAPI에 Bearer 보안 스키마, 두 로그아웃 API, 204·401·Cookie 계약을 반영한다.
- ERD에는 새 컬럼 없이 세션 기반 Access Token 무효화와 폐기 사유를 갱신한다.

## 7. 인수 조건

- 현재 세션 로그아웃은 해당 토큰 패밀리만 폐기하고 다른 로그인 세션은 유지한다.
- 전체 세션 로그아웃은 요청 사용자에게 속한 모든 활성 세션을 폐기한다.
- 로그아웃된 세션의 Refresh Token과 Access Token은 즉시 사용할 수 없다.
- 현재 세션 로그아웃은 토큰 존재 여부와 상태를 응답으로 구분하지 않는 멱등 동작이다.
- 전체 세션 로그아웃은 유효한 세션 기반 Bearer Token을 요구한다.
- 모든 응답에서 Refresh Token 원문과 해시가 노출되지 않는다.
- 동시 갱신·로그아웃에도 로그아웃 대상 세션이 활성 상태로 남지 않는다.

## 8. 승인할 테스트 목록

### 정상·멱등 동작

- [ ] 유효한 Refresh Cookie로 현재 로그아웃하면 `204`와 삭제 Cookie를 반환한다.
- [ ] 현재 로그아웃은 해당 family의 활성 세션을 `LOGOUT`으로 폐기한다.
- [ ] 같은 사용자의 별도 로그인 세션은 현재 로그아웃 후에도 유지된다.
- [ ] Cookie 누락·임의 토큰·이미 로그아웃된 토큰을 반복 제출해도 모두 `204`와 삭제 Cookie를 반환한다.
- [ ] 응답 본문·Header·일반 로그에 Refresh Token 원문이나 해시가 노출되지 않는다.

### 전체 세션 로그아웃·권한

- [ ] 유효한 Bearer Token으로 전체 로그아웃하면 `204`와 삭제 Cookie를 반환한다.
- [ ] 여러 기기 역할의 세션이 모두 `LOGOUT_ALL`로 폐기된다.
- [ ] 요청 사용자와 무관한 다른 사용자의 세션은 폐기하지 않는다.
- [ ] Bearer Token 누락·위조·만료·`sid` 누락은 `401 INVALID_ACCESS_TOKEN`이다.
- [ ] 이미 폐기된 세션의 Bearer Token은 `401 INVALID_ACCESS_TOKEN`이다.
- [ ] 인증 실패 응답도 Refresh Cookie를 삭제하고 민감정보를 노출하지 않는다.

### Access Token 즉시 무효화

- [ ] 로그인 Access Token은 서명·기존 승인 클레임·15분 만료와 올바른 `sid`를 가진다.
- [ ] 갱신 Access Token의 `sid`는 새로 회전된 세션 ID와 일치한다.
- [ ] 갱신 후 이전 세션의 Access Token은 거부되고 새 Access Token은 허용된다.
- [ ] 현재 로그아웃 후 해당 세션의 Access Token으로 보호된 로그아웃 API에 접근할 수 없다.
- [ ] 전체 로그아웃 후 모든 기존 Access Token과 Refresh Token을 사용할 수 없다.
- [ ] Access Token에는 이메일·프로필 아이디·Refresh Token 정보가 없다.

### 동시성·트랜잭션

- [ ] 동일 세션의 갱신과 현재 로그아웃이 동시에 실행돼도 완료 후 활성 family 세션이 없다.
- [ ] 전체 로그아웃과 기존 세션 갱신이 동시에 실행돼도 로그아웃 대상 활성 세션이 남지 않는다.
- [ ] 전체 로그아웃과 기존 로그인 완료가 경합할 때 사용자 잠금 순서에 따른 결과가 일관된다.
- [ ] 현재 세션 폐기 실패 시 세션 상태가 Rollback되고 `500`을 반환한다.
- [ ] 전체 세션 폐기 중 실패하면 일부 세션만 폐기되지 않고 전체 Rollback된다.
- [ ] PostgreSQL Testcontainers에서 사용자·세션 잠금, 제약과 V5 Migration을 검증한다.

### 회귀·문서

- [ ] VS-001 가입, VS-002 로그인, VS-003 Refresh Token 회전·재사용 테스트가 회귀하지 않는다.
- [ ] OpenAPI에 `/logout`, `/logout-all`, Bearer 인증, 204·401·Cookie 삭제 계약이 노출된다.
- [ ] ERD에 세션 기반 Access Token 무효화와 `LOGOUT`, `LOGOUT_ALL` 사유를 반영한다.
- [ ] `./gradlew clean test` 전체 테스트가 통과한다.

## 9. 미결정 사항

없음. 위 두 API, 세션 연결 Access Token, 사용자 단위 잠금 정책을 테스트 승인으로 확정한다.

## 10. 승인 명령

위 범위와 테스트 목록을 승인하려면 정확히 입력한다.

`VS-004 테스트 승인`

승인 전에는 테스트와 구현 코드를 작성하지 않는다.
