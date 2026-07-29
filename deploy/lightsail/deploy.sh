#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${1:-${REPOSITORY_ROOT}/.env.production}"
COMPOSE_FILE="${REPOSITORY_ROOT}/compose.production.yml"

fail() {
  printf '배포 실패: %s\n' "$1" >&2
  exit 1
}

[[ -f "${ENV_FILE}" ]] || fail "환경 파일이 없습니다: ${ENV_FILE}"

for required_name in \
  SITE_ADDRESS DB_NAME DB_USERNAME DB_PASSWORD JWT_SECRET AUTH_COOKIE_SECURE \
  COMPANY_VERIFICATION_CONFIRM_URL MAIL_HOST MAIL_PORT MAIL_USERNAME MAIL_PASSWORD MAIL_FROM; do
  if ! grep -Eq "^${required_name}=.+" "${ENV_FILE}"; then
    fail "${required_name} 값이 환경 파일에 없습니다."
  fi
done

if grep -Eq '=(replace-with-|change-me|example-secret)' "${ENV_FILE}"; then
  fail "환경 파일에 교체하지 않은 예제 Secret이 있습니다."
fi

if ! command -v docker >/dev/null 2>&1; then
  command -v apt-get >/dev/null 2>&1 || fail "Ubuntu apt-get 환경에서 실행해 주세요."
  sudo apt-get update
  sudo apt-get install --yes docker.io docker-compose-v2 curl
  sudo systemctl enable --now docker
fi

if docker info >/dev/null 2>&1; then
  DOCKER=(docker)
elif sudo docker info >/dev/null 2>&1; then
  DOCKER=(sudo docker)
else
  fail "Docker daemon에 연결할 수 없습니다."
fi

if ! "${DOCKER[@]}" compose version >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install --yes docker-compose-v2
fi

if ! command -v curl >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install --yes curl
fi

COMPOSE=("${DOCKER[@]}" compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")

"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" build api
"${COMPOSE[@]}" up --detach --remove-orphans

for attempt in $(seq 1 36); do
  if curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness >/dev/null; then
    printf '배포 완료: API readiness가 UP입니다.\n'
    "${COMPOSE[@]}" ps
    exit 0
  fi
  sleep 5
done

"${COMPOSE[@]}" ps >&2
"${COMPOSE[@]}" logs --tail 120 api worker postgres >&2
fail "3분 안에 API readiness가 UP이 되지 않았습니다."
