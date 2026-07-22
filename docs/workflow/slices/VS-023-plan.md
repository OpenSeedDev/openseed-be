# VS-023 AI 최종 실패와 수동 양식

## 시스템 결과

AI Provider의 일시 오류가 재시도 한도에 도달하면 Job을 영구 실패로 종료하고, 소유자가 일반 Draft 작성 화면으로 계속 진행할 수 있는 수동 양식과 안정적인 실패 코드를 조회한다.

## 포함 범위

- Timeout·429·5xx 실패를 최대 3번 시도한 뒤 `FAILED`로 종료
- 최종 실패 코드를 외부 계약 `AI_GENERATION_FAILED`로 통일
- 실패한 Job 조회 응답에 일반 Idea Draft와 동일한 7개 필드의 수동 작성 양식 제공
- 최종 실패를 정상적인 Worker 처리 결과로 반환하여 Provider 장애가 일반 HTTP API에 전파되지 않도록 격리
- API 계약과 AI Job 데이터 설명 갱신

## 제외 범위

- Provider별 세부 원인·원문·내부 재시도 횟수 노출
- 수동 양식의 자동 저장 또는 새 Draft 생성
- 실패 Job 재시작·취소 API
- Schema 오류 재시도 정책 변경

## API·데이터 영향

- 기존 `GET /api/v1/ai/idea-jobs/{jobId}`의 실패 응답에 `manualForm`을 추가한다.
- `manualForm`은 `title`, `category`, `summary`, `problem`, `targetCustomer`, `solution`, `businessModel` 빈 문자열을 제공한다.
- 기존 `ai_jobs.failure_code`에 `AI_GENERATION_FAILED`를 저장하므로 새 테이블·컬럼과 Migration은 없다.

## 인수 조건과 테스트 목록

- [ ] 일시 오류는 첫 번째와 두 번째 실패 후 Backoff 재시도한다.
- [ ] 세 번째 일시 오류는 Job을 `FAILED`로 끝내고 Lease·다음 시도 시각을 제거한다.
- [ ] 최종 실패 처리 결과는 예외가 아니라 `FAILED_FINAL`로 반환된다.
- [ ] 소유자는 실패 코드 `AI_GENERATION_FAILED`와 7개 필드 수동 양식을 조회한다.
- [ ] Pending·Processing·Succeeded 응답에는 수동 양식을 노출하지 않는다.
- [ ] 실패 API 응답에 Provider 세부 원인·입력·Lease·retry count가 노출되지 않는다.
- [ ] OpenAPI에 실패 응답의 `manualForm` 계약이 노출된다.
- [ ] 기존 Worker Backoff·Lease·결과 조회 회귀 테스트가 통과한다.
