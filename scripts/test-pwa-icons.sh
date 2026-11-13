#!/usr/bin/env bash
set -o pipefail
set -u
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/install/se.yrell.developertools_0.1.69.jar"
TEST_DIR="$ROOT/.build/pwa-icon-tests"
if [[ ! -f "$JAR" ]]; then echo "Missing $JAR" >&2; exit 2; fi
mkdir -p "$TEST_DIR"
javac --release 17 -proc:none -encoding UTF-8 -cp "$JAR" -d "$TEST_DIR" \
  "$ROOT/tests/stubs/se/yrell/developertools/Log.java" \
  "$ROOT/tests/PwaIconCatalogRegressionTest.java" \
  "$ROOT/tests/IconTlsSupportRegressionTest.java" || exit $?
java -Xverify:all -cp "$TEST_DIR:$JAR" PwaIconCatalogRegressionTest || exit $?
java -Xverify:all -cp "$TEST_DIR:$JAR" se.yrell.developertools.icons.IconTlsSupportRegressionTest
