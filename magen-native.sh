#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NATIVE_DIR="$SCRIPT_DIR/native"
export MAGEN_DATA_DIR="${MAGEN_DATA_DIR:-$NATIVE_DIR/data}"

if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in /nix/store/*-graalvm-ce-*/; do
    if [ -d "$candidate" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

exec "$NATIVE_DIR/magen-native" "$@"
