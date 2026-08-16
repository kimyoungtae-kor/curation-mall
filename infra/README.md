# Local infrastructure

## PostgreSQL

From the repository root, copy `.env.example` to `.env` and replace the local-only password. Then run:

```powershell
docker compose --env-file .env -f infra/compose.yaml up -d postgres
docker compose --env-file .env -f infra/compose.yaml ps
```

Stop the container without deleting its data volume:

```powershell
docker compose --env-file .env -f infra/compose.yaml stop postgres
```

Do not use `docker compose down -v` because it deletes the local PostgreSQL volume.

