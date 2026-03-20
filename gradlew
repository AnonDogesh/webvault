#!/usr/bin/env bash
set -euo pipefail

if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi

echo "Gradle executable not found. Install Gradle or add wrapper files." >&2
exit 1
