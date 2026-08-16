#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

deploy_validate_environment
deploy_require_compose
deploy_assert_services

app_domain="$(deploy_read_env APP_DOMAIN)"
legacy_app_domain="$(deploy_read_env LEGACY_APP_DOMAIN)"
deploy_certificate_exists "${app_domain}" \
  || deploy_die "No certificate exists for ${app_domain}. Run init-tls.sh first."
deploy_certificate_exists "${legacy_app_domain}" \
  || deploy_die "No certificate exists for ${legacy_app_domain}. Run init-tls.sh first."

proxy_was_running=false
if "${DEPLOY_COMPOSE[@]}" ps --status running --services | grep -qx proxy; then
  proxy_was_running=true
fi

restart_proxy() {
  if [[ "${proxy_was_running}" == "true" ]]; then
    deploy_info "Restoring the HTTPS proxy"
    "${DEPLOY_COMPOSE[@]}" up -d proxy >/dev/null
  fi
}
trap restart_proxy EXIT

if [[ "${proxy_was_running}" == "true" ]]; then
  deploy_info "Stopping the proxy briefly so Certbot standalone can bind port 80"
  "${DEPLOY_COMPOSE[@]}" stop proxy
fi

deploy_info "Checking and renewing the certificate only when Certbot says it is due"
"${DEPLOY_COMPOSE[@]}" --profile tools run --rm --service-ports certbot \
  renew --standalone --non-interactive

deploy_info "TLS renewal check completed"
