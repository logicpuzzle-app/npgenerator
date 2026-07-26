#!/bin/sh
set -eu

cd "$(dirname "$0")"

if [ ! -x target/release/npgen ]; then
    cargo build --release
fi

exec target/release/npgen "$@"

