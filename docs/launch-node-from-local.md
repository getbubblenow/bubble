Launching a Bubble from a Local Launcher
========================================
These instructions assume you have already set up a [Local Launcher](local-launcher.md)
or are running the [Bubble Docker Launcher](docker-launcher.md).

## Login
Login to your Local Launcher using the root admin account that was created during activation.

Because the login field must be an email address, use the special email address `root@local.local` to login
with the admin account.

## Launch Bubble
You should see a "Launch Bubble" screen.

In the "Bubble Type" drop-down, choose "Fork Bubble"

Choose your configuration options, then click the "Launch Your Bubble!" button kick things off.

The screen will refresh and show a progress meter. A typical launch will take about 10 minutes.

## Your Very First Bubble
The very first Bubble you launch will build a [Packer](https://packer.io) image that will be used for this and
subsequent launches.

This process adds about 20-25 minutes to the launch process.

While the packer image is building, the progress meter will appear to be "stuck" at 1%. This is normal.
If you're curious, you can observe the packer image being built in your Bubble logs.

This only happens the first time you launch a Bubble.
Later launches can skip this step, because Bubble will detect that the packer image already exists.

## Install Bubble Apps
While your Bubble is launching, take a moment to
[install the Bubble Native app](https://support.getbubblenow.com/hc/en-us/articles/360050801634-Connect-a-device-to-your-Bubble)
on each device you plan on connecting to your Bubble.

When your Bubble finishes launching, it will show a "Connect to Bubble" button. Click this and you'll be connected
to your Bubble Node.
