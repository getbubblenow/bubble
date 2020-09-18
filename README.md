# Bubble: a privacy-first VPN

Bubble helps you start and manage your own private VPN.

It also adds tools to this VPN to improve your internet experience by modifying your traffic: to
remove ads, block malware, and much more.

Visit the [Bubble website](https://getbubblenow.com/) to learn more.

If you're interested in developing on Bubble, see the [Bubble Developer Guide](docs/dev.md).

## Operating System Support
Once your Bubble is running, any device can connect to it: Windows, Linux, Mac, iOS, Android;
if it supports VPN connections, it will probably work just fine.

However, to launch your own Bubble using this software, you will need a Linux machine to run the launcher.
It *probably* works on MacOS, but it has not been tested and there are likely to be issues. Pull requests are welcome!

## The Easy Path
If you'd like to enjoy all the benefits of Bubble without going through this hassle, please try out the Bubble launching
service available on [getbubblenow.com](https://getbubblenow.com/).
Any Bubble you launch from [getbubblenow.com](https://getbubblenow.com/) will also be "yours only" -- all Bubbles
disconnect from their launcher during configuration.

## Getting Started
The setup instructions below assume you are running Ubuntu 20.04. If you're running another flavor of Linux,
you can probably figure out how to get things working. If you're running Mac OS X or Windows, things might be
more difficult.

### Download a Bubble Distribution
Download the [latest Bubble release](https://jenkins.bubblev.org/public/releases/bubble/latest/bubble.zip)

Open a command-line terminal.

Unzip the file that you downloaded:

    unzip bubble.zip

Change directories into the directory containing the files that were unzipped:

    cd bubble-Adventure_1.x.x     # replace `Adventure_1.x.x` with the version that you downloaded

### Install System Software
You'll need to install some software for Bubble to work correctly. Run:

    ./bin/first_time_ubuntu.sh

This will grab all the submodules and perform an initial build of all components.

You only need to run this command once, ever, on a given system.
It ensures that the appropriate packages are installed and proper databases and database users exist.

`first_time_ubuntu.sh` command uses `apt` commands to install various packages, so Debian (or other Debian-based)
distributions should work fine. If you are running a different OS or distribution, copy that file to something like:
                                
    ./bin/first_time_myoperatingsystem.sh
                                
And then edit it such that all the same packages get installed.
Then submit a pull request and we can add support for your operating system to the main repository.

## Deployment Modes
Bubble runs in three different modes. You'll at least need to run a Local Launcher first, then
decide if you want to use a Remote Launcher to manage multiple Bubble nodes, or just launch a single Bubble
directly from the Local Launcher.

#### Local Launcher Mode
In this mode, Bubble runs locally on your machine. You'll setup the various cloud services required to run Bubble,
and use the Local Launcher to start a Remote Launcher or a Bubble Node.

Learn more about setting up [Local Launcher Mode](docs/local-launcher.md)

#### Remote Launcher Mode
In this mode, Bubble is running in the cloud in Launcher Mode, ready to launch new Bubbles that you can use.
You cannot connect devices to a Bubble in Launcher Mode, you can only use it to launch new Bubbles.

Learn more about setting up [Remote Launcher Mode](docs/remote-launcher.md)

#### Bubble Node Mode
In this mode, the Bubble has been launched by a Local Launcher or a Remote Launcher and is a proper Bubble Node.
You can connect your devices to it and use it as your own private VPN and enhanced internet service.

Learn more about launching a [Bubble Node](docs/launch-node.md)
