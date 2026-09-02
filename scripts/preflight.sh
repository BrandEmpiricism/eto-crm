#!/usr/bin/env bash
set -euo pipefail

failure=0

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1"
    failure=1
  fi
}

require_command java
require_command mvn
require_command node
require_command npm
require_command git

if command -v java >/dev/null 2>&1; then
  java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
  if [[ -z "$java_major" || "$java_major" -lt 21 ]]; then
    echo "Java 21+ required; found ${java_major:-unknown}."
    failure=1
  fi
fi

if [[ "$failure" -ne 0 ]]; then
  exit 1
fi

echo "Preflight checks passed."

