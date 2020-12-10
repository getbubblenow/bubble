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
Vagrant.configure("2") do |config|
  config.vm.box = "ubuntu/focal64"

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
      s.env = {
        BUBBLE_PORT: port,
        LETSENCRYPT_EMAIL: ENV['LETSENCRYPT_EMAIL']
      }
      s.privileged = false
      s.inline = <<-SHELL
         # Copy shared bubble dir to ${HOME}
         cd ${HOME} && rsync -azc --exclude="**/target/**" /vagrant . && mv vagrant bubble

         # Initialize the system
         cd ${HOME}/bubble && ./bin/first_time_ubuntu.sh

         # Clone and build all dependencies
         SKIP_BUBBLE_BUILD=1 ./bin/first_time_setup.sh

         # Build the bubble jar
         BUBBLE_PRODUCTION=1 mvn -DskipTests=true -Dcheckstyle.skip=true -Pproduction clean package 2>&1 | tee /tmp/build.log

         # Write bubble API port
         echo \"export BUBBLE_SERVER_PORT=${BUBBLE_PORT}\" > ~/.bubble.env

         # Allow website to be loaded from disk
         echo \"export BUBBLE_ASSETS_DIR=$(pwd)/bubble-web/dist\" >> ~/.bubble.env

         # Add bubble bin to PATH
         echo \"export PATH=\${PATH}:${HOME}/bubble/bin\" >> ~/.bashrc

         # Ensure Bubble API listens on all addresses
         echo \"export BUBBLE_LISTEN_ALL=true\" >> ~/.bashrc

         # Set LETSENCRYPT_EMAIL is defined
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