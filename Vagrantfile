# -*- mode: ruby -*-
# vi: set ft=ruby :
# Copyright (c) 2020 Bubble, Inc.  All rights reserved. For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
#
# ==================
# Bubble Vagrantfile
# ==================
# Running `vagrant up` will create a full Bubble development environment including all
# dependencies, and build all the code and website. You'll be ready to run a local
# launcher or do dev work on the Bubble codebase.
#
# There are a few environment variables that determine how the box is initialized,
# described below.
#
# ## Environment Variables
#
# ### LETSENCRYPT_EMAIL
# If you specify the LETSENCRYPT_EMAIL environment variable, then `vagrant up` will also
# start a Local Launcher (see docs/local-launcher.md) which is your starting point for
# launching new Bubbles. You can always change this later by editing the `~/.bubble.env`
# file after the Vagrant box is setup.
#
# ### BUBBLE_PORT
# By default, Bubble will listen on port 8090.
# If something else is already using that port on your computer, `vagrant up` will fail.
# Set the `BUBBLE_PORT` environment variable to another port, and Bubble will listen on
# that port instead.
#
# ### BUBBLE_PUBLIC_PORT
# By default, Vagrant will only expose the Bubble API port (8090 or whatever BUBBLE_PORT
# is set to) as listening on 127.0.0.1
# If you want the Bubble API port to be listening on all addresses (bind to 0.0.0.0)
# then set the BUBBLE_PUBLIC_PORT environment variable to any value except 'false'
#
Vagrant.configure("2") do |config|
  config.vm.box = "ubuntu/focal64"

  # You can access the launcher on port 8090 (or BUBBLE_PORT) but only on 127.0.0.1
  # If you want to allow outside access to port 8090 (listen on 0.0.0.0), use the version below
  if not ENV.include? 'BUBBLE_PUBLIC_PORT' or ENV['BUBBLE_PUBLIC_PORT'] == 'false' do
      config.vm.network "forwarded_port", guest: 8090, host: ENV['BUBBLE_PORT'] || 8090, host_ip: "127.0.0.1"
  else
      # Anyone who can reach port 8090 on this system will be able to access the launcher
      config.vm.network "forwarded_port", guest: 8090, host: ENV['BUBBLE_PORT'] || 8090
  end

  # Update system
  config.vm.provision :shell do |s|
      s.inline = <<-SHELL
         apt-get update -y
         apt-get upgrade -y
      SHELL
  end

  # Get dependencies and build everything
  config.vm.provision :shell do |s|
      s.env = { LETSENCRYPT_EMAIL: ENV['LETSENCRYPT_EMAIL'] }
      s.privileged = false
      s.inline = <<-SHELL
         cd /vagrant

         # Initialize the system
         ./bin/first_time_ubuntu.sh

         # Clone and build all dependencies
         SKIP_BUBBLE_BUILD=1 ./bin/first_time_setup.sh

         # Build the bubble jar
         BUBBLE_PRODUCTION=1 mvn -DskipTests=true -Dcheckstyle.skip=true -Pproduction clean package

         # Write bubble API port
         echo \"export BUBBLE_SERVER_PORT=${BUBBLE_PORT}\" > ~/.bubble.env

         # Allow website to be loaded from disk
         echo \"export BUBBLE_ASSETS_DIR=$(pwd)/bubble-web/dist\" >> ~/.bubble.env

         # Add bubble bin to PATH
         echo \"export PATH=\${PATH}:${HOME}/bubble/bin\" >> ~/.bashrc

         # Set LETSENCRYPT_EMAIL is defined
         if [[ -n \"${LETSENCRYPT_EMAIL}\" ]] ; then
           echo \"export LETSENCRYPT_EMAIL=${LETSENCRYPT_EMAIL}\" >> ~/.bubble.env
         fi

         # Create env file symlink for running tests
         cd ~ && ln -s .bubble.env .bubble-test.env
      SHELL
    end
end
