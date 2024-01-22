# Welcome to OpenRemote's Fleet Management Repository!

This repository contains the OpenRemote custom project that contains full support for fleet management features, like location tracking and session tracking, and also the industry-first **complete, automatic data recognition** from any Teltonika Telematics device model.

Please look at the wiki for the tutorial on how to set up your ownfleet management system, and the Developer Guide to understand the inner workings of the fleet management implementation.

## Quickstart

An online demo will be made available to the public shortly, but you can still run the OpenRemote fleet management implementation locally using Docker:

The quickest way to get your own environment with full access is to make use of our docker images (both `amd64` and `arm64` are supported). 
1. Make sure you have [Docker Desktop](https://www.docker.com/products/docker-desktop) installed (v18+). 
2. Download the docker compose file:
[OpenRemote Stack](https://raw.githubusercontent.com/openremote/fleet-management/master/docker-compose.yml) (Right click 'Save link as...')
3. In a terminal `cd` to where you just saved the compose file and then run:
```
    docker-compose pull
    docker-compose -p fleet-management up
```
If all goes well then you should now be able to access the OpenRemote Manager UI at [https://localhost](https://localhost). You will need to accept the self-signed 
certificate, see [here](https://www.technipages.com/google-chrome-bypass-your-connection-is-not-private-message) for details how to do this in Chrome (similar for other browsers).

# Custom Project Format

To create the OpenRemote fleet management integration, a new custom project was created using [OpenRemote's custom-project template](https://github.com/openremote/custom-project). To view the changes of files between the original custom-project repository and the current state of the repository, press [here]( https://github.com/openremote/fleet-management/compare/668ae6fdfb20eeae5977ad62b655bf3fb3d58cdd...main). In this way, you can see the files that have been added since the creation of this repository. 

This repository uses the [feature/fleet-management](https://github.com/openremote/openremote/tree/feature/fleet-management) branch of the main OpenRemote repository as its core, specifically for adding more UI-related features. If the UI features are not something that interest you, you're encouraged to change the submodule to use the `master` OpenRemote branch. 


# Support and Community

For support, comments, questions, and concerns, please head to [OpenRemote's forum](https://forum.openremote.io/) and post any questions here. Issues and Pull-Requests created here could be ignored, so if they do, please contact us through the forum. 
