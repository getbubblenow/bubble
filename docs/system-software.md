Install Required System Software
================================
You only need to do this if you're [running from a binary distribution](run-binary.md)
or [building and running from source](dev.md).

If you're using the [Bubble Docker Launcher](docker-launcher.md) or
[Bubble Vagrant Developer Setup](dev_vagrant.md), skip this step.

## Why Do This?
You'll need to install some software for Bubble to work correctly.

Bubble needs a PostgreSQL database, Redis, and a bunch of command line tools installed. 

It ensures that the appropriate packages are installed and proper databases and database users exist.

You only need to install system software once, ever, on a given system.
You may be asked for your password to install packages and restart services.

### Ubuntu
For Ubuntu 18.04 and 20.04 systems, run:

    ./bin/first_time_ubuntu.sh

### Mac OS X
For Mac OS X systems, run:

    ./bin/first_time_macosx.sh

### Other Operating Systems
The important things to install:
  * Java 11
  * Maven 3
  * PostgreSQL 10+ (12+ preferred)
  * Redis
  * Python 3.8+
  * Packer (try `bin/install_packer.sh` first, it might work fine)
  * Required tools: curl, jq, uuid, sha256sum, openssl, ssh, scp, rsync, npm, webpack, zip, unzip

Look at the `first_time_ubuntu.sh` script and ensure you've basically done all that it does,
including creating PostgreSQL users/databases.

If you get Bubble working on another platform and are feeling generous, please create a
`./bin/first_time_some_new_os.sh` file to capture your work and submit a pull request so
it can be shared with others.
