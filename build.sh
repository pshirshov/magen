#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building magen.jar..."
sbt assembly

JAR_PATH="$SCRIPT_DIR/target/scala-2.13/magen.jar"
if [[ ! -f "$JAR_PATH" ]]; then
  echo "ERROR: Build failed, $JAR_PATH not found" >&2
  exit 1
fi

echo "Built: $JAR_PATH"
