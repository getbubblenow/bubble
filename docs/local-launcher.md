# Bubble Local Launcher Mode
A Bubble in Local Launcher Mode is the starting point for standing up a Bubble in
[Remote Launcher Mode](remote-launcher.md), which you can then use to launch proper Bubbles
in [Bubble Node Mode](launch-node.md).

### Run Bubble
Run the `./bin/run.sh` script on your local machine to start Bubble in Local Launcher mode.
Once the server is running, it will try to open a browser window to continue configuration.
It will also print out the URL, so if the browser doesn't start correctly, you can paste this
into your browser's location bar.

### Activation
Your Bubble is running locally in a "blank" mode. It needs an initial "root" account and some basic services configured.

In order to activate your Local Launcher, you'll need accounts and/or API keys from several cloud providers.
Have these account credentials handy. Be prepared to sign up for new accounts where needed.

#### Activate via Web UI
The browser-based admin UI should be displaying an "Activate" page. Complete the information on this page and submit the
data. The Bubble Launcher will create an initial "root" account and other basic system configurations. 

#### Activate via command line
Make a copy of the file `config/activation.json` and edit the copy. There are comments in the file to guide you.

To activate your Local Launcher Bubble, run this command:

   `./bin/bactivate /path/to/activation.json`

After running the above, refresh the web page that opened when the server started. You should see a login page.

You can now login as the admin user using the email address `root@local.local` and the password from your `activation.json` file.

You are now read to Launch Bubble in [Remote Launcher Mode](remote-launcher.md)
