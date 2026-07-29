# Lightsail 단일 인스턴스 배포

이 구성은 Lightsail Ubuntu 24.04 인스턴스 한 대에서 아래 컨테이너를 실행한다.

- Caddy: 외부 80/443 수신과 HTTPS 자동 인증서
- API: Spring MVC와 Actuator readiness
- Worker: HTTP 없이 랭킹·재무 정합성 예약 작업 실행
- PostgreSQL 17: 외부 포트 비공개, named volume 영속화

## 1. Lightsail 준비

1. 서울 리전에 Ubuntu 24.04 인스턴스를 만든다. API·Worker JVM과 PostgreSQL을 함께 실행하므로 2 vCPU, RAM 4 GB 이상을 권장한다.
2. Static IPv4를 생성해 인스턴스에 연결한다.
3. IPv4 방화벽에서 TCP 80, TCP 443을 허용한다. SSH 22는 가능한 한 관리자 IP로 제한한다.
4. 도메인을 쓸 경우 `api.example.com`의 A 레코드를 Static IPv4로 연결한다.

PostgreSQL 5432와 Spring Boot 8080은 Lightsail 방화벽에 열지 않는다.

## 2. 코드와 환경 파일 준비

인스턴스에 저장소를 clone한 뒤 예제 파일을 복사한다.

```bash
cp .env.production.example .env.production
chmod 600 .env.production
```

`DB_PASSWORD`와 `JWT_SECRET`은 다음처럼 각각 새 값으로 생성할 수 있다.

```bash
openssl rand -base64 48
```

IP로 먼저 확인할 때는 `SITE_ADDRESS=:80`, `AUTH_COOKIE_SECURE=false`를 사용한다. DNS가 연결된 뒤에는 `SITE_ADDRESS=api.example.com`, `AUTH_COOKIE_SECURE=true`, HTTPS 프론트엔드 확인 URL로 변경하고 다시 배포한다.

## 3. 한 번에 기동

저장소 루트에서 실행한다.

```bash
chmod +x deploy/lightsail/deploy.sh
./deploy/lightsail/deploy.sh
```

스크립트는 Docker가 없으면 설치하고, 환경 파일과 Compose를 검증한 다음 이미지 빌드, DB migration, 전체 서비스 기동, readiness 확인을 순서대로 수행한다.

확인 URL:

```text
http://STATIC_IP/actuator/health/readiness
https://api.example.com/actuator/health/readiness
```

정상 응답은 `{"status":"UP"}`이다.

## 운영 명령

```bash
sudo docker compose --env-file .env.production -f compose.production.yml ps
sudo docker compose --env-file .env.production -f compose.production.yml logs -f --tail 200 api worker
sudo docker compose --env-file .env.production -f compose.production.yml up -d --build
sudo docker compose --env-file .env.production -f compose.production.yml down
```

`down`은 named volume을 지우지 않는다. 운영 데이터가 있는 환경에서는 `down --volumes`를 실행하지 않는다.

## 현재 제한

- Blue-Green 전환과 자동 Rollback은 OPS-06 범위다.
- PostgreSQL 백업·복원 자동화는 OPS-07 범위다. 그 전까지 named volume만을 백업으로 간주하면 안 된다.
- 현재 코드에는 AI Job polling scheduler가 없으므로 Worker profile은 이미 구현된 랭킹·재무 정합성 예약 작업만 수행한다.
- 프론트엔드가 다른 Origin이면 CORS·CSRF·Cookie 정책이 확정되는 OPS-02가 필요하다. 같은 Origin reverse proxy 구성은 이 작업에 포함하지 않는다.
