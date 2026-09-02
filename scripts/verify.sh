#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"$project_root/scripts/preflight.sh"

(
  cd "$project_root/backend"
  mvn --batch-mode verify
)

(
  cd "$project_root/frontend"
  npm ci
  npm run check
)

echo "All verification checks passed."
