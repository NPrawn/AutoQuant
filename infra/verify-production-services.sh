#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo 'Usage: bash infra/verify-production-services.sh <compose-file> <env-file>' >&2
    exit 2
fi

compose_file="$1"
env_file="$2"

for command in docker curl grep seq; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Required command is missing: $command" >&2
        exit 1
    fi
done

for attempt in $(seq 1 12); do
    frontend_status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:3000 || true)
    backend_status=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:18080 || true)

    if [ "$frontend_status" = "200" ] \
        && { [ "$backend_status" = "200" ] || [ "$backend_status" = "401" ]; }; then
        break
    fi

    if [ "$attempt" = "12" ]; then
        echo "HTTP smoke test failed: frontend=$frontend_status backend=$backend_status" >&2
        exit 1
    fi

    sleep 5
done

for container in \
    a107-postgres \
    a107-redis \
    a107-kafka \
    a107-rabbitmq \
    a107-backend-api \
    a107-ai \
    a107-frontend \
    a107-ingest-producer \
    a107-ingest-consumer \
    a107-ingest-backfill; do
    state=$(docker inspect --format '{{.State.Status}}' "$container")

    if [ "$state" != "running" ]; then
        echo "$container is not running: $state" >&2
        exit 1
    fi
done

for container in a107-postgres a107-redis a107-kafka a107-rabbitmq a107-ai a107-frontend; do
    for attempt in $(seq 1 12); do
        health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}missing{{end}}' "$container")

        if [ "$health" = "healthy" ]; then
            break
        fi

        if [ "$attempt" = "12" ]; then
            echo "$container is not healthy: $health" >&2
            exit 1
        fi

        sleep 5
    done
done

kafka_init_state=$(docker inspect --format '{{.State.Status}}:{{.State.ExitCode}}' a107-kafka-init)
if [ "$kafka_init_state" != "exited:0" ]; then
    echo "a107-kafka-init did not complete successfully: $kafka_init_state" >&2
    exit 1
fi

worker_count=$(docker compose --env-file "$env_file" -f "$compose_file" ps --status running --services \
    | grep -cx 'backtest-worker' || true)

if [ "$worker_count" -lt 1 ]; then
    echo 'backtest-worker is not running' >&2
    exit 1
fi

echo 'Production service verification passed.'
