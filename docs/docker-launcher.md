Bubble Docker Launcher
======================
The Bubble Docker Launcher makes it easy to run a Bubble launcher.

## Automatic Setup with the Launcher Script
If you're running Linux or Mac OS X, try the launcher script first.
This script will automatically install docker, pull the Bubble docker image and run it.

    /bin/bash -c "$(curl -sL https://git.bubblev.org/bubblev/bubble/raw/branch/master/launcher)"

The launcher will listen on port 8090, you can change this by setting the `BUBBLE_PORT` environment variable:

    BUBBLE_PORT=8080 /bin/bash -c "$(curl -sL https://git.bubblev.org/bubblev/bubble/raw/branch/master/launcher)"

There are a few other environment variables you can set to customize the launcher configuration.

The header of the [launcher script](https://git.bubblev.org/bubblev/bubble/src/branch/master/launcher)
describes all the launch options in detail.

### Running Launcher from Source
If you have cloned the main [Bubble source repository](https://git.bubblev.org/bubblev/bubble), you can run
the launcher directly, without using `curl`.

From the top-level directory of the Bubble source repository (usually called `bubble`), run:

    ./launcher

You can pass environment variables in the same way as described above.

## Docker Installation
If you're running Windows, or if the above script has problems installing Docker,
please [install Docker manually](https://docs.docker.com/engine/install/).

If you're on Mac OS X or Linux, after installing Docker please re-run the above script.

If you're on Windows or would like to run the Bubble docker image directly, follow the instructions below in "Manual Setup".

## Manual Setup
If automatic installation has problems, or you are already comfortable working
with Docker, you can use the manual Bubble Docker approach.

All you need to do is pull and run the appropriate image from DockerHub.
This section explains how to determine the most recent version tag, and
then pull and run the docker image for that tag.

The commands below assume:
 * you already have docker installed
 * the docker daemon is running
 * the current user has appropriate permissions to start docker containers

### Use the Latest Bubble Version
To pull and run the Bubble Docker Launcher, open a terminal and run: 

    BUBBLE_RELEASE_URL="https://jenkins.bubblev.org/public/releases/bubble/latest.txt"
    VERSION="$(curl -s ${BUBBLE_RELEASE_URL}  | awk -F '_' '{print $2}' | awk -F '.' '{print $1"."$2"."$3}')"
    BUBBLE_TAG="getbubble/launcher:${VERSION}"

    docker pull ${BUBBLE_TAG}
    docker run -p 8090:8090 -t ${BUBBLE_TAG}

### Use a Specific Bubble Version
If you know the specific version of Bubble you want, you can just grab that and run it:

    docker pull getbubble/launcher:1.5.11
    docker run -p 8090:8090 -t getbubble/launcher:1.5.11

## Activation
Upon a successful startup, the bubble launcher will be listening on port 8090 (or whatever
you set the `BUBBLE_PORT` environment variable to).

Your Bubble is running locally in a "blank" mode.
It needs an initial "root" account and some basic services configured.

Open http://127.0.0.1:8090/ in a web browser to continue with activation.

Follow the [Bubble Activation](activation.md) instructions to configure your Bubble.

## Launch a Bubble!
Once your Bubble launcher has been activated, you can [launch a Bubble](launch-node-from-local.md)!
