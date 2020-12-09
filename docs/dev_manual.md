Bubble Manual Development Setup
===============================
These instructions presume you are running a newly-setup Ubuntu 20.04 or Mac OS X system.

For Ubuntu, either the Server or Desktop distribution will work.

Other Debian-based systems will probably also work fine.

See below for other Linux distributions and other operating systems.

## One-Time System Setup
You'll need to install some software for Bubble to work correctly.

Follow the instructions in [System Software Setup](system-software.md) to install the required software.

## First-Time Dev Setup
After running the system setup above, run:
```shell script
./bin/first_time_setup.sh
```

This will grab all the submodules and perform an initial build of all components.

This will take a while to complete, please be patient.

## Bubble environment file
You will need a file named `${HOME}/.bubble.env` which contains various environment variables
required to run the server. At the least, it should contain:
```shell script
export LETSENCRYPT_EMAIL=user@example.com
```

This defines what email address is used with LetsEncrypt when creating new SSL certificates.

If you will be running any tests, create a symlink called `${HOME}/.bubble-test.env`

```shell script
cd ${HOME} && ln -s .bubble.env .bubble-test.env
```

The `.bubble-test.env` file is used by the test suite.

## What's Next
Continue reading the [Bubble Developer Guide](dev.md) for information
on how to update the source code, reset the database, and more.
