#!/usr/bin/env bash

set -Eeuo pipefail

DEPLOY_SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_REPO_ROOT="$(cd -- "${DEPLOY_SCRIPT_DIR}/../.." && pwd)"
DEPLOY_ENV_FILE="${DEPLOY_ENV_FILE:-${DEPLOY_REPO_ROOT}/.env.deploy}"
DEPLOY_COMPOSE_FILE="${DEPLOY_COMPOSE_FILE:-${DEPLOY_REPO_ROOT}/infra/compose.deploy.yaml}"
DEPLOY_COMPOSE=(docker compose --env-file "${DEPLOY_ENV_FILE}" -f "${DEPLOY_COMPOSE_FILE}")

deploy_die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

deploy_warn() {
  printf 'WARNING: %s\n' "$*" >&2
}

deploy_info() {
  printf '==> %s\n' "$*"
}

deploy_require_command() {
  command -v "$1" >/dev/null 2>&1 || deploy_die "Required command not found: $1"
}

deploy_read_env() {
  local key="$1"
  local line
  [[ -f "${DEPLOY_ENV_FILE}" ]] || deploy_die "Missing ${DEPLOY_ENV_FILE}. Copy .env.deploy.example first."
  line="$(grep -m1 -E "^${key}=" "${DEPLOY_ENV_FILE}" || true)"
  [[ -n "${line}" ]] || deploy_die "Missing ${key} in ${DEPLOY_ENV_FILE}."
  line="${line#*=}"
  line="${line%$'\r'}"
  [[ -n "${line}" ]] || deploy_die "${key} must not be empty."
  printf '%s' "${line}"
}

deploy_validate_environment() {
  local app_domain certbot_email postgres_db postgres_user postgres_password
  local guest_secret

  [[ -f "${DEPLOY_COMPOSE_FILE}" ]] || deploy_die "Missing ${DEPLOY_COMPOSE_FILE}."

  app_domain="$(deploy_read_env APP_DOMAIN)"
  certbot_email="$(deploy_read_env CERTBOT_EMAIL)"
  postgres_db="$(deploy_read_env POSTGRES_DB)"
  postgres_user="$(deploy_read_env POSTGRES_USER)"
  postgres_password="$(deploy_read_env POSTGRES_PASSWORD)"
  guest_secret="$(deploy_read_env GUEST_LOOKUP_TOKEN_SECRET)"

  [[ "${app_domain}" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] \
    || deploy_die "APP_DOMAIN must be a hostname without a scheme or path."
  [[ "${app_domain}" != "shop.example.com" && "${app_domain}" == *.* ]] \
    || deploy_die "Replace the APP_DOMAIN placeholder with a real DNS name."
  [[ "${certbot_email}" == *@*.* && "${certbot_email}" != *"example.com" ]] \
    || deploy_die "Replace CERTBOT_EMAIL with a real mailbox."
  [[ "${postgres_db}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] \
    || deploy_die "POSTGRES_DB may contain only letters, numbers, and underscores."
  [[ "${postgres_user}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] \
    || deploy_die "POSTGRES_USER may contain only letters, numbers, and underscores."
  [[ ${#postgres_password} -ge 32 && "${postgres_password}" != replace_* ]] \
    || deploy_die "POSTGRES_PASSWORD must be a generated value of at least 32 characters."
  [[ ${#guest_secret} -ge 64 && "${guest_secret}" != replace_* && "${guest_secret}" != "${postgres_password}" ]] \
    || deploy_die "GUEST_LOOKUP_TOKEN_SECRET must be a different random value of at least 64 characters."
}

deploy_require_compose() {
  deploy_require_command docker
  docker info >/dev/null 2>&1 || deploy_die "Docker is not running or this user cannot access it."
  docker compose version >/dev/null 2>&1 || deploy_die "Docker Compose plugin is not installed."
  "${DEPLOY_COMPOSE[@]}" --profile tools config --quiet
}

deploy_assert_services() {
  local expected
  local services
  services="$("${DEPLOY_COMPOSE[@]}" --profile tools config --services)"
  for expected in postgres backend frontend proxy certbot; do
    grep -qx "${expected}" <<<"${services}" || deploy_die "Compose service is missing: ${expected}"
  done
}

deploy_certificate_exists() {
  local app_domain="$1"
  "${DEPLOY_COMPOSE[@]}" --profile tools run --rm --no-deps \
    --entrypoint /bin/sh certbot -c \
    "test -f '/etc/letsencrypt/live/${app_domain}/fullchain.pem'" >/dev/null 2>&1
}
