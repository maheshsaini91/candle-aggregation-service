#!/usr/bin/env bash
# Postgres in Docker; app runs on host (mvn spring-boot:run).
set -e
cd "$(dirname "$0")"

if docker compose version &>/dev/null; then
  DOCKER_COMPOSE="docker compose"
else
  DOCKER_COMPOSE="docker-compose"
fi

echo "Starting Postgres in Docker..."
$DOCKER_COMPOSE up -d postgres

echo "Waiting for Postgres to be ready..."
for i in {1..30}; do
  if $DOCKER_COMPOSE exec -T postgres pg_isready -U postgres -d aggregation >/dev/null 2>&1; then
    echo "Postgres is ready."
    break
  fi
  if [[ $i -eq 30 ]]; then
    echo "Postgres did not become ready in time."
    exit 1
  fi
  sleep 1
done

echo "Running app..."
mvn -q spring-boot:run -DskipTests
