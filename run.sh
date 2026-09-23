#!/usr/bin/env bash
set -a
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"
if [ -f "$DIR/.env" ]; then
    source "$DIR/.env"
fi
set +a

# Free port 8080 if already in use
PORT_PID=$(lsof -t -i:8080 2>/dev/null || true)
if [ -n "$PORT_PID" ]; then
    echo "Stopping existing process on port 8080 (PID $PORT_PID)..."
    kill -9 $PORT_PID 2>/dev/null || true
    sleep 1
fi

echo "Starting MessQ Backend connected to Neon PostgreSQL with demo-data profile..."
exec /Users/amandeepkumar/Projects/secureexam-ai-complete/apache-maven-3.9.6/bin/mvn spring-boot:run -Dspring-boot.run.profiles=demo-data
