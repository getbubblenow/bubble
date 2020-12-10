Launching a Bubble from a Local Launcher
========================================
These instructions assume you have already set up a [Local Launcher](local-launcher.md)
or are running the [Bubble Docker Launcher](docker-launcher.md).

## Login
Open a browser window to your Local Launcher. You should see a screen like this one:

#### Sign In Screen
  <a href="img/sign_in.png"><img src="img/sign_in.png" alt="screenshot of Sign In page" height="500"/></a>

Login to your Local Launcher using the root admin account that was created during activation.

## Launch Bubble
You should see a "Launch Bubble" screen, like the one below:

#### Launch Bubble Screen
  <a href="img/launch_bubble.png"><img src="img/launch_bubble.png" alt="screenshot of Launch Bubble page" height="500"/></a>

Click the "Launch with Advanced Settings" link below the `LAUNCH BUBBLE` button.
You should now see the advanced launch settings screen, like the one below:

#### Launch Settings Screen
  <a href="img/launch_settings.png"><img src="img/launch_settings.png" alt="screenshot of Launch Settings" height="500"/></a>

In the "Bubble Type" drop-down, ensure that "Fork Bubble" is selected.

Choose your other configuration options, then click the "Launch Your Bubble!" button kick things off.

The screen will refresh and show a progress meter. A typical launch will take about 10 minutes.

## Your Very First Bubble
The very first Bubble you launch will use a [packer image](packer.md) that was created
during [activation](activation.md).

If the image is still being built or needs to be built, that will add some time to the launch process.

The [Bubble packer documentation](packer.md) has more details on this process. 

## Install Bubble Native Apps
While your Bubble is launching, take a moment to
[install the Bubble Native app](https://support.getbubblenow.com/hc/en-us/articles/360050801634-Connect-a-device-to-your-Bubble)
on each device you plan on connecting to your Bubble.

When your Bubble finishes launching, it will show a "Connect to Bubble" button. Click this and you'll be connected
to your Bubble Node.
