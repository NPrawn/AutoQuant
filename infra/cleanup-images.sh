#!/usr/bin/env bash

set -u

KEEP_COUNT=3
APPLY=false

usage() {
    cat <<'EOF'
Usage: bash infra/cleanup-images.sh [--keep COUNT] [--apply]

Without --apply, the script only prints images that would be removed.
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --keep)
            if [ "$#" -lt 2 ] || ! [[ "$2" =~ ^[1-9][0-9]*$ ]]; then
                echo '--keep requires a positive integer.' >&2
                exit 2
            fi
            KEEP_COUNT="$2"
            shift 2
            ;;
        --apply)
            APPLY=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if ! command -v docker >/dev/null 2>&1; then
    echo 'Docker is required.' >&2
    exit 1
fi

protected_ids=$(mktemp)
trap 'rm -f "$protected_ids"' EXIT

# Keep every image currently used by a running container, including rolled-back versions.
docker ps -q \
    | xargs -r docker inspect --format '{{.Image}}' \
    | sort -u > "$protected_ids"

repositories=(
    a107/backend-api
    a107/ingest
    a107/ai
    a107/frontend
)

removed=0
skipped=0

for repository in "${repositories[@]}"; do
    mapfile -t tags < <(
        docker image ls "$repository" --format '{{.Tag}}' \
            | awk '$0 != "<none>"' \
            | sort -u
    )

    if [ "${#tags[@]}" -eq 0 ]; then
        echo "No tagged images found: $repository"
        continue
    fi

    # docker image ls returns images newest first by default.
    mapfile -t keep_tags < <(
        docker image ls "$repository" --format '{{.Tag}}' \
            | awk '$0 != "<none>"' \
            | awk '!seen[$0]++' \
            | head -n "$KEEP_COUNT"
    )

    for tag in "${tags[@]}"; do
        keep=false
        for keep_tag in "${keep_tags[@]}"; do
            if [ "$tag" = "$keep_tag" ]; then
                keep=true
                break
            fi
        done

        image="${repository}:${tag}"
        image_id=$(docker image inspect --format '{{.Id}}' "$image")

        if [ "$keep" = true ]; then
            echo "Keep recent image: $image"
            continue
        fi

        if grep -Fxq "$image_id" "$protected_ids"; then
            echo "Keep running image: $image"
            skipped=$((skipped + 1))
            continue
        fi

        if [ "$APPLY" = true ]; then
            if docker image rm "$image"; then
                removed=$((removed + 1))
            else
                echo "Could not remove image: $image" >&2
            fi
        else
            echo "Would remove image: $image"
            removed=$((removed + 1))
        fi
    done
done

if [ "$APPLY" = true ]; then
    docker image prune -f
fi

echo "Image cleanup complete: candidates=$removed protected=$skipped apply=$APPLY keep=$KEEP_COUNT"
docker system df
