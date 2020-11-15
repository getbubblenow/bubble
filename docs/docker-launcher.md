Bubble Docker Launcher
======================
The Bubble Docker Launcher makes it easy to run a Bubble launcher.

## Automatic Setup
If you're running Linux or Mac OS X, try the automatic setup script first.
This script will automatically install docker, pull the Bubble docker image and run it.

    /bin/bash -c "$(curl -sL https://git.bubblev.org/bubblev/bubble/raw/branch/master/docker/launcher.sh)"

If the script cannot install docker, please [install Docker manually](https://docs.docker.com/engine/install/)
and then re-run the above command.

## Manual Setup
If you're on Windows or are if you already have Docker installed and are comfortable using it directly,
you can run the Bubble Docker Launcher via:

    BUBBLE_RELEASE_URL="https://jenkins.bubblev.org/public/releases/bubble/latest.txt"
    VERSION="$(curl -s ${BUBBLE_RELEASE_URL}  | awk -F '_' '{print $2}' | awk -F '.' '{print $1"."$2"."$3}')"
    BUBBLE_TAG="getbubble/launcher:${VERSION}"

    docker pull ${BUBBLE_TAG}
    docker run -p 8090:8090 -t ${BUBBLE_TAG}

## Activation
Upon successful startup, the bubble launcher will be listening on port 8090

Your Bubble is running locally in a "blank" mode. It needs an initial "root" account and some basic services configured.

Open http://127.0.0.1:8090/ in a web browser to continue with activation.

Follow the [Bubble Activation](activation.md) instructions to configure your Bubble.

## Launch a Bubble!
Once your Bubble launcher has been activated, you can [launch a Bubble](launch-node-from-local.md)!
