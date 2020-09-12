Bubble Developer Guide
======================

# Development Setup
These instructions presume you are running a newly setup Ubuntu 20.04 system.
Either the Ubuntu Server or Desktop distribution will work.

## First-Time System Setup
After you clone this repository, run:

    ./bin/first_time_ubuntu.sh

If you are running on a non-Ubuntu system, copy that file to something like:

    ./bin/first_time_myoperatingsystem.sh

And then edit it such that all the same packages get installed.
Then submit a pull request and we can add support for your operating system to the main repository.

You only need to run this command once, ever, on a development system.
It ensures that the appropriate packages are installed and proper databases and database users exist.

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
