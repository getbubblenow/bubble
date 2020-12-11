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
# After a box is launched, use `vagrant ssh` to log in.
#   - the code is in ${HOME}/bubble
#   - API environment file is ${HOME}/.bubble.env
#   - start the API server (local launcher) with run.sh
#   - access the webapp at http://127.0.0.1:${BUBBLE_PORT}/
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
# ### BUBBLE_VM_MEM
# This is the number of MB of memory to allocate for the VirtualBox VM.
# By default, this is set to 2048, thus Vagrantfile allocates 2GB of memory for the VM.
# You can build and run Bubble with less memory, but if you go as low as 1024 (1GB) you
# may have problems building the entire codebase from scratch.
#
Vagrant.configure("2") do |config|

  # Start with Ubuntu 20.04
  config.vm.box = "ubuntu/focal64"

  # Set name and memory
  mem = ENV['BUBBLE_VM_MEM'] || 2048
  config.vm.provider "virtualbox" do |v|
      v.name = 'Bubble_Vagrant'
      v.memory = mem
  end

  # Forward ports
  port = ENV['BUBBLE_PORT'] || 8090
  config.vm.network "forwarded_port", guest: port, host: port

  # Update system
  config.vm.provision :shell do |s|
      s.inline = <<-SHELL
         apt-get update -y
         apt-get upgrade -y
      SHELL
  end

  # Get dependencies and build everything
  config.vm.provision :shell do |s|
      s.env = { BUBBLE_PORT: port, LETSENCRYPT_EMAIL: ENV['LETSENCRYPT_EMAIL'] }
      s.privileged = false
      s.inline = <<-SHELL
         # Copy shared bubble dir to ${HOME}
         cd ${HOME} && rsync -azc --exclude="**/target/**" /vagrant . && mv vagrant bubble

         # Initialize the system
         cd ${HOME}/bubble && ./bin/first_time_ubuntu.sh

         # Clone submodules, build all dependencies, build the Bubble jar
         ./bin/first_time_setup.sh

         # Write bubble API port
         echo \"export BUBBLE_SERVER_PORT=${BUBBLE_PORT}\" > ~/.bubble.env

         # Allow web to be loaded from disk
         echo \"export BUBBLE_ASSETS_DIR=$(pwd)/bubble-web/dist\" >> ~/.bubble.env

         # Add bubble bin to PATH
         echo \"export PATH=\${PATH}:${HOME}/bubble/bin\" >> ~/.bashrc

         # Ensure Bubble API listens on all addresses
         echo \"export BUBBLE_LISTEN_ALL=true\" >> ~/.bashrc

         # Set LETSENCRYPT_EMAIL if defined
         if [[ -n \"${LETSENCRYPT_EMAIL}\" ]] ; then
           echo \"export LETSENCRYPT_EMAIL=${LETSENCRYPT_EMAIL}\" >> ~/.bubble.env
         fi

         # Create env file symlink for running tests
         cd ~ && ln -s .bubble.env .bubble-test.env

         # Done!
         echo "
         ==================================================================
         Bubble Vagrant Setup Complete
         ==================================================================

         Log in to this box using:

           vagrant ssh

         Once you are logged in:
          - the code is in ${HOME}/bubble
          - API environment file is ${HOME}/.bubble.env
          - start the API server (local launcher) with run.sh
          - access the webapp at http://127.0.0.1:${BUBBLE_PORT}/

         Enjoy!
         ==================================================================
         "
      SHELL
    end
end
