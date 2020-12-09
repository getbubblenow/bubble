Bubble Developer Guide
======================
This guide is intended for developers who would like to work directly with the Bubble source code.

For API-level details, see the [Bubble API Guide](https://github.com/getbubblenow/bubble-docs/blob/master/api/README.md)
and the [Bubble API Reference](https://app.getbubblenow.com/apidocs/)

# Vagrant Setup
The easiest way to get started with Bubble is to install [Vagrant](https://www.vagrantup.com/) and use
the Bubble [Vagrantfile](../Vagrantfile):

```shell script
vagrant up
```

This will take a long time to complete. When it is done, you'll have a Vagrant box with
the Bubble source code and all dependencies fully built and ready to run a local launcher.

When your Vagrant box is ready, you can login to it and start the local launcher:

```shell script
vagrant ssh
./bubble/bin/run.sh
```

# Manual Development Setup
If you'd prefer not to use Vagrant or want to build things locally, follow
the [Bubble Manual Development Setup](dev_manual.md) instructions.

## Subsequent Updates
If you want to grab the latest code, and ensure that all git submodules are properly in sync with the main repository, run:
```shell script
./bin/git_update_bubble.sh
```

This will update and rebuild all submodules, and the main bubble jar file.

## Running in development
Run the `bin/run.sh` script to start the Bubble server.

## Resetting everything
If you want to "start over", run:
```shell script
./bin/reset_bubble_full
```

This will remove local files stored by Bubble, and drop the bubble database.

If you run `./bin/run.sh` again, it will be like running it for the first time.

## Next
What to do next depends on what you want to do with Bubble.

If you've started the Bubble API already using `run.sh`, and want to launch a Bubble,
continue with [activation](activation.md).

Would you like more guidance on starting the [Local Launcher](local-launcher.md)?

If all you want to do is launch your own Bubble, starting with
the [Bubble Docker Launcher](docker-launcher.md) is probably faster and easier.

Or perhaps you are interested in exploring the
[Bubble API](https://github.com/getbubblenow/bubble-docs/blob/master/api/README.md) and
interacting with Bubble programmatically.
