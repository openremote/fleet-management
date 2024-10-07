
./gradlew clean installDist
GIT_DEPLOYMENT_VERSION=$(git rev-parse --short HEAD)
docker login
docker buildx create --use --platform=linux/arm64,linux/amd64 --name multi-platform-builder
docker buildx build --load --platform linux/amd64,linux/arm64 -t pankalog/fleet-deployment:"$GIT_DEPLOYMENT_VERSION" --builder multi-platform-builder  ./deployment/build/
docker buildx build --load --platform linux/amd64,linux/arm64 -t pankalog/fleet-management:"$GIT_DEPLOYMENT_VERSION" --builder multi-platform-builder  ./openremote/manager/build/install/manager/
DEPLOYMENT_VERSION="$GIT_DEPLOYMENT_VERSION" docker-compose -p fleet-management up -d
