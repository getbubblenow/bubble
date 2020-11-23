Bubble Remote Launcher Mode
===========================
You must already have a Bubble running in [Local Launcher Mode](local-launcher.md) to proceed.

## Start Bubble
If your Bubble is not already running on your local system, start it by running `./bin/run.sh`

## Log In
When your local Bubble finishes launching, it should open a web page. If you see a page with the title "Activation",
then your Bubble still needs to be activated. Read about activation in the [Local Launcher Mode](local-launcher.md) instructions.

If you see a login screen, you should be able to log in as the admin user using the email
address `root@local.local` and the password from your `activation.json` file.

## Fork a Remote Launcher
After you log in, you should see a "Launch Bubble" screen.
If you don't see this screen, click the "My Bubble" link in the header.

In the "Bubble Type" drop-down box, choose "Fork Launcher".

In the "Fork Host" field, enter the fully-qualified domain name (FQDN) that the Bubble will be known as.

In the "Plan" field, it is recommended to choose the highest-level plan. The Remote Launcher requires a decent amount
of memory and CPU.

In the "Domain" field, choose the domain that corresponds to the FQDN you entered in the "Fork Host" field.

Configure the remaining fields as you desire. When you're ready, click the "Launch Your Bubble!" button.

The screen will refresh and show a progress meter. A typical launch will take about 10 minutes.

## Your Very Remote Launcher
The very first Bubble Remote Launcher you launch will use a [packer image](packer.md) that was created
during [activation](activation.md).

If the image is still being built or needs to be built, that will add some time to the launch process.

The [Bubble packer documentation](packer.md) has more details on this process. 

## Next Steps
When your Bubble finishes launching, it will show a "Connect to Bubble" button. Click this and you'll be connected
to your Remote Launcher.

You are now ready to [Launch a Bubble](launch-node.md) from the Remote Launcher
