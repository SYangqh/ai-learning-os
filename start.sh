#!/bin/bash
# Start AI Learning OS (PostgreSQL + Redis + Spring Boot backend + Next.js frontend)

set -e

echo "Starting AI Learning OS..."
ROOT=$(cd "$(dirname "$0")" && pwd)

# ── Step 1: Infrastructure (PostgreSQL + Redis) ───────────────
echo "[1/3] Starting PostgreSQL + Redis via Docker Compose..."
docker compose up -d
echo "[1/3] Waiting 5s for DB to be ready..."
sleep 5

# ── Step 2: Spring Boot backend ───────────────────────────────
cd "$ROOT/backend-spring"
echo "[2/3] Starting Spring Boot on :8080..."
./mvnw spring-boot:run -q &
BACKEND_PID=$!
echo "[2/3] Backend PID $BACKEND_PID  → http://localhost:8080"

# ── Step 3: Next.js frontend ──────────────────────────────────
cd "$ROOT/frontend"
if [ ! -d node_modules ]; then
  echo "[3/3] Installing npm packages..."
  npm install -s
fi
npm run dev &
FRONTEND_PID=$!
echo "[3/3] Frontend PID $FRONTEND_PID → http://localhost:3000"

echo ""
echo "  App:     http://localhost:3000"
echo "  Swagger: http://localhost:8080/swagger-ui.html"
echo ""
echo "Press Ctrl+C to stop. To stop infra: docker compose down"

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; docker compose down" INT TERM
wait

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null" INT TERM
wait
