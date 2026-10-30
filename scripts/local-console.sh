#!/usr/bin/env bash
set -euo pipefail

if [[ $# == 1 && ( "$1" == --help || "$1" == -h ) ]]; then
    printf 'Usage: %s {ws1-engine|ws2-engine|fo1|fo2}\nRun one interactive console after local-up.sh.\n' "$0"
    exit 0
fi
if [[ $# != 1 ]]; then
    printf 'Usage: %s {ws1-engine|ws2-engine|fo1|fo2}\n' "$0" >&2
    exit 1
fi
case "$1" in
    ws1-engine|ws2-engine|fo1|fo2) ;;
    *)
        printf 'Unknown console: %s. Expected ws1-engine, ws2-engine, fo1 or fo2.\n' "$1" >&2
        exit 1
        ;;
esac

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

exec docker compose -f "$project_dir/docker-compose.yml" run --rm --no-deps "$1"
