#!/usr/bin/env bash
set -euo pipefail

usage="Usage: $0 OPERATOR_ID"

if [[ $# == 1 && ( "$1" == --help || "$1" == -h ) ]]; then
    printf '%s\n' "$usage"
    printf 'Run one Field Operator (WM_FO) in this terminal, e.g. %s FO-001.\n' "$0"
    printf 'Kafka address is read from lab.env.\n'
    exit 0
fi
if [[ $# != 1 ]]; then
    printf '%s\n' "$usage" >&2
    exit 1
fi
operator_id="$1"
if [[ ! "$operator_id" =~ ^[A-Za-z0-9-]+$ ]]; then
    printf 'Invalid operator id: %s. Use only letters, digits and dashes, e.g. FO-001.\n' "$operator_id" >&2
    exit 1
fi

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

# Un proyecto de Compose por operario, así pueden ejecutarse varios en el mismo PC.
compose=(docker compose -p "wm-${operator_id,,}" -f "$project_dir/docker-compose.lab-fo.yml")
if [[ -f "$project_dir/lab.env" ]]; then
    compose+=(--env-file "$project_dir/lab.env")
fi

export OPERATOR_ID="$operator_id"
container="WM_FO_$operator_id"

if docker container inspect "$container" >/dev/null 2>&1; then
    printf '%s already exists. If it is not running in another terminal, remove it with:\n' "$container" >&2
    printf '  docker rm -f %s\n' "$container" >&2
    exit 1
fi

# Cerrar la terminal tiene que parar la aplicación igual que Ctrl+C (caída simulada):
# sin esto, su contenedor seguiría ejecutándose en segundo plano.
stop_container() {
    docker rm -f "$container" >/dev/null 2>&1 || true
}
trap stop_container EXIT
trap 'exit 129' HUP

"${compose[@]}" build operator
"${compose[@]}" run --rm --no-deps --name "$container" operator
