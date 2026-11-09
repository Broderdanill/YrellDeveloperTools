#!/usr/bin/env bash
# BMC Developer Studio 26.1.1 (build001) plugin build, Java 17 bytecode.
# Usage: ./scripts/build.sh /path/to/componentDeveloperStudioWindows.zip
#    or: ./scripts/build.sh /path/to/DeveloperStudio/x64
# No installed BMC libraries are copied into the finished plugin JAR.
set -o pipefail
set -u
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
PLUGIN="$ROOT/source/se.yrell.developertools"
OUT="$ROOT/install/se.yrell.developertools_0.1.57.jar"
BUILD="$ROOT/.build"
ORIGINAL="${1:-}"
if [[ -z "${ORIGINAL:-}" || ! -e "$ORIGINAL" ]]; then
  echo "Usage: $0 /path/to/componentDeveloperStudioWindows.zip (or x64 directory)" >&2
  exit 2
fi
if [[ -f "$ORIGINAL" ]]; then
  STUDIO="$BUILD/devstudio/x64/plugins"
  mkdir -p "$STUDIO"
  if ! find "$STUDIO" -maxdepth 1 -name 'com.bmc.arsys.studio.ui_*.jar' -print -quit | grep -q .; then
    echo 'Extracting Developer Studio plugin dependencies from archive...'
    unzip -q "$ORIGINAL" 'x64/plugins/*' -d "$BUILD/devstudio" || exit $?
  fi
elif [[ -d "$ORIGINAL/plugins" ]]; then
  STUDIO="$ORIGINAL/plugins"
elif [[ -d "$ORIGINAL/x64/plugins" ]]; then
  STUDIO="$ORIGINAL/x64/plugins"
else
  echo "Expected ZIP or directory containing x64/plugins: $ORIGINAL" >&2
  exit 2
fi
if ! find "$STUDIO" -maxdepth 1 -name 'com.bmc.arsys.studio.ui_26.1.1.build001.jar' -print -quit | grep -q .; then
  echo 'WARNING: Developer Studio 26.1.1 build001 library was not found. Compatibility may differ.' >&2
fi
ASM=$(find "$STUDIO" -maxdepth 1 -name 'org.objectweb.asm_*.jar' -print -quit)
ASM_COMMONS=$(find "$STUDIO" -maxdepth 1 -name 'org.objectweb.asm.commons_*.jar' -print -quit)
if [[ -z "$ASM" || -z "$ASM_COMMONS" ]]; then
  echo 'Required ASM core/commons jars are missing from Developer Studio.' >&2
  exit 2
fi
rm -rf "$BUILD/classes"
mkdir -p "$BUILD/classes" "$ROOT/install"
find "$PLUGIN/src" -type f -name '*.java' | sort > "$BUILD/sources.txt"
CLASSPATH=$(find "$STUDIO" -type f -name '*.jar' | sort | paste -sd: -)
echo "Compiling $(wc -l < "$BUILD/sources.txt") source files (Java 17 bytecode)..."
if ! javac -proc:none --release 17 -encoding UTF-8 -cp "$CLASSPATH" -d "$BUILD/classes" @"$BUILD/sources.txt"; then
  echo 'Compilation failed; no replacement JAR created.' >&2
  exit 1
fi
STAGE="$BUILD/jar-stage"
rm -rf "$STAGE"
mkdir -p "$STAGE"
cp -a "$BUILD/classes"/. "$STAGE"/ || exit $?
cp "$PLUGIN/plugin.xml" "$PLUGIN/README.md" "$STAGE"/ || exit $?
cp -a "$PLUGIN/icons" "$STAGE/" || exit $?
# The -javaagent entry point uses ASM before Eclipse/OSGi loads. Embed only
# ASM 9.7 core and commons classes; never package BMC runtime dependencies.
unzip -qq "$ASM" 'org/objectweb/asm/*.class' -d "$STAGE" || exit $?
unzip -qq "$ASM_COMMONS" 'org/objectweb/asm/commons/*.class' -d "$STAGE" || exit $?
jar --create --file "$OUT" --manifest "$PLUGIN/META-INF/MANIFEST.MF" -C "$STAGE" . || exit $?
echo "Created: $OUT"
jar --describe-module --file "$OUT" >/dev/null 2>&1 || true
