# VS-055 공개 프로필 아이디 수정

## 사용자 결과

로그인 사용자는 공개 프로필 아이디를 중복 여부나 변경 횟수에 제한받지 않고 수정할 수 있다. 기존 게시물과 기여의 소유 관계는 변경 가능한 프로필 아이디가 아니라 내부 사용자 UUID로 유지된다.

## 근거와 선행 조건

- 기능 명세 AUTH-02, AUTH-06, AUTH-07 및 MY-01
- 기획 정책: 영문자·숫자·밑줄 3~20자, 예약어 금지, MVP 중복 허용, 변경 횟수 제한 없음
- 백로그: VS-055 P1, 선행 VS-001
- GitHub PR #15에서 VS-001이 `e0ee1fb88d290e80de665feee7e7d614210d93ca`로 Merge된 것을 확인했다.

## 범위

### 포함

- `PATCH /api/v1/me/profile-id`
- 유효한 Bearer Access Token과 활성 세션·사용자 검증
- 프로필 아이디 형식 및 예약어 검증
- 중복 프로필 아이디 허용
- 같은 사용자의 횟수 제한 없는 반복 수정
- 내부 사용자 UUID를 유지한 채 현재 프로필 아이디와 `updatedAt` 갱신
- OpenAPI 계약과 인증·정상·Validation 통합 테스트

### 제외

- 프로필 아이디 중복 금지와 변경 주기·횟수 제한
- 프로필 이미지와 자기소개
- 이메일·비밀번호 변경과 회원 탈퇴
- 다른 사용자의 공개 프로필 조회
- 과거 프로필 아이디 이력

## API 계약

```http
PATCH /api/v1/me/profile-id
Authorization: Bearer <access-token>
Content-Type: application/json

{"profileId":"new_seed_id"}
```

- 성공: `200 OK`, `userId`, `profileId`, `updatedAt`
- 인증 실패: `401 INVALID_ACCESS_TOKEN`
- 형식·예약어 위반: `400 INVALID_PROFILE_ID`, `fieldErrors[].field=profileId`
- 동일한 프로필 아이디를 다른 사용자가 사용해도 성공한다.

## 테스트 목록

- 유효한 요청은 현재 사용자의 프로필 아이디를 수정하고 내부 UUID를 유지한다.
- 수정 직후 내 계정 조회가 새 프로필 아이디를 반환한다.
- 다른 사용자가 이미 사용 중인 프로필 아이디로도 수정할 수 있다.
- 한 사용자가 여러 번 연속 수정할 수 있다.
- 영문자·숫자·밑줄 3~20자만 허용한다.
- 예약어는 대소문자와 무관하게 거부한다.
- 누락·잘못된 Bearer Token과 폐기된 세션은 `401`로 거부한다.
- 다른 사용자의 프로필 아이디와 내부 UUID는 변경하지 않는다.
- 응답에 이메일·비밀번호·토큰·세션 ID를 포함하지 않는다.
- OpenAPI에 PATCH 경로, Bearer 인증, 200·400·401 응답이 노출된다.
- 기존 Migration과 `ddl-auto: validate` 및 전체 회귀 테스트가 통과한다.

## 데이터 영향

기존 `users.profile_id`, `users.updated_at` 컬럼을 사용하므로 Migration과 ERD 구조 변경은 없다.

