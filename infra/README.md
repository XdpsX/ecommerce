# Local infrastructure

Docker Compose provisions PostgreSQL 18.6 and Redis 8.6.8. Run commands from the repository root. Copy `.env.example` to `.env` and change both passwords. Compose reads `.env` with `--env-file .env`; Spring Boot processes started from an IDE do not automatically read it.

## Dev — infrastructure only

Run the databases in Docker and applications in the IDE:

```bash
docker compose --env-file .env -f infra/compose/compose.yml config
docker compose --env-file .env -f infra/compose/compose.yml up -d
docker compose --env-file .env -f infra/compose/compose.yml ps
```

## Local — containerized applications

`compose.local.yml` includes `compose.yml`. It currently starts the same infrastructure; application services will be added when images are ready.

```bash
docker compose --env-file .env -f infra/compose/compose.local.yml config
docker compose --env-file .env -f infra/compose/compose.local.yml up -d
```

Use only **one** `-f` file for either mode. Both use Compose project name `ecommerce`, sharing infrastructure containers and named volumes. The `include` feature requires Docker Compose v2.20.0+.

## Connections

| Service | Inside Docker network | Developer host | Credentials |
| --- | --- | --- | --- |
| PostgreSQL | `postgres:5432` | `127.0.0.1:${POSTGRES_PORT:-5432}` | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| Redis | `redis:6379` | `127.0.0.1:${REDIS_PORT:-6379}` | `REDIS_PASSWORD` |

Both host ports bind to loopback. Containers on the backend network use service names rather than `localhost`.

## Identity database

Identity uses a separate database on the same local PostgreSQL server, owned by the existing `POSTGRES_USER` role. It does not need a separate login role. On a fresh PostgreSQL volume, Compose runs `init.sql` to create the database if missing. Existing volumes do not rerun initialization scripts, so run the mounted script manually when setting up an existing volume; if `identity` already exists but is owned by another role, also change its owner:

```powershell
docker compose --env-file .env -f infra/compose/compose.yml up -d postgres
docker compose --env-file .env -f infra/compose/compose.yml exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/10-init.sql'
docker compose --env-file .env -f infra/compose/compose.yml exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "ALTER DATABASE identity OWNER TO $POSTGRES_USER"'
```

Set `IDENTITY_DB_URL=jdbc:postgresql://localhost:5432/identity`, `IDENTITY_DB_USERNAME` to the same value as `POSTGRES_USER`, and `IDENTITY_DB_PASSWORD` to the same value as `POSTGRES_PASSWORD`. The example values are `ecommerce` for both username and password. When running from the repository root, the Identity `local` Spring profile imports root `.env`; Spring Boot otherwise does not load it automatically. Replace the local passwords and supply a valid local signing-key file before starting the authorization server. Never use these local values in a shared environment.

## Readiness and operations

Use the base file below for dev; substitute `compose.local.yml` for local mode.

```bash
docker compose --env-file .env -f infra/compose/compose.yml exec postgres sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose --env-file .env -f infra/compose/compose.yml exec redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping'
docker compose --env-file .env -f infra/compose/compose.yml logs -f
docker compose --env-file .env -f infra/compose/compose.yml down
```

Redis should respond `PONG`. Health checks verify basic readiness, not application integration. Named volumes persist across restarts and `down`; **`down -v` deletes the project-managed volumes and local data**. PostgreSQL 18 mounts `/var/lib/postgresql`; PostgreSQL 17 data requires a supported upgrade, not direct reuse.

## Security

Use strong local passwords. Loopback binding limits exposure but does not replace authentication. Redis password authentication does not encrypt traffic. Do not expose these databases to untrusted networks. Production TLS, secret management and backups are outside this local setup.
