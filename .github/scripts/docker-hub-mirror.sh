#!/usr/bin/env bash
# Pull Docker Hub images through Google's public cache (mirror.gcr.io) first. GitHub runners share
# IP addresses and hit Docker Hub's anonymous pull limit (429 Too Many Requests); Docker falls back
# to Docker Hub by itself when the cache does not have an image.
set -euo pipefail
config=/etc/docker/daemon.json
current='{}'
if sudo test -s "$config"; then
  current=$(sudo cat "$config")
fi
echo "$current" | jq '. + {"registry-mirrors": ["https://mirror.gcr.io"]}' | sudo tee "$config" > /dev/null
sudo systemctl restart docker
docker info --format 'Registry mirrors: {{.RegistryConfig.Mirrors}}'
