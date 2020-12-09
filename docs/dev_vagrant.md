Bubble Vagrant Developer Setup
==============================
[Vagrant](https://www.vagrantup.com/) is a popular way to run pre-packaged software on virtual machines
like VirtualBox and VMware.

# Requirements
Before starting a Bubble Vagrant box, you'll need install some software if you don't already have it:

 * [VirtualBox](https://www.virtualbox.org/)
 * [Vagrant](https://www.vagrantup.com/)

# Configuration
There are a few environment variables you can use to configure how the Vagrant box is initialized.

## Port Selection
Do you have anything already listening on port 8090? If so, set the `BUBBLE_PORT` environment
variable to some free port where the Bubble API can listen. Otherwise, it defaults to 8090.

## Local Launcher and SSL Certificates
Do you want to use this setup as a local launcher to start new Bubbles? If so, set
the `LETSENCRYPT_EMAIL` environment variable to an email address that will be registered with
[LetsEncrypt](https://letsencrypt.org/) when your Bubbles need to install SSL certificates.

## Other Settings
Read the Bubble [Vagrantfile](../Vagrantfile) for full information on all settings.

# Starting
To start the Bubble Vagrant box, run:

```shell script
vagrant up
```

To start the Bubble Vagrant box with some environment variables (for example):

```shell script
LETSENCRYPT_EMAIL=someone@example.com BUBBLE_PORT=8080 vagrant up
```

The first time `vagrant up` runs it will take a long time to complete, since it needs to
download and build a lot of stuff. For future runs of `vagrant up`, startup times will be
much faster.

When the command completes, you'll have a Vagrant box with the Bubble source code and all
dependencies fully built and ready to run a local launcher.

# Stopping
To stop the Vagrant box, run:

```shell script
vagrant halt
```

# Deleting
To delete the Vagrant box, run:

```shell script
vagrant destroy [-f]
```

If you supply `-f` then you will not be asked for confirmation.

# Logging In
When your Vagrant box is ready, you can login to it:

```shell script
vagrant ssh
```

## Developing
You can develop directly on the Vagrant box by editing the source files in `${HOME}/bubble`, then
building and running from there.

Alternatively, you can develop locally on the host and periodically synchronize your source
and/or build assets to the Vagrant box, where you then (maybe build and) run the Bubble API.

The `/vagrant` directory on the Vagrant guest box is a shared folder. On the host side, it 
is the directory that the `Vagrantfile` is in (the top-level of the bubble repository).

For various reasons, the Vagrant box does not build and work in this directory directly. Instead,
when the box is created, `/vagrant` is copied to `${HOME}/bubble` and we run things from there.

So, if you are doing development and building locally (on the host), and want to see
your changes on the Vagrant box (guest), then copy your bubble jar from the host to the guest:

```shell script
cp /vagrant/bubble-server/target/bubble-server-*.jar ${HOME}/bubble/bubble-server/target/
```

To synchronize the entire directory (for example if you're editing sources on
the host and building the jar on the guest):
```shell script
rsync -avzc /vagrant/* /vagrant/.* ${HOME}/bubble/
```

## What's Next
Continue reading the [Bubble Developer Guide](dev.md) for information
on how to update the source code, reset the database, and more.
