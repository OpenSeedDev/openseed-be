# VS-019 AI Worker Lease와 Backoff

## 시스템 결과

여러 AI Worker가 동시에 실행되어도 처리 가능한 Job을 한 Worker만 선점하며, Worker 중단으로 만료된 Lease를 복구하고 Timeout·429·5xx 실패를 지수 Backoff 뒤 재시도한다.

## 전달 방식과 선행 조건

- Fast Build Stacked PR: `codex/vs-018-ai-job` 기반, parent VS-018 PR #38, stack root VS-018, depth 2
- parent Head: `3b0f158942975bb786ea31d5c63fdc1fa842c520`
- VS-018은 아직 Merge되지 않았으며 parent 변경 시 이 슬라이스를 다시 검증한다.
- Merge 순서: VS-018 → VS-019

## 포함 범위

- PostgreSQL `FOR UPDATE SKIP LOCKED` 기반 가장 오래된 실행 가능 Job 선점
- `PENDING` → `PROCESSING` 상태 전이와 2분 Lease, Worker ID, 매 선점마다 새 fencing token
- 만료된 `PROCESSING` Lease 재선점과 이전 token의 상태 변경 차단
- Timeout·429·5xx 실패를 `RETRY_WAIT`로 전환하고 retry count 증가
- 30초부터 두 배씩 증가하고 15분으로 제한되는 Backoff
- Backoff 만료 시 재선점, 만료 전 Job 제외
- 새 UTC Flyway Migration, JPA 매핑과 ERD 갱신

## 제외 범위

- AI Provider 실제 호출과 후보 결과 저장: VS-020
- Job 상태·결과 사용자 API: VS-021
- 성공 완료와 최종 실패·수동 작성 전환: VS-020·VS-023
- Worker 스케줄러·프로세스 패키징·Graceful Shutdown: OPS-05

## 데이터 영향

- `ai_jobs.status`에 `PROCESSING`, `RETRY_WAIT`를 추가한다.
- `lease_owner`, `lease_token`, `next_attempt_at`을 추가한다.
- 상태별 Lease/Backoff 필드 일관성을 DB CHECK로 강제하고 실행 가능 Job 조회 인덱스를 추가한다.
- 공개 HTTP API 변경은 없다.

## 인수 조건과 테스트 목록

- [ ] PENDING Job 선점 시 PROCESSING과 2분 Lease·Worker·token을 원자 저장한다.
- [ ] 동시 Worker는 같은 Job을 중복 선점하지 않는다.
- [ ] 활성 Lease는 다른 Worker가 선점하지 못한다.
- [ ] 만료 Lease는 새 Worker와 새 token으로 복구된다.
- [ ] 이전 fencing token으로 retry 상태를 기록할 수 없다.
- [ ] Timeout·429·5xx는 retry count를 올리고 30초 Backoff를 예약한다.
- [ ] 연속 실패 Backoff는 30·60·120초로 증가하고 최대 15분이다.
- [ ] Backoff 만료 전에는 선점하지 않고 정확한 만료 시각부터 선점한다.
- [ ] VS-018 생성·멱등성 회귀와 PostgreSQL Migration 검증이 통과한다.
