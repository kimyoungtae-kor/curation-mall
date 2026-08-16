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
app_www_domain="$(deploy_read_env APP_WWW_DOMAIN)"
legacy_app_domain="$(deploy_read_env LEGACY_APP_DOMAIN)"
certbot_email="$(deploy_read_env CERTBOT_EMAIL)"

primary_certificate_exists=false
legacy_certificate_exists=false
if deploy_certificate_exists "${app_domain}"; then
  primary_certificate_exists=true
fi
if deploy_certificate_exists "${legacy_app_domain}"; then
  legacy_certificate_exists=true
fi

if [[ "${primary_certificate_exists}" == true \
    && "${legacy_certificate_exists}" == true ]]; then
  deploy_die "Both certificates already exist. Use renew-tls.sh for renewal or deploy.sh to start the proxy."
fi

for domain in "${app_domain}" "${app_www_domain}" "${legacy_app_domain}"; do
  resolved_ipv4="$(getent ahostsv4 "${domain}" | awk 'NR == 1 {print $1}')"
  [[ -n "${resolved_ipv4}" ]] \
    || deploy_die "${domain} does not resolve to IPv4 yet. Fix its DNS record first."
  deploy_info "${domain} currently resolves to ${resolved_ipv4}"
done
deploy_warn "Confirm this is the EC2 Elastic IP and security-group port 80 is open before continuing."

read -r -p "Type ISSUE ${app_domain} to request the certificate: " confirmation
[[ "${confirmation}" == "ISSUE ${app_domain}" ]] || deploy_die "Confirmation did not match; no certificate was requested."

proxy_was_running=false
if "${DEPLOY_COMPOSE[@]}" ps --status running --services | grep -qx proxy; then
  proxy_was_running=true
fi

restore_proxy_on_failure() {
  if [[ "${proxy_was_running}" == true ]]; then
    deploy_warn "Restoring the previously running proxy after certificate setup did not complete."
    "${DEPLOY_COMPOSE[@]}" up -d proxy >/dev/null 2>&1 || true
  fi
}
trap restore_proxy_on_failure EXIT

"${DEPLOY_COMPOSE[@]}" stop proxy >/dev/null 2>&1 || true

if [[ "${primary_certificate_exists}" == false ]]; then
  deploy_info "Requesting the primary Let's Encrypt certificate using standalone HTTP validation"
  "${DEPLOY_COMPOSE[@]}" --profile tools run --rm --service-ports certbot \
    certonly --standalone --non-interactive --agree-tos --no-eff-email \
    --email "${certbot_email}" --cert-name "${app_domain}" \
    -d "${app_domain}" -d "${app_www_domain}"
else
  deploy_info "Existing primary certificate found: ${app_domain}"
fi

if [[ "${legacy_certificate_exists}" == false ]]; then
  deploy_info "Requesting the redirect-only legacy-domain certificate"
  "${DEPLOY_COMPOSE[@]}" --profile tools run --rm --service-ports certbot \
    certonly --standalone --non-interactive --agree-tos --no-eff-email \
    --email "${certbot_email}" --cert-name "${legacy_app_domain}" \
    -d "${legacy_app_domain}"
else
  deploy_info "Existing legacy-domain certificate found: ${legacy_app_domain}"
fi

deploy_info "Starting the HTTPS proxy"
"${DEPLOY_COMPOSE[@]}" up -d --wait --wait-timeout 90 proxy
trap - EXIT

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
