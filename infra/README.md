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
