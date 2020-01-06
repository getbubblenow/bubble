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

### Download a Bubble Distribution

### Install PostgreSQL and Redis
Install [PostgreSQL](https://www.postgresql.org/download/) if it is not installed on your system.
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

### Start the Bubble launcher
Running a Bubble locally 

### Activate your local Bubble

#### Activate using the Web UI

#### Activate using the command line

### Configure Cloud Services
