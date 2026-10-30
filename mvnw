#!/usr/bin/env bash
# Maven wrapper redirecting to local maven
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
MVN_BIN="$DIR/.maven/bin/mvn"

if [ -f "$MVN_BIN" ]; then
    exec "$MVN_BIN" "$@"
elif command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
else
    echo "Error: Neither local Maven ($MVN_BIN) nor system 'mvn' was found on PATH."
    echo "Please install Maven (e.g. 'brew install maven' or 'apt install maven')."
    exit 1
fi

