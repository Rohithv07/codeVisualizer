#!/usr/bin/env bash
# Maven wrapper redirecting to local maven
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
MVN_BIN="$DIR/.maven/bin/mvn"

if [ ! -f "$MVN_BIN" ]; then
    echo "Error: Local Maven not found at $MVN_BIN"
    exit 1
fi

exec "$MVN_BIN" "$@"

