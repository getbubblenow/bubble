# Install Required System Software
You only need to do this if you're [running from a binary distribution](run-binary.md)
or [building and running from source](dev.md).

If you're using the [Bubble Docker Launcher](docker-launcher.md), skip this step.

## Why Do This?
You'll need to install some software for Bubble to work correctly.

Bubble needs a PostgreSQL database, Redis, and a bunch of command line tools installed. 

It ensures that the appropriate packages are installed and proper databases and database users exist.

You only need to install system software once, ever, on a given system.

### Ubuntu
For Ubuntu 18.04 and 20.04 systems, run:

    ./bin/first_time_ubuntu.sh

### Mac OS X
For Mac OS X systems, run:

    ./bin/first_time_macosx.sh

### Other Operating Systems
If you are running a different OS or distribution, copy `first_time_ubuntu.sh` to something like:
                                
    ./bin/first_time_myoperatingsystem.sh
                                
Then edit it such that all the same packages get installed.

Submit a pull request, and we'll add support for your operating system to the main repository.

