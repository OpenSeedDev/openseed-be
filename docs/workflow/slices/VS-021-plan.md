# VS-021 소유자용 AI Job 상태와 결과

## 시스템 결과

AI Job 소유자가 비동기 처리 상태를 확인하고, 성공 시 정규화된 후보 결과를, 실패 시 이해 가능한 실패 코드를 조회한다.

## 전달 방식과 선행 조건

- Fast Build Stacked PR: `codex/vs-020-ai-candidates` 기반, parent VS-020 PR #62, stack root VS-018, depth 4
- parent Head: `b645d129a41be4d80e6a2e9d035f52dbb6541e64`
- VS-020은 아직 Merge되지 않았으며 parent 변경 시 이 슬라이스를 다시 검증한다.
- Merge 순서: VS-018 → VS-019 → VS-020 → VS-021

## 포함 범위

- `GET /api/v1/ai/idea-jobs/{jobId}` 소유자 전용 조회
- 사용자 상태 `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED` 제공
- 내부 `RETRY_WAIT`는 재처리 대기 중인 `PENDING`으로 매핑
- 성공 시 문제 분석과 후보 5개의 정규화 결과 반환
- 실패 시 실패 코드 반환
- 존재하지 않거나 다른 소유자의 Job은 동일한 `404` 반환
- OpenAPI 계약 갱신

## 제외 범위

- Provider 원본 응답, 입력 스냅샷, Prompt Version, retry count, Lease 정보 노출
- 후보 선택·편집과 Idea Draft 전환: VS-022
- 재시도 종료 정책·수동 작성 양식과 사용자용 실패 문구: VS-023
- 목록·검색·운영자 조회 API

## API·데이터 영향

- 조회 API 하나를 추가하며 기존 테이블과 Migration은 변경하지 않는다.
- 응답은 `jobId`, `status`, `result`, `failureCode`, `createdAt`, `updatedAt`으로 제한한다.
- `result`는 성공일 때만, `failureCode`는 실패일 때만 값이 있다.

## 인수 조건과 테스트 목록

- [ ] 소유자는 Pending·Processing 상태를 조회한다.
- [ ] RetryWait는 외부에서 Pending으로 보인다.
- [ ] 성공한 Job은 문제 분석과 서로 다른 후보 5개를 정규화된 구조로 반환한다.
- [ ] 실패한 Job은 결과 없이 실패 코드를 반환한다.
- [ ] 다른 소유자와 존재하지 않는 Job은 같은 404 오류를 반환한다.
- [ ] 인증이 없거나 유효하지 않으면 401을 반환한다.
- [ ] 원본 결과·입력·Prompt·retry·Lease 필드는 응답하지 않는다.
- [ ] OpenAPI에 인증 및 200·401·404 계약이 노출된다.
- [ ] 집중·AI Job 위험 회귀와 로컬 전체 테스트가 통과한다.
