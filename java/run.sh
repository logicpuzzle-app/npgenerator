#!/bin/sh
set -eu

cd "$(dirname "$0")"

if [ ! -f build/classes/jp/gr/puzzle/npgen2007/NpGen.class ]; then
    ./build.sh
fi

if [ -x /opt/homebrew/opt/openjdk@17/bin/java ]; then
    JAVA=/opt/homebrew/opt/openjdk@17/bin/java
else
    JAVA=$(command -v java)
fi

exec "$JAVA" -cp build/classes jp.gr.puzzle.npgen2007.NpGen "$@"
