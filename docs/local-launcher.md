# Bubble Local Launcher Mode
A Bubble in Local Launcher Mode is the starting point for standing up a Bubble in
[Remote Launcher Mode](remote-launcher.md), which you can then use to launch proper Bubbles
in [Bubble Node Mode](launch-node.md).

## Run Bubble
Run the `./bin/run.sh` script on your local machine to start Bubble in Local Launcher mode.
Once the server is running, it will try to open a browser window to continue configuration.
It will also print out the URL, so if the browser doesn't start correctly, you can paste this
into your browser's location bar.

## Activation
Your Bubble is running locally in a "blank" mode. It needs an initial "root" account and some basic services configured.

Follow the [Bubble Activation](activation.md) instructions to configure your Bubble.

### Resetting everything
If you want undo activation and "start over" with a blank Bubble: first, if your local bubble is still running, stop it.
Then, run:

     ./bin/reset_bubble_full

This will remove local files stored by Bubble, and drop the bubble database.

If you run `./bin/run.sh` again, it will be like running it for the first time.

## Next Steps
You are now read to launch a new Bubble in [Bubble Mode](launch-node.md), or
a Remote Launcher via [Remote Launcher Mode](remote-launcher.md)
