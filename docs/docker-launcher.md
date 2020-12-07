Bubble Docker Launcher
======================
The Bubble Docker Launcher makes it easy to run a Bubble launcher.

## Automatic Setup
If you're running Linux or Mac OS X, try the automatic setup script first.
This script will automatically install docker, pull the Bubble docker image and run it.

    /bin/bash -c "$(curl -sL https://git.bubblev.org/bubblev/bubble/raw/branch/master/launcher.sh)"

## Docker Installation
If you're running Windows, or if the above script has problems installing Docker,
please [install Docker manually](https://docs.docker.com/engine/install/).

If you're on Mac OS X or Linux, after installing Docker please re-run the above script.

If you're on Windows or would like to run the Bubble docker image directly, follow the instructions below in "Manual Setup".

## Manual Setup
The commands below assume:
 * you already have docker installed
 * the docker daemon is running
 * the current user has appropriate permissions to start docker containers

To pull and run the Bubble Docker Launcher, open a terminal and run: 

    BUBBLE_RELEASE_URL="https://jenkins.bubblev.org/public/releases/bubble/latest.txt"
    VERSION="$(curl -s ${BUBBLE_RELEASE_URL}  | awk -F '_' '{print $2}' | awk -F '.' '{print $1"."$2"."$3}')"
    BUBBLE_TAG="getbubble/launcher:${VERSION}"

    docker pull ${BUBBLE_TAG}
    docker run -p 8090:8090 -t ${BUBBLE_TAG}

## Activation
Upon a successful startup, the bubble launcher will be listening on port 8090

Your Bubble is running locally in a "blank" mode. It needs an initial "root" account and some basic services configured.

Open http://127.0.0.1:8090/ in a web browser to continue with activation.

Follow the [Bubble Activation](activation.md) instructions to configure your Bubble.

## Launch a Bubble!
Once your Bubble launcher has been activated, you can [launch a Bubble](launch-node-from-local.md)!
