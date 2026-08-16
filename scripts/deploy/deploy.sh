#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

bash "${SCRIPT_DIR}/preflight.sh"

app_domain="$(deploy_read_env APP_DOMAIN)"

deploy_info "Building the backend first to avoid parallel-build memory pressure"
"${DEPLOY_COMPOSE[@]}" build --pull backend
deploy_info "Building the frontend second"
"${DEPLOY_COMPOSE[@]}" build --pull frontend
deploy_info "Starting PostgreSQL, backend, and frontend"
"${DEPLOY_COMPOSE[@]}" up -d --wait --wait-timeout 240 postgres backend frontend

if deploy_certificate_exists "${app_domain}"; then
  deploy_info "Existing TLS certificate found; starting the HTTPS proxy"
  "${DEPLOY_COMPOSE[@]}" up -d --wait --wait-timeout 90 proxy

  deploy_info "Checking public HTTPS and database-backed API health"
  for attempt in {1..18}; do
    if curl --fail --silent --show-error --max-time 10 \
      "https://${app_domain}/api/v1/health" >/dev/null; then
      deploy_info "HTTPS health check passed"
      break
    fi
    if [[ "${attempt}" == "18" ]]; then
      deploy_die "HTTPS health check failed. Inspect: ${DEPLOY_COMPOSE[*]} logs --tail=200 proxy backend"
    fi
    sleep 5
  done
else
  deploy_warn "No certificate exists yet, so the proxy was not started."
  printf '\nNext step after DNS points to this EC2 and ports 80/443 are open:\n  %s\n\n' \
    "bash ${SCRIPT_DIR}/init-tls.sh"
fi

"${DEPLOY_COMPOSE[@]}" ps
deploy_warn "This is a STAGE demonstration with SIMULATED payments, not a real payment service."
