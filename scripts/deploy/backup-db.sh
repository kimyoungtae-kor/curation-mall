#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

deploy_validate_environment
deploy_require_compose

postgres_db="$(deploy_read_env POSTGRES_DB)"
postgres_user="$(deploy_read_env POSTGRES_USER)"
backup_root="${DEPLOY_BACKUP_DIR:-${DEPLOY_REPO_ROOT}/backups/postgres}"
timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
final_file="${backup_root}/${postgres_db}_${timestamp}.dump"
partial_file="${final_file}.part"

mkdir -p "${backup_root}"
chmod 700 "${backup_root}"
trap 'rm -f -- "${partial_file}"' EXIT

deploy_info "Creating a PostgreSQL custom-format backup"
"${DEPLOY_COMPOSE[@]}" exec -T postgres \
  pg_dump --username "${postgres_user}" --dbname "${postgres_db}" \
  --format custom --no-owner --no-privileges >"${partial_file}"

[[ -s "${partial_file}" ]] || deploy_die "Backup output was empty."
mv -- "${partial_file}" "${final_file}"
sha256sum "${final_file}" >"${final_file}.sha256"
trap - EXIT

deploy_info "Backup created: ${final_file}"
deploy_warn "Copy this backup off EC2. A file on the same EBS volume is not a complete backup."
