#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f .env ]]; then
	echo ".env file is required." >&2
	exit 1
fi

set -a
source .env
set +a

container_video_storage_root="/app/storage/videos"
host_video_storage_root="${HOST_VIDEO_STORAGE_ROOT:-${VIDEO_STORAGE_ROOT:-}}"
docker_db_url="${DOCKER_DB_URL:-${DB_URL:-jdbc:postgresql://localhost:5432/acttub_db}}"
docker_db_url="${docker_db_url/localhost/host.docker.internal}"
docker_db_url="${docker_db_url/127.0.0.1/host.docker.internal}"

if [[ -z "$host_video_storage_root" ]]; then
	echo "VIDEO_STORAGE_ROOT or HOST_VIDEO_STORAGE_ROOT is required in .env." >&2
	exit 1
fi

docker stop acttub-backend >/dev/null 2>&1 || true

docker run --rm -d --name acttub-backend \
	--add-host=host.docker.internal:host-gateway \
	-p 8080:8080 \
	--env-file .env \
	-e SPRING_PROFILES_ACTIVE=local \
	-e DB_URL="$docker_db_url" \
	-e VIDEO_STORAGE_ROOT="$container_video_storage_root" \
	-v "$host_video_storage_root:$container_video_storage_root" \
	acttub-backend
