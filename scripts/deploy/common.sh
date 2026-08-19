#!/usr/bin/env bash

set -Eeuo pipefail

DEPLOY_SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_REPO_ROOT="$(cd -- "${DEPLOY_SCRIPT_DIR}/../.." && pwd)"
DEPLOY_ENV_FILE="${DEPLOY_ENV_FILE:-${DEPLOY_REPO_ROOT}/.env.deploy}"
DEPLOY_COMPOSE_FILE="${DEPLOY_COMPOSE_FILE:-${DEPLOY_REPO_ROOT}/infra/compose.deploy.yaml}"
DEPLOY_COMPOSE=(docker compose --env-file "${DEPLOY_ENV_FILE}" -f "${DEPLOY_COMPOSE_FILE}")
DEPLOY_BACKEND_UID=10001
DEPLOY_BACKEND_GID=10001

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
  local app_domain app_www_domain legacy_app_domain certbot_email
  local postgres_db postgres_user postgres_password
  local guest_secret media_host_path

  [[ -f "${DEPLOY_COMPOSE_FILE}" ]] || deploy_die "Missing ${DEPLOY_COMPOSE_FILE}."

  app_domain="$(deploy_read_env APP_DOMAIN)"
  app_www_domain="$(deploy_read_env APP_WWW_DOMAIN)"
  legacy_app_domain="$(deploy_read_env LEGACY_APP_DOMAIN)"
  certbot_email="$(deploy_read_env CERTBOT_EMAIL)"
  postgres_db="$(deploy_read_env POSTGRES_DB)"
  postgres_user="$(deploy_read_env POSTGRES_USER)"
  postgres_password="$(deploy_read_env POSTGRES_PASSWORD)"
  guest_secret="$(deploy_read_env GUEST_LOOKUP_TOKEN_SECRET)"
  media_host_path="$(deploy_read_env MEDIA_HOST_PATH)"

  [[ "${app_domain}" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] \
    || deploy_die "APP_DOMAIN must be a hostname without a scheme or path."
  [[ "${app_domain}" != "shop.example.com" && "${app_domain}" == *.* ]] \
    || deploy_die "Replace the APP_DOMAIN placeholder with a real DNS name."
  [[ "${app_www_domain}" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] \
    || deploy_die "APP_WWW_DOMAIN must be a hostname without a scheme or path."
  [[ "${app_www_domain}" != "www.shop.example.com" && "${app_www_domain}" == *.* ]] \
    || deploy_die "Replace the APP_WWW_DOMAIN placeholder with a real DNS name."
  [[ "${legacy_app_domain}" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] \
    || deploy_die "LEGACY_APP_DOMAIN must be a hostname without a scheme or path."
  [[ "${legacy_app_domain}" != "legacy-shop.example.com" && "${legacy_app_domain}" == *.* ]] \
    || deploy_die "Replace the LEGACY_APP_DOMAIN placeholder with a real DNS name."
  [[ "${app_domain}" != "${app_www_domain}" \
      && "${app_domain}" != "${legacy_app_domain}" \
      && "${app_www_domain}" != "${legacy_app_domain}" ]] \
    || deploy_die "APP_DOMAIN, APP_WWW_DOMAIN, and LEGACY_APP_DOMAIN must be different hostnames."
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
  [[ "${media_host_path}" == /* && "${media_host_path}" != "/" ]] \
    || deploy_die "MEDIA_HOST_PATH must be an absolute host directory other than /."
}

deploy_validate_media_storage() {
  local media_host_path media_real repo_real media_uid media_gid media_mode media_mode_value

  media_host_path="$(deploy_read_env MEDIA_HOST_PATH)"
  [[ -e "${media_host_path}" ]] \
    || deploy_die "MEDIA_HOST_PATH does not exist. Prepare it first: sudo install -d -o ${DEPLOY_BACKEND_UID} -g ${DEPLOY_BACKEND_GID} -m 0750 ${media_host_path}"
  [[ -d "${media_host_path}" ]] \
    || deploy_die "MEDIA_HOST_PATH must point to a directory: ${media_host_path}"
  [[ ! -L "${media_host_path}" ]] \
    || deploy_die "MEDIA_HOST_PATH must be a real directory, not a symbolic link: ${media_host_path}"

  media_real="$(realpath -e -- "${media_host_path}")"
  repo_real="$(realpath -e -- "${DEPLOY_REPO_ROOT}")"
  case "${media_real}" in
    "${repo_real}"|"${repo_real}"/*)
      deploy_die "MEDIA_HOST_PATH must stay outside the Git checkout: ${media_real}"
      ;;
  esac

  media_uid="$(stat -c '%u' "${media_real}")"
  media_gid="$(stat -c '%g' "${media_real}")"
  media_mode="$(stat -c '%a' "${media_real}")"
  [[ "${media_uid}" == "${DEPLOY_BACKEND_UID}" && "${media_gid}" == "${DEPLOY_BACKEND_GID}" ]] \
    || deploy_die "MEDIA_HOST_PATH must be owned by ${DEPLOY_BACKEND_UID}:${DEPLOY_BACKEND_GID}. Do not recursively chown an unknown directory; verify the path, then prepare this dedicated directory explicitly."

  media_mode_value=$((8#${media_mode}))
  (( (media_mode_value & 0300) == 0300 )) \
    || deploy_die "MEDIA_HOST_PATH owner must have write and traverse permission: ${media_real} (mode ${media_mode})"
  (( (media_mode_value & 0022) == 0 )) \
    || deploy_die "MEDIA_HOST_PATH must not be writable by group or others: ${media_real} (mode ${media_mode})"

  deploy_info "Persistent media directory: ${media_real} (owner ${media_uid}:${media_gid}, mode ${media_mode})"
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
