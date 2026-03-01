# PostgreSQL access details

Use these when Postgres is running via Docker (`docker compose up -d postgres` or `docker compose up -d`).

## Connection details

| Setting    | Value        |
|-----------|--------------|
| **Host**  | `localhost`  |
| **Port**  | `5432`       |
| **Database** | `aggregation` |
| **Username** | `postgres`   |
| **Password** | `postgres`   |

## JDBC URL (for app / profile db)

```
jdbc:postgresql://localhost:5432/aggregation
```

## Command-line (psql)

From your machine (with Postgres in Docker):

```bash
# If you have psql installed locally
psql -h localhost -p 5432 -U postgres -d aggregation

# Or via Docker (no local psql needed)
docker exec -it aggregationservice-postgres psql -U postgres -d aggregation
```

Password when prompted: `postgres`

## From inside Docker network

When the **app** runs in Docker (same compose), it connects to Postgres using the service name:

- **Host:** `postgres` (not localhost)
- **Port:** `5432`
- **JDBC URL:** `jdbc:postgresql://postgres:5432/aggregation`

Credentials (username/password/database) are the same as above.

## Changing credentials

1. Edit `.env` in the project root (used by Docker).
2. Edit `src/main/resources/application.properties` (datasource section at the bottom).

Keep both in sync so the app can connect.

## Tables

- **candles** — finalized OHLC candles (symbol, interval_sec, bucket_start, open, high, low, close, volume).
- **symbols** — tradable symbols and status (id, symbol, status).

---

## "Connection refused" in DBeaver

This means nothing is listening on `localhost:5432`. **Start PostgreSQL first**, then connect from DBeaver.

### Option 1: Start Postgres with Docker

If Docker is installed:

```bash
cd /path/to/aggregationservice
docker compose up -d postgres
```

Wait a few seconds, then try DBeaver again. Check the container is up: `docker ps` (you should see `aggregationservice-postgres`).

### Option 2: Install and start Postgres locally (Mac with Homebrew)

If you don't use Docker:

```bash
brew install postgresql@15
brew services start postgresql@15
```

Then create the database and user (default macOS Postgres often has no password for local user):

```bash
createuser -s postgres   # if needed
createdb -O postgres aggregation
```

If your local Postgres uses your Mac username instead of `postgres`, in DBeaver use:
- **Username:** your Mac username (e.g. `mahesh`)
- **Password:** leave empty, or the password you set for that user
- **Database:** `aggregation` (create it with `createdb aggregation` if it doesn’t exist)

### Check that something is listening on 5432

```bash
lsof -i :5432
```

If you see a line with `LISTEN`, Postgres is running. Then retry the connection in DBeaver.
