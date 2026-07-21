# VS-020 구조화 AI 후보 5개

## 시스템 결과

Worker가 AI Provider의 구조화 응답을 받아 문제 분석과 서로 다른 후보 5개를 검증·정규화해 저장하고 Job을 성공 종료한다.

## 전달 방식과 선행 조건

- Fast Build Stacked PR: `codex/vs-019-worker-lease` 기반, parent VS-019 PR #58, stack root VS-018, depth 3
- parent Head: `947ae68559219083ab5d1b72fa666d68d1d40f82`
- VS-019는 아직 Merge되지 않았으며 parent 변경 시 이 슬라이스를 다시 검증한다.
- Merge 순서: VS-018 → VS-019 → VS-020

## 포함 범위

- AI Provider 추상화와 구조화 JSON 응답 처리
- 문제 분석과 제목·카테고리·요약·문제·고객·해결책·수익 모델 후보 정확히 5개 검증
- 필수값·길이·후보 제목 중복 검증과 공백 정규화
- 원본 JSON과 정규화 JSON을 `AiGenerationResult`로 한 번만 저장
- 유효 결과의 `SUCCEEDED`, Schema 오류의 `FAILED / INVALID_RESPONSE_SCHEMA` 전이
- Timeout·429·5xx Provider 실패의 기존 Backoff 재시도 연결
- Lease fencing을 지킨 결과 완료와 새 UTC Flyway Migration

## 제외 범위

- Job 상태·결과 사용자 조회 API: VS-021
- 후보 선택·편집과 Idea Draft 전환: VS-022
- 최종 재시도 종료 정책과 수동 작성 양식: VS-023
- 실제 외부 AI SDK·키 설정과 Worker 스케줄러: 운영 후속 작업
- AI 결과의 자동 게시

## 인수 조건과 테스트 목록

- [ ] 정확히 5개의 서로 다른 유효 후보와 문제 분석을 저장하고 Job을 성공 종료한다.
- [ ] 원본 JSON과 공백을 제거한 정규화 JSON을 함께 저장한다.
- [ ] 후보 개수, 필수값, 필드 길이, 중복 제목, JSON Schema 오류를 거부한다.
- [ ] Schema 오류는 Provider 재시도 오류와 구분해 실패 코드를 기록한다.
- [ ] Timeout·429·5xx는 기존 Backoff 재시도로 연결한다.
- [ ] 만료되거나 교체된 Lease token은 결과를 완료할 수 없다.
- [ ] 같은 Job 결과는 한 번만 저장되며 Idea를 생성하거나 게시하지 않는다.
- [ ] VS-018 생성과 VS-019 Worker 위험 회귀가 통과한다.
