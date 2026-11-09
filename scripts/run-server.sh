#!/usr/bin/env bash
# Builds (if needed) and starts the REST service.
#   ./scripts/run-server.sh            uses server/.env if present, else the current environment
#   LLM_PROVIDER=mock API_PASSWORD=pw ./scripts/run-server.sh     offline demo, no API key needed
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root/server"

case "$root" in
  *:*) echo "The project path contains a ':' which breaks Java classpaths. Move the folder and retry." >&2; exit 1 ;;
esac

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

jar=target/darija-translator-server.jar
if [ ! -f "$jar" ] || [ -n "$(find src pom.xml -newer "$jar" -print -quit)" ]; then
  echo "Building..." >&2
  mvn -q -B -DskipTests package
fi

exec java -jar "$jar"
