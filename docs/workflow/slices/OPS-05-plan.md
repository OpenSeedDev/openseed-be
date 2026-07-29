# OPS-05 Docker 이미지와 Lightsail 단일 인스턴스 배포 계획

## 사용자·운영 결과

- 동일한 불변 Docker 이미지로 API와 Worker 프로필을 각각 실행한다.
- Lightsail Ubuntu 인스턴스 한 대에서 Caddy, API, Worker, PostgreSQL을 한 번의 Docker Compose 명령으로 기동한다.
- API 종료 시 새 요청 수신을 멈추고 진행 중 요청과 예약 작업을 정해진 시간 안에 마친다.
- 도메인이 있으면 Caddy가 HTTPS를 자동 적용하고, 도메인이 없으면 정적 IP의 HTTP로 먼저 검증할 수 있다.

## 범위

- Java 21 기반 multi-stage Docker 이미지와 non-root 런타임
- `api`, `worker` Spring profile 및 Graceful Shutdown 설정
- 운영용 PostgreSQL, API, Worker, Caddy Compose 구성
- Secret을 저장소에 넣지 않는 `.env.production.example`
- Lightsail 인스턴스용 1회 배포 스크립트와 상태 확인 절차
- 이미지·프로필·Compose 계약의 자동 검증

## 제외

- Lightsail 인스턴스, Static IP, DNS 레코드의 AWS 계정 내 실제 생성
- Blue-Green 트래픽 전환과 자동 Rollback(OPS-06)
- 운영 DB 백업과 실제 복원 훈련(OPS-07)
- 외부 SMTP, AI Provider, 관측 플랫폼의 계정 발급
- AI Job polling scheduler 추가

## 의존성과 데이터 영향

- `SETUP-08`은 백로그 `initial_merged`와 이를 포함한 현재 `main`으로 충족됐다.
- 기존 Flyway migration과 DB 스키마는 변경하지 않는다.
- PostgreSQL 데이터는 Docker named volume에 저장하지만, 별도 백업·복원 자동화는 OPS-07에서 다룬다.

## 인수 조건과 테스트 목록

1. Dockerfile은 Gradle 빌드와 Java 21 런타임을 분리하고 non-root 사용자로 실행한다.
2. 하나의 이미지가 `api`와 `worker` profile에서 기동 가능하다.
3. API profile은 HTTP와 health probe를 제공하고 예약 작업을 실행하지 않는다.
4. Worker profile은 HTTP 서버를 열지 않고 현재 예약 작업만 실행한다.
5. SIGTERM 수신 시 API 요청과 Spring scheduler에 최대 30초의 정상 종료 시간을 준다.
6. 운영 Compose는 PostgreSQL 준비 완료 뒤 API·Worker를 시작하고 API readiness 뒤 Caddy를 연결한다.
7. PostgreSQL과 애플리케이션 포트는 외부에 직접 공개하지 않고 80/443만 Caddy가 공개한다.
8. 예제 환경 파일에는 실제 Secret이 없고 필수 값과 안전한 생성 방법이 문서화된다.
9. 배포 스크립트는 환경 검증, 빌드·기동, readiness 확인을 실패 즉시 중단 방식으로 수행한다.
10. 패키징 계약 검사, 프로필 집중 테스트, 전체 회귀 테스트와 워크플로우 검증이 성공한다.
