# Bubble: a privacy-first VPN

Bubble helps you start and manage your own private VPN.

It also adds tools to this VPN to improve your Internet experience by modifying your traffic: to
remove ads, block malware, and much more.

## Operating System Support
Once your Bubble is running, any device can connect to it: Windows, Linux, Mac, iOS, Android; if it supports VPN connections,
it will probably work just fine.

However, to launch your own Bubble using this software, you will need a Linux machine to run the launcher.
It *probably* works on MacOS, but it has not been tested and there are likely to be issues. Pull requests are welcome!

If you'd like to enjoy all the benefits of Bubble without going through this hassle, please try out the Bubble launching
service available on [bubblev.com](https://bubblev.com). Any Bubble you launch from [bubblev.com](https://bubblev.com)
will also be "yours only" -- all Bubbles disconnect from their launcher during configuration.

## Getting Started

### Install OpenJDK 11
Install [Java 11](https://openjdk.java.net/install/) from OpenJDK.
It will probably be easier to install using an OS package, for example `sudo apt install openjdk-11-jre-headless`

### Install PostgreSQL and Redis
Install [PostgreSQL 10](https://www.postgresql.org/download/) if it is not installed on your system.
It will probably be easier to install using an OS package, for example `sudo apt install postgresql`

Install [Redis](https://redis.io/download) if it is not already installed on your system.
It will probably be easier to install using an OS package, for example `sudo apt install redis`

### Configure PostgreSQL
The Bubble launcher connects to a PostgreSQL database named 'bubble' as the PostgreSQL user 'bubble'.

If your current OS user account has permissions to create PostgreSQL databases and users, you can skip this step
since the database and user will be created upon startup.

Otherwise, please either:
  * Update the PostgreSQL `pg_hba.conf` file to allow your current OS user to create databases and DB users, OR
  * Create a PostgreSQL database named `bubble` and a database user named `bubble`. Set a password for the `bubble` user,
  and set the environment variable `BUBBLE_PG_PASSWORD` to this password when starting the Bubble launcher.

### Download a Bubble Distribution
Download and unzip the latest [Bubble Distribution ZIP](https://bubblev.com/download).

### Start the Bubble launcher
Run the `./bin/run.sh` script to start the Bubble launcher. Once the server is running, it will try to open a browser window
to continue configuration. It will also print out the URL, so if the browser doesn't start correctly, you can paste this
into your browser's location bar.

### Activate your local Bubble
Your Bubble is running locally in a "blank" mode. It needs an initial "root" account and some basic services configured.

#### Activate via Web UI
The browser-based admin UI should be displaying an "Activate" page. Complete the information on this page and submit the
data. The Bubble Launcher will create an initial "root" account and other basic system configurations. 

#### Activate via command line
Copy the file in `config/activation.json`, then edit the file. There are comments in the file to guide you.
After saving the updated file, run this command:

   `./bin/bactivate /path/to/activation.json`

### Launch a new Bubble!
Using the web UI, click "Bubbles", select "New Bubble". Fill out and submit the New Bubble form, and your Bubble will be created!
