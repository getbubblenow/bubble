# Bubble Activation
The very first time Bubble runs it has a blank database, nothing has been defined.

This is a Bubble that is awaiting activation.

Activation defines the initial data required to run a Bubble launcher. This includes the initial admin password,
cloud services, and DNS domains.

## Cloud Accounts
In order to activate your Local Launcher, you'll need accounts and/or API keys from several cloud providers.

Have these account credentials handy. Be prepared to sign up for new accounts where needed.

### Activate via Web UI
The browser-based admin UI should be displaying an "Activate" page. Complete the information on this page and submit the
data. The Bubble Launcher will create an initial "root" account and other basic system configurations. 

### Activate via command line
Make a copy of the file `config/activation.json` and edit the copy. There are comments in the file to guide you.

To activate your Local Launcher Bubble, run this command:

    ./bin/bactivate /path/to/activation.json

After running the above, refresh the web page that opened when the server started. You should see a login page.

You can now login as the admin user using the email address `root@local.local` and the password from your `activation.json` file.
