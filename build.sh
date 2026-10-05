#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
STARSECTOR_API="${STARSECTOR_API:?Set STARSECTOR_API to exact RC8 starfarer.api.jar}"
LOG4J_JAR="${LOG4J_JAR:?Set LOG4J_JAR to exact RC8 log4j-1.2.9.jar}"
JSON_JAR="${JSON_JAR:?Set JSON_JAR to exact RC8 json.jar}"
LUNALIB_JAR="${LUNALIB_JAR:?Set LUNALIB_JAR to exact LunaLib 2.0.5 LunaLib.jar}"
rm -rf "$ROOT/build"
mkdir -p "$ROOT/build/classes" "$ROOT/jars"
find "$ROOT/src" -name '*.java' -print | sort > "$ROOT/build/sources.txt"
javac --release 8 -Xlint:-options -cp "$STARSECTOR_API:$LOG4J_JAR:$JSON_JAR:$LUNALIB_JAR" -d "$ROOT/build/classes" @"$ROOT/build/sources.txt"
jar --create --file "$ROOT/jars/UniversalMissileRegeneration.jar" --date=2000-01-01T00:00:00Z -C "$ROOT/build/classes" .
