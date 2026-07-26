#!/bin/sh
set -eu

cd "$(dirname "$0")"

if [ -x /opt/homebrew/opt/openjdk@17/bin/javac ]; then
    JAVAC=/opt/homebrew/opt/openjdk@17/bin/javac
else
    JAVAC=$(command -v javac)
fi

rm -rf build/classes
mkdir -p build/classes
find src -name '*.java' -print | sort > build/sources.txt
"$JAVAC" -encoding UTF-8 -d build/classes @build/sources.txt
