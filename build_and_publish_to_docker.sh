#!/usr/bin/env bash
set -euo pipefail

# Configuration
DOCKER_REPO="pankalog/fleet-deployment"
COMPOSE_PROJECT="fleet-management"
BUILDER_NAME="multi-platform-builder"
PLATFORMS="linux/amd64,linux/arm64"
MANAGER_VERSION="1.15.1"

# Derived values
GIT_VERSION=$(git rev-parse --short HEAD)
DEPLOYMENT_DIR="./build/deployment-${GIT_VERSION}"

echo "==> Building deployment version: $GIT_VERSION"
echo "==> Deployment directory: $DEPLOYMENT_DIR"

# Clean deployment directory
echo "==> Cleaning previous builds..."
rm -rf ./deployment/build

# Build application
echo "==> Running Gradle build..."
./gradlew clean installDist

# Prepare deployment artifacts
cp -r ./deployment/build "$DEPLOYMENT_DIR"

# Docker multi-arch build
echo "==> Building and pushing Docker images..."
docker login
docker buildx create --use --platform="$PLATFORMS" --name "$BUILDER_NAME" 2>/dev/null || true
docker buildx build \
    --push \
    --platform "$PLATFORMS" \
    --builder "$BUILDER_NAME" \
    --no-cache \
    -t "${DOCKER_REPO}:${GIT_VERSION}" \
    -t "${DOCKER_REPO}:latest" \
    "$DEPLOYMENT_DIR"

# Deploy
echo "==> Preparing deployment..."
export OR_ADMIN_PASSWORD="${OR_ADMIN_PASSWORD:-secret}"
export DEPLOYMENT_VERSION="$GIT_VERSION"
export MANAGER_VERSION="$MANAGER_VERSION"

docker pull "${DOCKER_REPO}:${GIT_VERSION}"
docker compose -p "$COMPOSE_PROJECT" pull deployment manager

echo "==> Stopping services and cleaning up..."
docker compose -p "$COMPOSE_PROJECT" down -v

echo "==> Starting services with fresh images..."
docker compose -p "$COMPOSE_PROJECT" up -d

echo "==> Deployment complete: $GIT_VERSION"
