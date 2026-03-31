#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/native"

# Only initialize our app and its dependencies at build time.
# JDK classes (especially AWT) stay at runtime init.
BUILD_INIT="scala,io.septimalmind,io.circe,cats,shapeless,izumi,org.snakeyaml,org.yaml,org.typelevel,jawn,macrocompat,io.github"

echo "=== Building assembly JAR and data zip ==="
sbt -batch assembly packageDataZip

echo ""
echo "=== Building native image ==="
mkdir -p "$BUILD_DIR"
native-image \
  --no-fallback \
  "--initialize-at-build-time=$BUILD_INIT" \
  -H:+ReportExceptionStackTraces \
  -H:+AddAllCharsets \
  -jar "$SCRIPT_DIR/target/scala-2.13/magen.jar" \
  -o "$BUILD_DIR/magen-native"

echo ""
echo "=== Extracting data ==="
mkdir -p "$BUILD_DIR/data"
unzip -o "$SCRIPT_DIR/target/magen-data.zip" -d "$BUILD_DIR/data/"

echo ""
echo "=== Writing runner ==="
cat > "$BUILD_DIR/magen-native.sh" << 'RUNNER'
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export MAGEN_DATA_DIR="${MAGEN_DATA_DIR:-$SCRIPT_DIR/data}"
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in /nix/store/*-graalvm-ce-*/; do
    if [ -d "$candidate" ]; then export JAVA_HOME="$candidate"; break; fi
  done
fi
exec "$SCRIPT_DIR/magen-native" "$@"
RUNNER
chmod +x "$BUILD_DIR/magen-native.sh"

echo ""
echo "=== Done ==="
echo "Run: ./native/magen-native.sh gui"
