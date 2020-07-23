ALTER TABLE app_site ADD COLUMN max_security_hosts_json VARCHAR(5000);
ALTER TABLE app_site ADD COLUMN enable_max_security_hosts BOOLEAN;
UPDATE app_site SET max_security_hosts_json = '["twitter.com","*.twitter.com","*.twimg.com","t.co"]' WHERE name = 'Twitter';
UPDATE app_site SET enable_max_security_hosts = true WHERE name = 'Twitter';
