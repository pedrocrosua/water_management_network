#!/usr/bin/env bash
set -euo pipefail

if [[ $# == 1 && ( "$1" == --help || "$1" == -h ) ]]; then
    printf 'Usage: %s\nBuild all application images and start Kafka, Central and both monitors.\n' "$0"
    exit 0
fi
if [[ $# != 0 ]]; then
    printf 'Usage: %s\n' "$0" >&2
    exit 1
fi

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

docker compose -f "$project_dir/docker-compose.yml" build
docker compose -f "$project_dir/docker-compose.yml" up -d --wait --wait-timeout 180 \
    kafka wm-central ws1-monitor ws2-monitor

printf '\nPanel: http://localhost:8080\n'
printf 'Run each console in a separate terminal:\n'
printf '  ./scripts/local-console.sh ws1-engine\n'
printf '  ./scripts/local-console.sh ws2-engine\n'
printf '  ./scripts/local-console.sh fo1\n'
printf '  ./scripts/local-console.sh fo2\n'
