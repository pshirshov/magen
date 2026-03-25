#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="$SCRIPT_DIR/target/scala-2.13/magen.jar"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "magen.jar not found. Run ./build.sh first." >&2
  exit 1
fi

# Run from project root so relative mappings/ paths resolve correctly
cd "$SCRIPT_DIR"
exec java -jar "$JAR_PATH" "$@"
