# Bubble Packer Images
A [packer image](https://packer.io) is way to initialize a new cloud system with an operating system and
software and files pre-installed. This is an oversimplified explanation but will suffice for our current needs.
 
Before packer images, we launched Bubbles onto "blank" Ubuntu systems and then did
tons of installation and configuration. It took 20+ minutes to launch a new Bubble.

We decided we could do better. Packer allows us to create Ubuntu images that already have 
PostgreSQL, Redis and nginx installed and configured, and lots more.

Launching a Bubble is now faster (usually 10 minutes or less) because all the standard software
and configs are already present on the packer image that each launch starts with.

We can make it even faster, but getting a new Bubble launched in under 10 minutes is a real accomplishment,
and Packer makes it possible. 

## Image Types
There are two types of packer images: `sage` and `node`

A `sage` image is for deploying new Bubbles in Launcher mode. A launcher is called a `sage` in the Bubble API.

A `node` image is for deploying new Bubbles in Node mode. A regular Bubble that acts as a VPN for devices is called a `node` in the Bubble API.

Bubble will automatically use the appropriate image type based on the Launch Type.

To set the Launch Type: on the Bubble launch screen, click the "Launch with Advanced Settings" link.

When using the API, set the `launchType` property in the JSON request that creates the Bubble. 

## Launching a Bubble while Packer Images are Building
If you try to launch a Bubble before the required packer image is ready, your Bubble launcher will detect that the image
is in the process of being built, and wait until it is ready before trying to use it.

## Image Validity
The packer images are tied to a specific Bubble version. As long as the Bubble API launcher can find packer images
that match its own version, it will be able to launch Bubbles.

When your Bubble is upgraded, its version changes, so the old packer images are no longer valid.

Your Bubble will automatically build new images the next time a Bubble is launched.

If you'd prefer not to add a 20 minute delay (as packer images are built) to the next Bubble launch, you
can manually rebuild the images after upgrading your Bubble server. See below.

## Manually Rebuilding Packer Images
To ensure that all required packer images exist (build them if needed):
 
    ./bin/pack_bubble

For more information, see:

    ./bin/pack_bubble --help

Note: to use the `bin` tools, define environment variables `BUBBLE_USER`, `BUBBLE_PASS`, and `BUBBLE_API`
appropriately for your Bubble. For example:

    export BUBBLE_USER=user@example.com
    export BUBBLE_PASSWORD=password
    export BUBBLE_API=https://bubble.example.com/api   # deployed launcher
    export BUBBLE_API=http://127.0.0.1:8090/api        # local launcher

    ./bin/pack_bubble

## Packer Build Status
When packer images are being built, you can check status with:

    ./bin/pack_status

This will show all running packer jobs, and all completed packer images.

To only see running packer jobs:

    ./bin/pack_status running

To only see completed packer images:

    ./bin/pack_status completed

### Packer Build Status in Logs
If you're able to view the Bubble API logs, you'll see a line like the one below when a packer
image has completed processing:

    ... [PackerJob.java:301] packer images created in 00:19:30.0933: [PackerImage(id=ce95fb4371175, name=packer_bubble_node_local_868d2fba8f83ab3591c60b7bade35_d1.1.4.8_a1.1.4.10_m1.1.4.21_1.4.20_20201117153449, regions=null)]
