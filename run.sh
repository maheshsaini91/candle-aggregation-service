#!/usr/bin/env bash
# Build and run Postgres + app in Docker. No Java/Maven needed on host.
# Prereqs: Docker + Docker Compose. On Mac use Colima (works on all macOS versions).
set -e
cd "$(dirname "$0")"

# On Mac with Colima: use colima context so docker talks to Colima (fixes "already running" but docker not ready)
if [[ "$(uname)" = "Darwin" ]] && command -v colima &>/dev/null; then
  docker context use colima &>/dev/null || true
fi

if ! docker info &>/dev/null; then
  if [[ "$(uname)" = "Darwin" ]] && command -v colima &>/dev/null; then
    echo "Docker daemon not reachable. Restarting Colima..."
    colima stop 2>/dev/null || true
    colima start
    docker context use colima &>/dev/null || true
    echo "Waiting for Docker to be ready..."
    for i in {1..30}; do
      if docker info &>/dev/null; then
        echo "Docker is ready."
        break
      fi
      if [[ $i -eq 30 ]]; then
        echo "Docker did not become ready. Run: colima stop && colima start"
        exit 1
      fi
      sleep 2
    done
  else
    echo "Docker is not running."
    echo ""
    echo "On Mac:  brew install colima docker docker-compose jq && colima start"
    echo "Then run ./run.sh again."
    exit 1
  fi
fi

# Use "docker compose" (V2) or "docker-compose" (V1)
if docker compose version &>/dev/null 2>&1; then
  DOCKER_COMPOSE="docker compose"
elif command -v docker-compose &>/dev/null; then
  DOCKER_COMPOSE="docker-compose"
else
  echo "Docker Compose is not available. Install:  brew install docker-compose"
  exit 1
fi

# Fix Docker credential helper when using Colima (no Docker Desktop)
DOCKER_CONFIG="${DOCKER_CONFIG:-$HOME/.docker/config.json}"
if [[ -f "$DOCKER_CONFIG" ]]; then
  if grep -q 'desktop' "$DOCKER_CONFIG" 2>/dev/null && ! command -v docker-credential-desktop &>/dev/null; then
    if command -v jq &>/dev/null; then
      echo "Fixing Docker config for Colima..."
      jq 'del(.credsStore) | del(.credHelpers)' "$DOCKER_CONFIG" > "$DOCKER_CONFIG.tmp" && mv "$DOCKER_CONFIG.tmp" "$DOCKER_CONFIG"
    else
      echo "Install jq for auto-fix:  brew install jq"
      echo "Or edit $DOCKER_CONFIG and remove the \"credsStore\" line."
      exit 1
    fi
  fi
fi

# Ensure .env exists (docker-compose needs it)
if [[ ! -f .env ]]; then
  if [[ -f .env.example ]]; then
    cp .env.example .env
    echo "Created .env from .env.example"
  fi
fi

echo "Building and starting Postgres + app (all in Docker)..."
$DOCKER_COMPOSE up --build
