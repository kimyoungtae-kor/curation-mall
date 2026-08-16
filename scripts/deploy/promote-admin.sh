#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

deploy_validate_environment
deploy_require_compose

postgres_db="$(deploy_read_env POSTGRES_DB)"
postgres_user="$(deploy_read_env POSTGRES_USER)"

deploy_info "First create the account through the public signup screen with a user-chosen strong password."
deploy_warn "This script never reads, prints, or changes that password."
read -r -p "Normalized signup email to promote: " admin_email
admin_email="$(printf '%s' "${admin_email}" | tr '[:upper:]' '[:lower:]' | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
[[ "${admin_email}" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]] \
  || deploy_die "Enter the same valid email used at signup."

read -r -p "Type PROMOTE ${admin_email} to grant ADMIN: " confirmation
[[ "${confirmation}" == "PROMOTE ${admin_email}" ]] \
  || deploy_die "Confirmation did not match; no role was changed."

deploy_info "Granting ADMIN to the existing active account"
"${DEPLOY_COMPOSE[@]}" exec -T postgres \
  psql --username "${postgres_user}" --dbname "${postgres_db}" \
  --set ON_ERROR_STOP=1 --set admin_email="${admin_email}" <<'SQL'
BEGIN;
SELECT set_config('pet.admin_email', :'admin_email', true);

DO $block$
DECLARE
    target_user_id UUID;
BEGIN
    SELECT id
      INTO target_user_id
      FROM users
     WHERE normalized_email = current_setting('pet.admin_email')
       AND status = 'ACTIVE';

    IF target_user_id IS NULL THEN
        RAISE EXCEPTION 'No ACTIVE signup account matched that normalized email.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM roles WHERE code = 'ADMIN') THEN
        RAISE EXCEPTION 'ADMIN role is missing; verify Flyway migration status.';
    END IF;

    DELETE FROM user_roles
     WHERE user_id = target_user_id;

    INSERT INTO user_roles (user_id, role_id)
    SELECT target_user_id, id
      FROM roles
     WHERE code = 'ADMIN'
    ON CONFLICT DO NOTHING;
END;
$block$;
COMMIT;

SELECT u.normalized_email, string_agg(r.code, ',' ORDER BY r.code) AS roles
  FROM users u
  JOIN user_roles ur ON ur.user_id = u.id
  JOIN roles r ON r.id = ur.role_id
 WHERE u.normalized_email = :'admin_email'
 GROUP BY u.normalized_email;
SQL

deploy_info "The account now has ADMIN only. Log out and log in again so the session receives the new authority."
