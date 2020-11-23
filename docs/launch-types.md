Bubble Launch Types
===================
There are different ways to launch a Bubble, depending on what you're trying to do.

First we'll discuss how to set the launch type, then look at what the different options are and what
they mean.

# Setting the Launch Type
How you set the launch type depends on whether you use the web interface or the [CLI/API](https://github.com/getbubblenow/bubble-docs/blob/master/api/README.md).

### Via Web UI
Launch Type is called "Bubble Type" in the web interface.

On the Bubble launch screen, click the "Launch with Advanced Settings" link.
The first drop-down option is "Bubble Type".

### Via the API
The Launch Type is called `installType` in the Bubble API. When using the API, set the `launchType` property in the JSON object that creates the Bubble. 

# What Makes Launch Types Different?
The two key distinguishing characteristics of each Launch Type are its **Mode** and how it handles **Cloud Services**.

### Bubble Mode
The mode can be either `sage` or `node`.

A `sage`, also called a launcher, is a Bubble that launches other Bubbles.
You don't connect devices to a sage/launcher. You just launcher other Bubbles with it.

A `node`, also often just called a Bubble, is a regular Bubble that acts as a VPN and you connect devices to it.
A Bubble can either delegate cloud services back to the Bubble that launched it (we call this a Bubble in cloud mode),
or it can call cloud services directly (a Bubble in standalone mode).

The mode is called `installType` in the [API](https://github.com/getbubblenow/bubble-docs/blob/master/api/README.md)

### Cloud Services
A bubble will either delegate cloud services back to the launcher that created it,
or use the cloud services directly itself.

If you don't want your cloud credentials stored in your node, use delegated cloud services.

Alternatively, if you're starting a launcher or don't want cloud services mediated via a launcher,
you'll want your Bubble to call cloud services directly.

# Launch Types in Detail
Bubble supports the following launch types:
  
## Regular
  * **Packer Image**: `node`
  * **Mode**: `node` (cloud)
  * **Cloud Services**: delegated to the `sage` that launched it
  * **Scenario**: Launching regular Bubbles from a Remote Launcher

## Fork Launcher
  * **Packer Image**: `sage`
  * **Mode**: `sage`
  * **Cloud Services**: cloned from launcher, called directly
  * **Scenario**: Creating a Remote Launcher from a Local Launcher

## Fork Bubble
  * **Packer Image**: `node`
  * **Mode**: `node` (standalone)
  * **Cloud Services**: cloned from launcher, called directly
  * **Scenario**: Creating a standalone Bubble with no ties to any launcher

### Local Launchers
A [Local Launcher](local-launcher.md) is a special case, since it bootstraps everything else.

Nonetheless, using the same bullet points as above can be informative:
 
## Local Launcher
  * **Packer Image**: none
  * **Mode**: `sage`
  * **Cloud Services**: supplied during [activation](activation.md), called directly
  * **Scenario**: The first step before launching a proper Bubble
