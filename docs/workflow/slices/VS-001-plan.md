# VS-001 회원가입과 최초 300P 지급

## 1. 사용자 결과

일반 사용자가 이메일, 비밀번호, 공개 프로필 아이디를 제출하면 이메일 인증 없이 즉시 활성 계정이 생성되고, 바로 사용할 수 있는 300P가 지급된다.

## 2. 범위

### 포함

- `POST /api/v1/auth/signup`
- 로그인 이메일 중복 방지
- 비밀번호 단방향 해시 저장
- 공개 프로필 아이디 형식과 예약어 검증
- 중복 공개 프로필 아이디 허용
- `User`, `PointWallet`, 가입 보너스 `PointLedger`의 원자적 생성
- 동일 이메일 동시 가입 제어
- 공통 오류 응답, OpenAPI, ERD, Flyway Migration 갱신

### 제외

- 일반 사용자 이메일 인증
- 로그인과 토큰 발급
- 회사 가입 및 회사 이메일 인증
- 프로필 아이디 수정
- 비밀번호 변경·재설정, 이메일 변경, 회원 탈퇴
- 프로필 이미지와 자기소개

## 3. 승인 시 확정할 세부 정책

- 이메일은 앞뒤 공백을 제거하고 `Locale.ROOT` 기준 소문자로 정규화해 저장·비교한다.
- 이메일은 유효한 형식이며 최대 254자여야 한다.
- 비밀번호는 공백을 자동 제거하지 않는다. 8자 이상이며 UTF-8 기준 72바이트 이하여야 한다.
- 비밀번호 조합 규칙은 두지 않아 가입 장벽을 낮춘다.
- 비밀번호 해시는 전체 보안 필터를 먼저 도입하지 않고 `spring-security-crypto`의 BCrypt를 사용한다.
- 공개 프로필 아이디는 `^[A-Za-z0-9_]{3,20}$`를 만족해야 한다.
- 예약어는 대소문자 구분 없이 `admin`, `administrator`, `root`, `system`, `support`, `seedrank`, `openseed`를 금지한다.
- 동일한 공개 프로필 아이디는 서로 다른 사용자가 사용할 수 있다.
- 가입 재전송 전용 키는 MVP에 추가하지 않는다. 정규화 이메일의 DB 유일 제약으로 한 요청만 성공시킨다.

## 4. API 계약

### 요청

```http
POST /api/v1/auth/signup
Content-Type: application/json
```

```json
{
  "email": "member@example.com",
  "password": "password123",
  "profileId": "open_seed"
}
```

### 성공

- 상태: `201 Created`
- 이메일, 비밀번호, 비밀번호 해시, 인증 토큰은 반환하지 않는다.

```json
{
  "userId": "내부 사용자 ID",
  "profileId": "open_seed",
  "status": "ACTIVE",
  "pointBalance": 300,
  "createdAt": "2026-07-20T09:00:00Z"
}
```

### 실패

- 유효하지 않은 입력: `400 Bad Request`, `VALIDATION_ERROR`
- 이미 존재하는 정규화 이메일: `409 Conflict`, `EMAIL_ALREADY_EXISTS`
- 프로필 아이디 형식 또는 예약어 위반: `400 Bad Request`, `INVALID_PROFILE_ID`
- 예상하지 못한 오류: `500 Internal Server Error`, 민감정보를 제외한 공통 오류 응답

```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "이미 가입된 이메일입니다.",
  "requestId": "요청 추적 ID",
  "fieldErrors": []
}
```

## 5. 저장 결과와 불변식

- `User`: 정규화 이메일, BCrypt 해시, 프로필 아이디, `USER` 역할, `ACTIVE` 상태를 저장한다.
- `PointWallet`: 사용자당 하나만 존재하며 `balance=300`, `pendingRecoveryBalance=0`으로 생성한다.
- `PointLedger`: `type=CREDIT`, `sourceType=SIGNUP_BONUS`, `originalAmount=300`, `paidAmount=300`, `expiredAmount=0`, `balanceAfter=300`으로 한 건 생성한다.
- 가입 보너스는 일일 활동 보상 한도에 포함하지 않는다.
- 세 엔티티 중 하나라도 저장되지 않으면 모두 롤백한다.
- 정규화 이메일에는 DB 유일 제약을 둔다. 애플리케이션 사전 검사와 무관하게 동시 가입 중 하나만 커밋된다.
- 원장 행은 생성 후 수정·삭제하지 않는 append-only 데이터로 취급한다.
- API 응답과 애플리케이션 로그에 비밀번호와 해시를 남기지 않는다.

## 6. 승인할 테스트 목록

### 정상·경계

- [ ] 유효한 요청은 `201 Created`를 반환한다.
- [ ] 가입 직후 사용자는 `USER`, `ACTIVE` 상태다.
- [ ] 이메일은 앞뒤 공백 제거와 소문자 변환 후 저장된다.
- [ ] 비밀번호 원문과 다른 BCrypt 해시만 저장되고 해시 검증은 성공한다.
- [ ] 지갑은 사용 가능 잔액 300P와 회수 대기 잔액 0P로 생성된다.
- [ ] 가입 보너스 원장 한 건의 금액·잔액·출처가 정책과 일치한다.
- [ ] 3자 및 20자 프로필 아이디를 허용한다.
- [ ] 동일 프로필 아이디로 서로 다른 이메일 두 개의 가입이 모두 성공한다.

### 입력 실패

- [ ] 이메일 누락·형식 오류·254자 초과는 `400 VALIDATION_ERROR`다.
- [ ] 비밀번호 누락·8자 미만·UTF-8 72바이트 초과는 `400 VALIDATION_ERROR`다.
- [ ] 프로필 아이디 누락, 3자 미만, 20자 초과, 허용하지 않는 문자는 `400 INVALID_PROFILE_ID`다.
- [ ] 대소문자를 달리한 예약어도 `400 INVALID_PROFILE_ID`다.

### 중복·동시성·트랜잭션

- [ ] 대소문자 또는 앞뒤 공백만 다른 기존 이메일은 `409 EMAIL_ALREADY_EXISTS`다.
- [ ] 같은 이메일의 동시 가입 두 건은 정확히 한 건만 성공하고 User·Wallet·Ledger가 각각 한 건만 남는다.
- [ ] Wallet 저장 실패 시 User와 Ledger도 남지 않는다.
- [ ] Ledger 저장 실패 시 User와 Wallet도 남지 않는다.

### 계약·보안·문서

- [ ] 성공 응답에 이메일·비밀번호·해시·토큰이 없다.
- [ ] 실패 응답은 `code`, `message`, `requestId`, `fieldErrors` 형식을 지킨다.
- [ ] 요청·오류 로그에 비밀번호와 해시가 기록되지 않는다.
- [ ] PostgreSQL Testcontainers에서 Migration과 저장 제약이 검증된다.
- [ ] OpenAPI에 요청·성공·주요 실패 계약이 반영된다.
- [ ] ERD에 User, PointWallet, PointLedger의 관계와 유일 제약이 반영된다.

## 7. 구현 후 갱신 대상

- Flyway: User, PointWallet, PointLedger 테이블과 인덱스·제약
- OpenAPI: 회원가입 요청·응답·오류 예시
- ERD: 세 엔티티와 관계
- 기능 명세 추적: `AUTH-01`, `AUTH-02`, `AUTH-03`, `AUTH-04`, `AUTH-05`, `AUTH-08`, `AUTH-09`

## 8. 승인 명령

위 기능 범위, 세부 정책, API 계약과 테스트 목록을 승인할 때 정확히 다음과 같이 입력한다.

`VS-001 테스트 승인`

승인 전에는 실패 테스트와 구현 코드를 작성하지 않는다.
