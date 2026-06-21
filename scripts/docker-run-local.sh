#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f .env ]]; then
	echo ".env file is required." >&2
	exit 1
fi

read_env_value() {
	local key="$1"
	awk -v key="$key" '
		/^[[:space:]]*($|#)/ {
			next
		}
		{
			line = $0
			sub(/^[[:space:]]*/, "", line)
			if (index(line, key "=") == 1) {
				sub(/^[^=]*=/, "", line)
				print line
				exit
			}
		}
	' .env
}

container_video_storage_root="/app/storage/videos"
host_video_storage_root="$(read_env_value HOST_VIDEO_STORAGE_ROOT)"
if [[ -z "$host_video_storage_root" ]]; then
	host_video_storage_root="$(read_env_value VIDEO_STORAGE_ROOT)"
fi
docker_db_url="$(read_env_value DOCKER_DB_URL)"
if [[ -z "$docker_db_url" ]]; then
	docker_db_url="$(read_env_value DB_URL)"
fi
if [[ -z "$docker_db_url" ]]; then
	docker_db_url="jdbc:postgresql://localhost:5432/acttub_db"
fi
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
