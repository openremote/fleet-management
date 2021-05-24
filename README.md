# Custom Project Template
This repo is a template for custom projects; showing the recommended project structure and including `README` files in the `deployment` directory to provide details about how to customise each part.

This project can be compiled and used as it is using the following commands:

```yml
./gradlew clean installDist
DEPLOYMENT_VERSION=$(git rev-parse --short HEAD)
MANAGER_VERSION=$(git rev-parse --short HEAD)
cd ..
docker build -t openremote/manager:$MANAGER_VERSION ./openremote/manager/build/install/manager/
docker build -t openremote/custom-deployment:$DEPLOYMENT_VERSION ./deployment/build/
docker-compose -p custom down
docker volume rm custom_deployment-data
# Do the following volume rm command if you want a clean install (wipe all existing data)
docker volume rm custom_postgresql-data
MANAGER_VERSION=$MANAGER_VERSION DEPLOYMENT_VERSION=$DEPLOYMENT_VERSION EMAIL=admin@noreply.com PASSWORD=secret DEPLOYMENT_HOST=localhost docker-compose -p custom up -d
```
