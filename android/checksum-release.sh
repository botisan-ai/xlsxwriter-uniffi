#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -eq 0 ]]; then
  echo "Usage: $0 <release-artifact> [...]" >&2
  exit 2
fi

for artifact in "$@"; do
  checksum_file="${artifact}.sha256"

  if command -v sha256sum >/dev/null 2>&1; then
    checksum=$(sha256sum "$artifact" | awk '{ print $1 }')
  else
    checksum=$(shasum -a 256 "$artifact" | awk '{ print $1 }')
  fi

  printf '%s  %s\n' "$checksum" "$(basename "$artifact")" > "$checksum_file"
done
