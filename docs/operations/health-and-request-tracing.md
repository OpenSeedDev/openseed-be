# 서비스 상태와 HTTP 요청 추적

## 상태 확인

애플리케이션은 Spring Boot Actuator의 health endpoint만 외부 HTTP 관리 endpoint로 노출한다.

| 목적 | 경로 | 정상 응답 |
|---|---|---|
| 프로세스 생존 확인 | `GET /actuator/health/liveness` | `200`, `{"status":"UP"}` |
| 트래픽 수신 준비 확인 | `GET /actuator/health/readiness` | `200`, `{"status":"UP"}` |

상세 컴포넌트 상태는 응답하지 않는다. 외부 관측 플랫폼 연결, 경보와 배포 probe 설정은 이 슬라이스 범위가 아니다.

## requestId 계약

- 클라이언트는 선택적으로 `X-Request-Id`를 보낼 수 있다.
- 영문 대소문자, 숫자, `.`, `_`, `:`, `-`로 구성된 1~64자 값만 그대로 사용한다.
- 값이 없거나 계약에 맞지 않으면 서버가 UUID를 새로 생성한다.
- 최종 requestId는 모든 HTTP 응답의 `X-Request-Id`, 공통 오류 응답의 `requestId`, MDC의 `requestId`, 접근 로그의 `requestId`에서 동일하다.
- 요청 처리가 끝나면 MDC 값을 복원하거나 제거한다.

## 구조화 접근 로그

각 HTTP 요청은 다음 키만 포함한 JSON 이벤트 한 건을 INFO 레벨로 남긴다.

```json
{
  "event": "http_request",
  "requestId": "web-01.request:42",
  "method": "POST",
  "route": "/api/v1/auth/signup",
  "status": 400,
  "durationMs": 12
}
```

`route`는 원본 URI나 쿼리 문자열이 아니라 Spring MVC의 매칭된 라우트 패턴이며, 매칭되지 않은 요청은 `UNMAPPED`로 기록한다. 요청·응답 본문, 쿼리 문자열, 이메일·비밀번호 같은 개인정보, Authorization·Cookie·토큰 헤더는 기록하지 않는다.
