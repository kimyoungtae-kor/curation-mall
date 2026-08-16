#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

deploy_validate_environment
deploy_require_compose

postgres_db="$(deploy_read_env POSTGRES_DB)"
postgres_user="$(deploy_read_env POSTGRES_USER)"
catalog_seed="${DEPLOY_REPO_ROOT}/backend/src/main/resources/db/seed/R__seed_demo_catalog.sql"
merchandising_seed="${DEPLOY_REPO_ROOT}/backend/src/main/resources/db/seed/R__seed_demo_merchandising.sql"

[[ -f "${catalog_seed}" ]] || deploy_die "Missing safe catalog seed: ${catalog_seed}"
[[ -f "${merchandising_seed}" ]] || deploy_die "Missing safe merchandising seed: ${merchandising_seed}"

deploy_warn "This loads fictional catalog and merchandising data only."
deploy_warn "Identity and order seed files are deliberately never executed."
read -r -p "Type LOAD SAFE STAGE DEMO DATA to continue: " confirmation
[[ "${confirmation}" == "LOAD SAFE STAGE DEMO DATA" ]] \
  || deploy_die "Confirmation did not match; no demo data was loaded."

deploy_info "Loading catalog seed in one transaction"
"${DEPLOY_COMPOSE[@]}" exec -T postgres \
  psql --username "${postgres_user}" --dbname "${postgres_db}" \
  --set ON_ERROR_STOP=1 --single-transaction <"${catalog_seed}"

deploy_info "Loading merchandising seed in one transaction"
"${DEPLOY_COMPOSE[@]}" exec -T postgres \
  psql --username "${postgres_user}" --dbname "${postgres_db}" \
  --set ON_ERROR_STOP=1 --single-transaction <"${merchandising_seed}"

"${DEPLOY_COMPOSE[@]}" exec -T postgres \
  psql --username "${postgres_user}" --dbname "${postgres_db}" \
  --set ON_ERROR_STOP=1 --tuples-only --command \
  "SELECT 'products=' || COUNT(*) FROM products UNION ALL SELECT 'collections=' || COUNT(*) FROM collections;"

deploy_info "Safe stage demo data bootstrap completed"
