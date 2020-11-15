Bubble Activation
=================
The very first time Bubble runs it has a blank database, nothing has been defined.

This is a Bubble that is awaiting activation.

Activation defines the initial data required to run a Bubble launcher. This includes the initial admin password,
cloud services, and DNS domains.

## Cloud Accounts
In order to activate your Local Launcher, you'll need accounts and/or API keys from several cloud providers.

Have these account credentials handy. Be prepared to sign up for new accounts where needed.

## Activation Notes

### Root User Name
Do not change the name of the root user. Certain parts of the system have hardcoded references to "root" as the
name of the admin user.

### Required Clouds
You must define at least one cloud provider for each of these categories:
  * DNS (Amazon Route53 or GoDaddy DNS)
  * Email (SMTP, SendGrid or Mailgun)
  * Compute (Vultr, DigitalOcean or Amazon EC2)  (note EC2 support is WIP, Vultr and DigitalOcean are stable)

### Initial Domain
The initial Domain you define during activation must be owned by you, and the DNS provider for this domain must be the
one selected in the "DNS Provider" drop-down box.

## Activate via Web UI
The browser-based admin UI should be displaying an "Activate" page. Complete the information on this page and submit the
data. The Bubble Launcher will create an initial "root" account and other basic system configurations. 

## Activate via command line
If you installed Bubble from a binary distribution or built it from source, you can also perform activation
using the Bubble command line tools.

Make a copy of the file `config/activation.json` and edit the copy. There are comments in the file to guide you.

To activate your Local Launcher Bubble, run this command:

    ./bin/bactivate /path/to/activation.json

After running the above, refresh the web page that opened when the server started. You should see a login page.

You can now login as the admin user using the email address `root@local.local` and the password from your `activation.json` file.
