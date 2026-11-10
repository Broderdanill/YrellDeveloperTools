#!/usr/bin/env bash
# Usage: ./scripts/test.sh /path/to/DeveloperStudio/x64
# Test also accepts the original Studio ZIP if build.sh has extracted its libraries.
set -o pipefail
set -u
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
STUDIO="${1:-$ROOT/.build/devstudio/x64/plugins}"
if [[ -f "$STUDIO" ]]; then
  "$ROOT/scripts/build.sh" "$STUDIO" || exit $?
  STUDIO="$ROOT/.build/devstudio/x64/plugins"
elif [[ -d "$STUDIO/x64/plugins" ]]; then
  STUDIO="$STUDIO/x64/plugins"
elif [[ -d "$STUDIO/plugins" ]]; then
  STUDIO="$STUDIO/plugins"
fi
JAR="$ROOT/install/se.yrell.developertools_0.1.66.jar"
if [[ ! -f "$JAR" ]]; then echo "Build JAR first: $JAR" >&2; exit 2; fi
mkdir -p "$ROOT/.build/tests"
CLASSPATH=$(find "$STUDIO" -type f -name '*.jar' | sort | paste -sd: -)
CP="$JAR:$ROOT/.build/tests:$CLASSPATH"
if ! javac -proc:none --release 17 -encoding UTF-8 -cp "$CP" -d "$ROOT/.build/tests" "$ROOT/tests/"*.java; then exit 1; fi
for test in AutoFieldIdRegressionTest DefaultNamingRegressionTest FastFormsStateRegressionTest TransformAudit MidTierCacheRegressionTest MidTierSsoRegressionTest MidTierConfigAuthorizationRegressionTest MidTierCommandRegressionTest MidTierObjectListRegressionTest ObjectListSortingRegressionTest ManualSuffixCleanupRegressionTest MessageSuppressionRegressionTest; do
  echo "== $test =="
  if [[ "$test" == "MidTierCacheRegressionTest" ]]; then
    java -cp "$CP" "$test" || exit 1
  elif [[ "$test" == "MidTierCommandRegressionTest" || "$test" == "MidTierObjectListRegressionTest" ]]; then
    java -cp "$CP" "$test" "$ROOT/source/se.yrell.developertools/plugin.xml" || exit 1
  else
    java -cp "$CP" "$test" || exit 1
  fi
done
# Optional JS DOM simulation: verifies our embedded browser code in Node.js.
if command -v node >/dev/null 2>&1; then
  echo '== MidTierBrowserDomRegressionTest =='
  java -cp "$CP" MidTierBrowserJsDump > "$ROOT/.build/tests/midtier_browser_scripts.txt" || exit 1
  node "$ROOT/tests/MidTierBrowserDomRegressionTest.cjs" "$ROOT/.build/tests/midtier_browser_scripts.txt" || exit 1
else
  echo 'SKIP optional SWT Browser JS DOM simulation (Node.js not installed).'
fi
# Check a real JVM starts with this JAR configured as -javaagent, not only
# that the Agent-Class metadata happens to exist.
echo '== agent bootstrap =='
java -javaagent:"$JAR" -Dbmc.ds.fastForms.enabled=false -cp "$CP" FastFormsStateRegressionTest || exit 1
echo '== early agent message hook =='
java -javaagent:"$JAR" -Dbmc.ds.fastForms.enabled=false -Dyrell.verifyAgentInfoHook=true -cp "$CP" MessageSuppressionRegressionTest || exit 1
echo '== save-warning UI class verification (real BMC 26.1.1, -javaagent, -Xverify:all) =='
java -Xverify:all -javaagent:"$JAR" -Dbmc.ds.fastForms.enabled=false -cp "$CP" AgentMessageClassloadRegressionTest || exit 1
if ! javap -classpath "$JAR" -verbose se.yrell.developertools.ToolsActivator | grep -q 'major version: 61'; then
  echo 'Wrong Java bytecode version (expected Java 17 / major 61).' >&2
  exit 1
fi
echo 'ALL TESTS PASSED'
