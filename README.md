# Bubble: a privacy-first VPN

Bubble helps you start and manage your own private VPN.

It also adds tools to this VPN to improve your internet experience by modifying your traffic: to
remove ads, block malware, and much more.

Visit the [**Bubble website**](https://getbubblenow.com/) to learn more.

The [**Bubble Manifesto**](https://github.com/getbubblenow/bubble-docs/blob/master/README.md)
is a statement of the values we cherish and the goals we strive to achieve.

Read [**What is Bubble?**](https://github.com/getbubblenow/bubble-docs/blob/master/what_is_bubble.md)
to get a more in-depth view of what Bubble is and how it works.

If you're interested in developing on Bubble, see the [**Bubble Developer Guide**](docs/dev.md) and
[**Bubble API Guide**](https://github.com/getbubblenow/bubble-docs/blob/master/api/README.md).

## Operating System Support
Once your Bubble is running, any device can connect to it: Windows, Linux, Mac, iOS, Android;
if it supports VPN connections, it will probably work just fine.

To launch your own Bubble using this software, you will need either:
 * A system with Docker installed, to run the [Bubble Docker Launcher](docs/docker-launcher.md)
 * A Mac or Linux system, to run the launcher and/or build from source

## The Easy Path
If you'd like to enjoy all the benefits of Bubble without going through all this hassle,
try launching a Bubble using [GetBubbleNow.com](https://GetBubbleNow.com/).

There are no technical steps, you can be up and running with a few clicks in a few minutes.

Any Bubble you launch from [GetBubbleNow.com](https://GetBubbleNow.com/) will also be "yours only"
-- all Bubbles, shortly after launching, disable all admin access.

## Getting Started
To self-launch your own Bubble, the [Bubble Docker Launcher](docs/docker-launcher.md)
is the easiest way to get started.

If you're feeling more adventurous, you can also [run a binary distribution](docs/run-binary.md), or [build Bubble from source](docs/dev.md).
 
## Deployment Modes
Bubble runs in three different modes. You'll at least need to run a Local Launcher first, then
decide if you want to use a Remote Launcher to manage multiple Bubble nodes, or just launch a single Bubble
directly from the Local Launcher.

#### Local Launcher Mode
In this mode, Bubble runs locally on your machine. You'll setup the various cloud services required to run Bubble,
and use the Local Launcher to start a Remote Launcher or a Bubble Node.

This is the mode that the [Bubble Docker Launcher](docs/docker-launcher.md) runs.

Learn more about setting up [Local Launcher Mode](docs/local-launcher.md)

#### Remote Launcher Mode
In this mode, Bubble is running in the cloud in Launcher Mode, ready to launch new Bubbles that you can use.
You cannot connect devices to a Bubble in Launcher Mode, you can only use it to launch new Bubbles.

Learn more about setting up [Remote Launcher Mode](docs/remote-launcher.md)

#### Bubble Node Mode
In this mode, the Bubble has been launched by a Local Launcher or a Remote Launcher and is a proper Bubble Node.
You can connect your devices to it and use it as your own private VPN and enhanced internet service.

Learn more about launching a [Bubble Node](docs/launch-node.md)
