CREATE TABLE trusted_client (
   uuid character varying(100) NOT NULL,
   ctime bigint NOT NULL,
   mtime bigint NOT NULL,
   account character varying(100) NOT NULL,
   trust_id character varying(200) NOT NULL
);

ALTER TABLE ONLY trusted_client ADD CONSTRAINT trusted_client_pkey PRIMARY KEY (uuid);

CREATE INDEX trusted_client_idx_account ON trusted_client USING btree (account);
CREATE UNIQUE INDEX trusted_client_uniq_account_trust_id ON trusted_client USING btree (account, trust_id);

ALTER TABLE ONLY trusted_client ADD CONSTRAINT trusted_client_fk_account FOREIGN KEY (account) REFERENCES account(uuid);
