Bubble Developer Guide
======================
This guide is intended for developers who would like to work directly with the Bubble source code.

For API-level details, see the [the Bubble API](https://github.com/getbubblenow/bubble-docs/blob/master/api/README.md)

# Development Setup
These instructions presume you are running a newly setup Ubuntu 20.04 or Mac OS X system.

For Ubuntu, either the Server or Desktop distribution will work.

Other Debian-based systems will probably also work fine.

See below for other Linux distributions and other operating systems.

## One-Time System Setup
You'll need to install some software for Bubble to work correctly.

Follow the instructions in [System Software Setup](system-software.md) to install the required software. 

## First-Time Dev Setup
After running the system setup above, run:

    ./bin/first_time_setup.sh

This will grab all the submodules and perform an initial build of all components.

This will take a while to complete, please be patient.

## Bubble environment file
You will need a file named `${HOME}/.bubble.env` which contains various environment variables required to run the server.

Talk to another developer to get a copy of this file.
Do not ever send this file over email or any other unencrypted channel, it contains secret keys to various cloud
services that your Bubble will use. Always use `scp` to copy this file from one machine to another.

If you will be running any tests, create a symlink called `${HOME}/.bubble-test.env`

    cd ${HOME} && ln -s .bubble.env .bubble-test.env

The `.bubble-test.env` file is used by the test suite.

## Subsequent Updates
If you want to grab the latest code, and ensure that all git submodules are properly in sync with the main repository, run:

    ./bin/git_update_bubble.sh

This will update and rebuild all submodules, and the main bubble jar file.

## Running in development
Run the `bin/run.sh` script to start the Bubble server.

## Resetting everything
If you want to "start over", run:

     ./bin/reset_bubble_full

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
