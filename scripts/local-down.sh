#!/usr/bin/env bash
set -euo pipefail

if [[ $# == 1 && ( "$1" == --help || "$1" == -h ) ]]; then
    printf 'Usage: %s\nStop local services after closing the consoles. Keep the SQLite volume.\n' "$0"
    exit 0
fi
if [[ $# != 0 ]]; then
    printf 'Usage: %s\n' "$0" >&2
    exit 1
fi

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

exec docker compose -f "$project_dir/docker-compose.yml" down
