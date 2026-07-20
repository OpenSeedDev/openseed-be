# SeedRank ERD

현재 구현된 데이터 모델을 수직 슬라이스 단위로 갱신한다.

```mermaid
erDiagram
    USERS ||--|| POINT_WALLETS : owns
    USERS ||--o{ POINT_LEDGERS : receives
    USERS ||--o{ AUTH_SESSIONS : authenticates

    USERS {
        uuid id PK
        varchar email UK "normalized, max 254"
        varchar password_hash "BCrypt"
        varchar profile_id "not unique"
        varchar role "USER"
        varchar status "ACTIVE"
        timestamptz created_at
        timestamptz updated_at
    }

    POINT_WALLETS {
        uuid id PK
        uuid user_id FK,UK
        int balance "0..2000"
        int pending_recovery_balance "0 or greater"
        timestamptz updated_at
    }

    POINT_LEDGERS {
        uuid id PK
        uuid user_id FK
        varchar type "CREDIT"
        int original_amount
        int paid_amount
        int expired_amount
        int balance_after "0..2000"
        varchar source_type "SIGNUP_BONUS"
        uuid source_id
        timestamptz created_at
    }

    AUTH_SESSIONS {
        uuid id PK
        uuid user_id FK
        varchar refresh_token_hash UK
        timestamptz expires_at
        timestamptz created_at
        timestamptz revoked_at
        uuid family_id
        uuid rotated_from_id FK
        varchar revocation_reason "ROTATED, REUSE_DETECTED, USER_SUSPENDED, LOGOUT, LOGOUT_ALL"
    }
```

## VS-001 제약

- `users.email`만 유일하며 공개 프로필 아이디는 중복을 허용한다.
- 사용자당 Point 지갑은 하나만 생성한다.
- 가입 시 User, PointWallet, PointLedger를 같은 트랜잭션에 저장한다.
- 가입 원장은 `300 = paidAmount(300) + expiredAmount(0)`을 만족한다.
- PointLedger는 append-only 데이터로 취급한다.
- 로그인 Refresh Token은 원문이 아닌 SHA-256 해시로만 AuthSession에 저장한다.
- Refresh Token은 family ID와 이전 세션 ID로 회전 계보를 보존하며 재사용 탐지 시 패밀리를 폐기한다.
- Access Token의 `sid` 클레임은 `auth_sessions.id`를 가리키며, 서명·만료와 해당 세션 활성 상태를 함께 검증한다.
- 현재 로그아웃은 세션 family를 `LOGOUT`으로, 전체 로그아웃은 사용자의 모든 활성 세션을 `LOGOUT_ALL`로 폐기한다.
- 로그인·갱신·로그아웃의 세션 변경은 사용자 행 잠금 후 수행해 동시 요청을 직렬화한다.
