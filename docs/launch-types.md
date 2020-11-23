Bubble Launch Types
===================
There are different ways to launch a Bubble, depending on what you're trying to do.

First we'll discuss how to set the Launch Type, then look at what the different options are and what
they mean.

# Setting the Launch Type
How you set the Launch Type depends on whether you use the web interface or the [CLI/API](https://github.com/getbubblenow/bubble-docs/blob/master/api/README.md).

### Via Web UI
Launch Type is called "Bubble Type" in the web interface.

On the Bubble launch screen, click the "Launch with Advanced Settings" link.
The first drop-down option is "Bubble Type".

### Via the API
When using the API, set the `launchType` property in the JSON object that creates the Bubble. 

# What Makes Launch Types Different?
The two key distinguishing characteristics of each Launch Type are its **Mode** and how it handles **Cloud Services**.

### Bubble Mode
The mode can be either `sage` or `node`.

A `sage`, also called a launcher, is a Bubble that launches other Bubbles.
You don't connect devices to a sage/launcher. You just launcher other Bubbles with it.

A `node`, also often just called a Bubble, is a regular Bubble that acts as a VPN and you connect devices to it.

### Cloud Services
When it needs to use cloud services, a Bubble can either:
 * Delegate the call back to its launcher (a "cloud Bubble")
 * Call the cloud service directly (a "standalone Bubble")

What are the pros/cons of each approach?

If you don't want your cloud credentials stored in your node, use delegated cloud services.
Alternatively, if you're starting a launcher or don't want cloud services mediated via a launcher,
you'll want your Bubble to call cloud services directly.

# Launch Types in Detail
Bubble supports the following Launch Types:
  
## Regular
  * **Packer Image**: `node`
  * **Mode**: `node` (cloud)
  * **Cloud Services**: delegated to the `sage` that launched it
  * **Scenario**: Launching a [cloud Bubble](launch-node-from-remote.md) from a [Remote Launcher](remote-launcher.md)

## Fork Launcher
  * **Packer Image**: `sage`
  * **Mode**: `sage`
  * **Cloud Services**: cloned from launcher, called directly
  * **Scenario**: Creating a [Remote Launcher](remote-launcher.md) from a [Local Launcher](local-launcher.md)

## Fork Bubble
  * **Packer Image**: `node`
  * **Mode**: `node` (standalone)
  * **Cloud Services**: cloned from launcher, called directly
  * **Scenario**: Creating a [standalone Bubble](launch-node-from-local.md) from a [Local Launcher](local-launcher.md)

### Local Launchers
The [Local Launcher](local-launcher.md) is a special case, since it bootstraps everything else.

Nonetheless, using the same bullet points as above can be informative:
 
## Local Launcher
  * **Packer Image**: none
  * **Mode**: `sage`
  * **Cloud Services**: supplied during [activation](activation.md), called directly
  * **Scenario**: The first step before launching any other kind of Bubble
