#!/bin/bash

service mitmproxy stop && service nginx stop && certbot renew --standalone --non-interactive || echo "Error updating SSL certificates"
service mitmproxy restart
service nginx restart
