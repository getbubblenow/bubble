# -*- mode: ruby -*-
# vi: set ft=ruby :
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# ==================
# Bubble Vagrantfile
# ==================
# Running `vagrant up` will create a full Bubble development environment, and
# optionally start a local launcher.
#
# ## Environment Variables
#
# ### LETSENCRYPT_EMAIL
# If you specify the LETSENCRYPT_EMAIL environment variable, then `vagrant up` will also
# start a Local Launcher (see docs/local-launcher.md) which is your starting point for
# launching new Bubbles.
#
# ### BUBBLE_PORT
# By default, Bubble will listen on port 8090.
# If something else is already using that port on your computer, `vagrant up` will fail.
# Set the `BUBBLE_PORT` environment variable to another port, and Bubble will listen on
# that port instead.
#
# ### BUBBLE_TAG
# By default, the Vagrant box will run the bleeding edge (`master` branch) of Bubble.
# Set the `BUBBLE_TAG` environment variable to the name of a git branch or tag to check
# out instead.
#
#
Vagrant.configure("2") do |config|
  config.vm.box = "ubuntu/focal64"

  # You can access the launcher on port 8090 (or BUBBLE_PORT) but only on 127.0.0.1
  # If you want to allow outside access to port 8090 (listen on 0.0.0.0), use the version below
  config.vm.network "forwarded_port", guest: 8090, host: ENV['BUBBLE_PORT'] || 8090, host_ip: "127.0.0.1"

  # Anyone who can reach port 8090 on this system will be able to access the launcher
  # config.vm.network "forwarded_port", guest: 8090, host: ENV['BUBBLE_PORT'] || 8090

  config.vm.provision :shell do |s|
      s.inline = <<-SHELL
         apt-get update -y
         apt-get upgrade -y
      SHELL
  end
  config.vm.provision :shell do |s|
      s.env = {
          LETSENCRYPT_EMAIL: ENV['LETSENCRYPT_EMAIL'],
          BUBBLE_TAG: ENV['BUBBLE_TAG'] || 'master'
      }
      s.privileged = false
      s.inline = <<-SHELL
         # Get the code
         git clone https://git.bubblev.org/bubblev/bubble.git
         cd bubble && git fetch && git pull origin ${BUBBLE_TAG}

         # Initialize the system
         ./bin/first_time_ubuntu.sh

         # Clone and build all dependencies
         SKIP_BUBBLE_BUILD=1 ./bin/first_time_setup.sh

         # Build the bubble jar
         MVN_QUIET=""
         BUBBLE_PRODUCTION=1 mvn ${MVN_QUIET} -Pproduction clean package

         # Start the Local Launcher if LETSENCRYPT_EMAIL is defined
         if [[ -n \"${LETSENCRYPT_EMAIL}\" ]] ; then
           echo \"export LETSENCRYPT_EMAIL=${LETSENCRYPT_EMAIL}\" > bubble.env
           # ./bin/run.sh bubble.env
         fi
      SHELL
    end
end
