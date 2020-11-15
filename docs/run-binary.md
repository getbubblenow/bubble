# Run Bubble from a Binary Distribution
The setup instructions below assume you are running Ubuntu 20.04 or Mac OS X.

If you're running another flavor of Linux, you can probably figure out how to get things working.

If you're running Windows, things might be more difficult.

## Download a Bubble Distribution
Download the [latest Bubble release](https://jenkins.bubblev.org/public/releases/bubble/latest/bubble.zip)

Open a command-line terminal.

Unzip the file that you downloaded:

    unzip bubble.zip

Change directories into the directory containing the files that were unzipped:

    cd bubble-Adventure_1.x.x     # replace `Adventure_1.x.x` with the version that you downloaded

## Install System Software
You'll need to install some software for Bubble to work correctly.

For Ubuntu systems, run:

    ./bin/first_time_ubuntu.sh

For Mac OS X systems, run:

    ./bin/first_time_macosx.sh

You only need to run this command once, ever, on a given system.
It ensures that the appropriate packages are installed and proper databases and database users exist.

If you are running a different OS or distribution, copy `first_time_ubuntu.sh` to something like:
                                
    ./bin/first_time_myoperatingsystem.sh
                                
Then edit it such that all the same packages get installed.

Submit a pull request, and we'll add support for your operating system to the main repository.

## Run Bubble
Continue by running your Bubble as a [Local Launcher](local-launcher.md).
