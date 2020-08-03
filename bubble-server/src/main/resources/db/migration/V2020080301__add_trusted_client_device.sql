
DELETE FROM trusted_client;
ALTER TABLE ONLY trusted_client ADD COLUMN device character varying(100) NOT NULL;

CREATE INDEX trusted_client_idx_device ON trusted_client USING btree (device);
CREATE UNIQUE INDEX trusted_client_uniq_account_device ON trusted_client USING btree (account, device);

ALTER TABLE ONLY trusted_client ADD CONSTRAINT trusted_client_fk_device FOREIGN KEY (device) REFERENCES device(uuid);
