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
The very first Bubble you launch will use a [packer image](packer.md) that was created
during [activation](activation.md).

### Packer Image Creation
Normally the packer images are created during activation, and are generally available within about 20 minutes.

If the required packer image is still being built when you launch your first Bubble, it's OK,
the launcher will wait for the packer image to be ready.

If for some reason the packer image does not exist and is not currently being built,
then the launcher will start building the packer image right then.
This process adds about 20 minutes to the launch process.
 
When launching a Bubble and the required packer image is still being built,
the progress meter will appear to be "stuck" at 1% until the image is ready. This is normal.

If you're running the launcher from a [binary](run-binary.md) or [source](dev.md) Bubble distribution,
you can check the status of the packer jobs by running
```shell script
pack_status
```

If you're running the launcher from using the [Bubble Docker Launcher](docker-launcher.md),
you can observe output of the packer image being built in your Bubble logs.

More details on packer and the `pack_status` command are available in
the [Bubble packer documentation](packer.md).

## Install Bubble Apps
While your Bubble is launching, take a moment to
[install the Bubble Native app](https://support.getbubblenow.com/hc/en-us/articles/360050801634-Connect-a-device-to-your-Bubble)
on each device you plan on connecting to your Bubble.

When your Bubble finishes launching, it will show a "Connect to Bubble" button. Click this and you'll be connected
to your Bubble Node.
