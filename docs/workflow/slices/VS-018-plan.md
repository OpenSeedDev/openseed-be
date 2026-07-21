# VS-018 AI Job 생성·중복 방지

## 사용자 결과

로그인 사용자는 사업화 키워드와 문제의식을 비동기 AI 생성 Job으로 접수하고 즉시 `jobId`를 받는다. 같은 요청을 재전송하거나 동시에 보내도 Job은 하나만 생성된다.

## 근거와 선행 조건

- 백로그: VS-018, 선행 VS-009, resource lock `ai-job`
- 선행 PR #29가 2026-07-21에 Merge됐고 Merge 커밋 `087d640d87b5283e7ae952e7ad00bc63a5091344`이 현재 기준 커밋이다.
- 수직 슬라이스 기획은 사용자 입력과 Prompt Version의 스냅샷, `202 Accepted`, `jobId`, 동일 요청의 중복 방지를 요구한다.
- 통합 기능 명세의 입력은 사업화 키워드와 문제의식이며 AI 결과를 비동기 Job으로 생성한다.

## 범위

### 포함

- `POST /api/v1/ai/idea-jobs`
- `keyword`, `background` 입력과 서버 관리 Prompt Version의 불변 스냅샷
- 최초 생성과 같은 `Idempotency-Key` 재전송 모두 `202 Accepted`와 같은 `jobId` 반환
- 사용자별 Idempotency Key 고유 제약으로 동시 중복 생성 방지
- `PENDING`, 재시도 횟수 0, 생성·수정 시각 저장
- Bearer Access Token과 활성 세션 기반 인증
- PostgreSQL Migration, JPA 매핑, OpenAPI와 ERD 갱신

### 제외

- Worker 선점, Lease, Backoff와 재시도 실행(VS-019)
- AI Provider 호출, 구조화 후보 5개와 결과 저장(VS-020)
- Job 상태·결과 조회와 소유자 권한(VS-021)
- 후보 선택·편집과 Idea Draft 전환(VS-022)
- 최종 실패와 수동 작성 fallback(VS-023)

## API와 데이터 계약

- `keyword`는 필수 1~200자, `background`는 필수 1~2,000자이며 앞뒤 공백을 제거해 스냅샷한다.
- `Idempotency-Key`는 필수 1~100자이고 앞뒤 공백이나 제어 문자를 허용하지 않는다.
- Prompt Version은 클라이언트가 선택하지 않고 서버 설정 `idea-candidates-v1`을 저장한다.
- 최초 요청과 같은 사용자의 같은 Key·같은 입력 재전송은 같은 `jobId`를 반환한다.
- 같은 사용자의 같은 Key를 다른 입력에 재사용하면 `409 IDEMPOTENCY_KEY_REUSED`다.
- 다른 사용자는 같은 Key를 독립적으로 사용할 수 있다.
- 응답은 `jobId`만 포함하며 입력 원문, Prompt Version, 내부 사용자·세션 정보는 노출하지 않는다.

## 인수 조건과 테스트 목록

- 유효한 사용자가 요청하면 `PENDING` Job을 한 건 저장하고 `202 Accepted`, `Location`, `jobId`를 반환한다.
- DB에 정규화된 입력 JSON 스냅샷, 서버 Prompt Version, retry count 0, UTC 저장 시각이 남는다.
- 순차 재전송과 동시 재전송은 같은 `jobId`에 수렴하고 DB 행은 하나다.
- 사용자별 Key이므로 다른 사용자의 같은 Key는 별도 Job을 만든다.
- 같은 Key를 다른 입력으로 재사용하면 기존 Job을 변경하지 않고 `409`다.
- 필수 입력·길이·Key 형식 오류는 `400 VALIDATION_ERROR`다.
- 인증 누락·위조·폐기 세션은 `401 INVALID_ACCESS_TOKEN`이다.
- 응답과 일반 오류에 AI 입력 원문이나 인증 정보가 없다.
- OpenAPI에 Bearer 인증, `Idempotency-Key`, 202/400/401/409 계약이 노출된다.
- PostgreSQL Testcontainers에서 새 Migration과 `ddl-auto: validate`가 통과한다.
- 집중 테스트와 `./gradlew clean test` 전체 회귀 테스트가 통과한다.

## 미결정 사항

없음. Job 조회, Worker 처리 상태 전이와 Provider 결과 계약은 명시된 후속 슬라이스에서 정한다.
