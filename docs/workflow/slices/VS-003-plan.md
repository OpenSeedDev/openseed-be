# VS-003 인증 갱신·회전·재사용 방지

## 사용자 결과

로그인 사용자는 유효한 Refresh Token으로 새 Access Token과 Refresh Token을 발급받는다. 이미 사용되거나 탈취된 Refresh Token이 다시 제출되면 관련 토큰 패밀리를 폐기한다.

## 범위

### 포함

- `POST /api/v1/auth/refresh`
- HttpOnly Cookie의 Refresh Token 검증
- Refresh Token 1회용 회전
- 새 15분 Access Token과 새 14일 Refresh Token 발급
- 토큰 패밀리 기반 재사용 탐지·폐기
- 동시 갱신 직렬화
- OpenAPI·ERD·Flyway 갱신

### 제외

- 명시적 로그아웃·전체 세션 폐기: VS-004
- Access Token 인증 필터와 보호 API
- 기기 목록·세션 관리 화면
- CSRF·분산 Rate Limit·보안 Header: OPS-02

## 승인 시 확정할 정책

- 요청 본문 없이 `refresh_token` Cookie만 받는다.
- DB에는 원문이 아닌 SHA-256 해시만 사용한다.
- 세션 행을 비관적 잠금으로 조회해 같은 토큰의 동시 갱신을 직렬화한다.
- 정상 갱신은 기존 세션을 `ROTATED`로 폐기하고 같은 패밀리의 새 세션을 만든다.
- 새 Refresh Token은 갱신 시점부터 14일인 sliding expiration을 사용한다.
- 이미 회전·폐기된 토큰이 다시 제출되면 `REUSE_DETECTED`로 판단하고 같은 패밀리의 활성 세션을 모두 폐기한다.
- 재사용 탐지 응답과 만료·위조·누락 응답은 모두 `401 INVALID_REFRESH_TOKEN`으로 통일한다.
- 재사용 또는 무효 토큰 응답에서는 Refresh Cookie를 즉시 삭제한다.
- 동일 토큰 동시 요청은 하나만 성공한다. 뒤 요청은 재사용으로 판단해 새 패밀리 세션도 폐기하므로 첫 응답의 Refresh Token 역시 이후 사용할 수 없다.
- 정지된 사용자는 갱신할 수 없고 해당 패밀리를 폐기한다.
- Refresh Token과 해시는 응답 본문·로그에 노출하지 않는다.

## API 계약

```http
POST /api/v1/auth/refresh
Cookie: refresh_token=...
```

성공 `200 OK`:

```json
{
  "accessToken": "JWT",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

- 새 Refresh Token은 `Set-Cookie`로만 전달한다.
- 무효·만료·재사용·정지 계정은 `401 INVALID_REFRESH_TOKEN`과 삭제 Cookie를 반환한다.

## 데이터 변경

- `auth_sessions.family_id`: 최초 로그인 세션부터 이어지는 토큰 패밀리
- `auth_sessions.rotated_from_id`: 직전 세션 ID
- `auth_sessions.revocation_reason`: `ROTATED`, `REUSE_DETECTED`, `USER_SUSPENDED`
- 기존 VS-002 세션은 `family_id=id`로 이관한다.
- 패밀리 활성 세션 조회 인덱스를 추가한다.

## 승인할 테스트 목록

### 정상 갱신

- [ ] 유효한 Cookie로 `200`과 새 Access/Refresh Token을 받는다.
- [ ] 새 Access Token은 올바른 서명·클레임·15분 만료를 가진다.
- [ ] 기존 Refresh 세션은 `ROTATED`, 새 세션은 같은 family ID로 저장된다.
- [ ] 새 Refresh Token 원문은 DB·응답 본문·로그에 없다.
- [ ] 새 세션 만료는 갱신 시점부터 14일이다.
- [ ] 회전을 연속 수행할 때 매번 서로 다른 토큰이 발급된다.

### 실패·재사용

- [ ] Cookie 누락·임의 토큰·만료 토큰은 동일한 `401 INVALID_REFRESH_TOKEN`이다.
- [ ] 이미 회전된 토큰 재사용 시 같은 패밀리의 새 활성 세션도 폐기된다.
- [ ] 재사용 탐지 후 최신 Refresh Token으로도 갱신할 수 없다.
- [ ] 정지 사용자는 갱신할 수 없고 패밀리가 폐기된다.
- [ ] 실패 응답은 Refresh Cookie를 `Max-Age=0`으로 삭제한다.

### 동시성·트랜잭션

- [ ] 동일 Refresh Token 동시 요청 두 건 중 하나만 성공한다.
- [ ] 뒤 요청의 재사용 탐지로 패밀리가 폐기된다.
- [ ] 새 세션 저장 실패 시 기존 세션 회전도 롤백된다.
- [ ] PostgreSQL Testcontainers에서 잠금·제약·Migration을 검증한다.

### 회귀·문서

- [ ] VS-001 가입과 VS-002 로그인 테스트가 회귀하지 않는다.
- [ ] OpenAPI에 성공·401·Cookie 회전 계약을 반영한다.
- [ ] ERD에 토큰 패밀리와 회전 관계를 반영한다.

## 워크플로우 재발 방지

- PR 생성 후 상태 기록 커밋과 최신 Head CI가 완료되기 전에는 Merge를 요청하지 않는다.
- 리뷰 확인 후 반드시 `AWAITING_USER_MERGE` 상태를 Push한 다음 사용자에게 Merge를 요청한다.
- 이 절차를 자동 검사하는 pre-merge guard는 별도 워크플로우 개선 항목으로 유지한다.

## 승인 명령

위 범위와 회전·재사용·동시성 정책을 승인하려면 정확히 입력한다.

`VS-003 테스트 승인`

승인 전에는 테스트와 구현 코드를 작성하지 않는다.
