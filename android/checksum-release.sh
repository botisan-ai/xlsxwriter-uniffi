#!/usr/bin/env bash

set -euo pipefail

if [[ "$#" -ne 1 ]]; then
  echo "Usage: $0 <release-zip>" >&2
  exit 2
fi

archive="$1"
checksum_file="${archive}.sha256"

if command -v sha256sum >/dev/null 2>&1; then
  checksum=$(sha256sum "$archive" | awk '{ print $1 }')
else
  checksum=$(shasum -a 256 "$archive" | awk '{ print $1 }')
fi

printf '%s  %s\n' "$checksum" "$(basename "$archive")" > "$checksum_file"
