#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

deploy_info "Checking the stage deployment environment"
deploy_require_command git
deploy_require_command curl
deploy_require_command openssl
deploy_require_command realpath
deploy_require_command stat
deploy_validate_environment
deploy_validate_media_storage
deploy_require_compose
deploy_assert_services

if [[ "$(uname -m)" != "x86_64" && "$(uname -m)" != "amd64" ]]; then
  deploy_warn "Expected t3a x86_64, but this host reports $(uname -m). Verify the selected EC2 instance."
fi

if [[ "$(uname -s)" == "Linux" ]]; then
  env_mode="$(stat -c '%a' "${DEPLOY_ENV_FILE}")"
  [[ "${env_mode}" == "600" ]] || deploy_die "Run: chmod 600 ${DEPLOY_ENV_FILE}"

  memory_kib="$(awk '/MemTotal:/ {print $2}' /proc/meminfo)"
  (( memory_kib >= 3500000 )) || deploy_warn "Less than about 4 GiB RAM is visible. t3a.medium should provide 4 GiB."

  free_kib="$(df -Pk "${DEPLOY_REPO_ROOT}" | awk 'NR == 2 {print $4}')"
  (( free_kib >= 8388608 )) || deploy_warn "Less than 8 GiB disk space is free. Builds and backups may fill EBS."

  media_free_kib="$(df -Pk "$(deploy_read_env MEDIA_HOST_PATH)" | awk 'NR == 2 {print $4}')"
  (( media_free_kib >= 2097152 )) \
    || deploy_warn "Less than 2 GiB is free on the persistent media filesystem. Image uploads may fill it."
fi

for required_media in \
  media/demo/home/summer-hydration.webp \
  media/demo/catalog/oasis-water-bowl.webp; do
  [[ -f "${DEPLOY_REPO_ROOT}/${required_media}" ]] \
    || deploy_die "Required demonstration media is missing: ${required_media}"
done

if git -C "${DEPLOY_REPO_ROOT}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  if [[ -n "$(git -C "${DEPLOY_REPO_ROOT}" status --porcelain)" ]]; then
    deploy_die "The release tree has uncommitted files. Commit and test one exact release before deployment."
  fi
  deploy_info "Release commit: $(git -C "${DEPLOY_REPO_ROOT}" rev-parse --short HEAD)"
  if git -C "${DEPLOY_REPO_ROOT}" check-ignore -q .env.deploy 2>/dev/null; then
    deploy_info ".env.deploy is ignored by Git"
  else
    deploy_die ".env.deploy is not ignored by Git. Do not continue with secrets at risk."
  fi
else
  deploy_warn "No Git metadata is present. Confirm this directory came from a tested git archive."
fi

deploy_warn "SIMULATED payment is enabled. This stage server must be labelled as a test-payment demonstration."
deploy_info "Preflight passed"
