#!/usr/bin/env bash
set -euo pipefail

usage="Usage: $0 monitor STATION_ID LOCATION
       $0 engine STATION_ID"

if [[ $# == 1 && ( "$1" == --help || "$1" == -h ) ]]; then
    printf '%s\n' "$usage"
    printf 'Run the Monitor or the Engine of one Watering Station in this terminal.\n'
    printf 'Each station needs two terminals: first its monitor, then its engine.\n'
    printf 'Central and Kafka addresses are read from lab.env.\n'
    exit 0
fi

role="${1:-}"
station_id="${2:-}"
case "$role" in
    monitor) expected_args=3 ;;
    engine)  expected_args=2 ;;
    *)       expected_args=0 ;;
esac
if [[ $expected_args == 0 || $# != "$expected_args" ]]; then
    printf '%s\n' "$usage" >&2
    exit 1
fi
if [[ ! "$station_id" =~ ^[A-Za-z0-9-]+$ ]]; then
    printf 'Invalid station id: %s. Use only letters, digits and dashes, e.g. WS-01.\n' "$station_id" >&2
    exit 1
fi

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

# Un proyecto de Compose por estación: su monitor y su engine comparten una red privada.
compose=(docker compose -p "wm-${station_id,,}" -f "$project_dir/docker-compose.lab-ws.yml")
if [[ -f "$project_dir/lab.env" ]]; then
    compose+=(--env-file "$project_dir/lab.env")
fi

export STATION_ID="$station_id"
export STATION_LOCATION="${3:-}"

if [[ $role == monitor ]]; then
    container="WM_WS_M_$station_id"
    run_options=(--no-TTY --use-aliases)   # el engine lo encuentra por el nombre "monitor"
else
    container="WM_WS_E_$station_id"
    run_options=()
fi

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

"${compose[@]}" build "$role"
"${compose[@]}" run --rm --no-deps --name "$container" "${run_options[@]}" "$role"
