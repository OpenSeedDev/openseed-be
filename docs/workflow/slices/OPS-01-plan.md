# OPS-01 서비스 상태와 요청 추적 계획

## 사용자·운영 결과

- 운영 환경이 애플리케이션 생존 여부와 트래픽 수신 준비 여부를 분리해 확인할 수 있다.
- 모든 HTTP 요청은 안전한 `requestId`를 응답 헤더, 오류 응답, 접근 로그에서 동일하게 추적할 수 있다.
- 접근 로그는 HTTP 메서드, 라우트 패턴, 상태 코드, 처리 시간, requestId만 구조화해 남기며 요청 본문·쿼리·인증 정보·개인정보를 남기지 않는다.

## 범위

- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `X-Request-Id` 생성·안전한 입력값 전파·응답 헤더 제공
- 공통 오류 응답의 requestId 연계
- MDC와 JSON 접근 로그 이벤트
- 운영 설정과 OpenAPI 공통 헤더 계약 문서

## 제외

- 외부 관측 플랫폼, 로그 수집기, 알림, 대시보드, 배포 환경 구성
- 도메인별 운영 지표와 Job ID 추적
- DB·Migration·ERD 변경

## 의존성과 데이터 영향

- `SETUP-08`은 PR #26이 반영된 `main` foundation과 백로그의 `initial_merged`로 충족됐다.
- 데이터베이스 스키마와 저장 데이터에는 영향이 없다.

## 인수 조건과 테스트 목록

1. liveness와 readiness만 필요한 health 경로로 노출되고 둘 다 정상 상태에서 `UP`을 반환한다.
2. requestId가 없는 요청은 UUID requestId를 생성해 `X-Request-Id`로 반환한다.
3. 안전한 1~64자 requestId는 그대로 전파하고, 빈 값·길이 초과·허용하지 않는 문자가 있는 값은 새 UUID로 교체한다.
4. 오류 응답의 `requestId`는 응답 헤더의 requestId와 같다.
5. 접근 로그는 requestId, method, route, status, durationMs를 포함하는 JSON 이벤트다.
6. 접근 로그는 쿼리 문자열, Authorization·Cookie 헤더, 이메일·비밀번호·토큰 등 요청 본문을 포함하지 않는다.
7. 요청 완료 후 MDC의 requestId가 정리되어 스레드 재사용 시 누출되지 않는다.
8. 전체 테스트와 워크플로우 검증이 성공한다.
