#!/usr/bin/env bash
# Try to run Postgres + app in Docker. If Docker is not available, print minimal manual steps.
# Minimal requirement: Postgres running + Java 17+ (see README Option B or C).
set -e
cd "$(dirname "$0")"

# Optional: on Mac with Colima, use colima context
command -v docker &>/dev/null && [[ "$(uname)" = "Darwin" ]] && command -v colima &>/dev/null && docker context use colima &>/dev/null || true

if ! docker info &>/dev/null; then
  echo "Docker is not available. Use one of these (minimal: Postgres + Java 17):"
  echo ""
  echo "  Option B — Postgres in Docker, app with Maven:"
  echo "    docker run -d --name postgres -e POSTGRES_DB=aggregation -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:15-alpine"
  echo "    # Then run schema+seed (see docker/postgres/init/) or use: docker compose up -d postgres  (if you have compose)"
  echo "    mvn -q spring-boot:run -DskipTests"
  echo ""
  echo "  Option C — Postgres and Java installed natively: create DB, run schema.sql + data.sql, then:"
  echo "    mvn -q spring-boot:run -DskipTests"
  echo ""
  echo "See README for full steps."
  exit 1
fi

# Optional: fix credential helper (Colima); skip if jq missing
DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker/config.json}"
if [[ -f "$DOCKER_CONFIG" ]] && grep -q 'desktop' "$DOCKER_CONFIG" 2>/dev/null && ! command -v docker-credential-desktop &>/dev/null; then
  if command -v jq &>/dev/null; then
    jq 'del(.credsStore) | del(.credHelpers)' "$DOCKER_CONFIG" > "$DOCKER_CONFIG.tmp" 2>/dev/null && mv "$DOCKER_CONFIG.tmp" "$DOCKER_CONFIG" || true
  fi
fi

if docker compose version &>/dev/null 2>&1; then
  DOCKER_COMPOSE="docker compose"
elif command -v docker-compose &>/dev/null; then
  DOCKER_COMPOSE="docker-compose"
else
  echo "Docker Compose not found. Start Postgres and run the app manually:"
  echo "  docker run -d --name postgres -e POSTGRES_DB=aggregation -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:15-alpine"
  echo "  mvn -q spring-boot:run -DskipTests"
  echo "See README Option B."
  exit 1
fi

[[ ! -f .env ]] && [[ -f .env.example ]] && cp .env.example .env

echo "Building and starting Postgres + app..."
$DOCKER_COMPOSE up --build
