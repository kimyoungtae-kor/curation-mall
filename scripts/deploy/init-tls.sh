#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

deploy_require_command getent
deploy_validate_environment
deploy_require_compose
deploy_assert_services

app_domain="$(deploy_read_env APP_DOMAIN)"
certbot_email="$(deploy_read_env CERTBOT_EMAIL)"

if deploy_certificate_exists "${app_domain}"; then
  deploy_die "A certificate already exists for ${app_domain}. Use renew-tls.sh instead."
fi

resolved_ipv4="$(getent ahostsv4 "${app_domain}" | awk 'NR == 1 {print $1}')"
[[ -n "${resolved_ipv4}" ]] || deploy_die "${app_domain} does not resolve to IPv4 yet. Fix the DNS A record first."
deploy_info "${app_domain} currently resolves to ${resolved_ipv4}"
deploy_warn "Confirm this is the EC2 Elastic IP and security-group port 80 is open before continuing."

read -r -p "Type ISSUE ${app_domain} to request the certificate: " confirmation
[[ "${confirmation}" == "ISSUE ${app_domain}" ]] || deploy_die "Confirmation did not match; no certificate was requested."

"${DEPLOY_COMPOSE[@]}" stop proxy >/dev/null 2>&1 || true

deploy_info "Requesting the first Let's Encrypt certificate using standalone HTTP validation"
"${DEPLOY_COMPOSE[@]}" --profile tools run --rm --service-ports certbot \
  certonly --standalone --non-interactive --agree-tos --no-eff-email \
  --email "${certbot_email}" --cert-name "${app_domain}" -d "${app_domain}"

deploy_info "Starting the HTTPS proxy"
"${DEPLOY_COMPOSE[@]}" up -d --wait --wait-timeout 90 proxy

for attempt in {1..18}; do
  if curl --fail --silent --show-error --max-time 10 \
    "https://${app_domain}/api/v1/health" >/dev/null; then
    deploy_info "First HTTPS certificate and API health check passed"
    exit 0
  fi
  if [[ "${attempt}" == "18" ]]; then
    deploy_die "Certificate was issued, but HTTPS health failed. Inspect proxy and backend logs."
  fi
  sleep 5
done
