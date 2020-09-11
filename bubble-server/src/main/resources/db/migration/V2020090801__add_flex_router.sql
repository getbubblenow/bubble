CREATE TABLE public.flex_router (
    uuid character varying(100) NOT NULL,
    ctime bigint NOT NULL,
    mtime bigint NOT NULL,
    account character varying(100) NOT NULL,
    active boolean NOT NULL,
    device character varying(100) NOT NULL,
    enabled boolean NOT NULL,
    initialized boolean NOT NULL,
    ip character varying(50) NOT NULL,
    key character varying(10100) NOT NULL,
    key_hash character varying(100) NOT NULL,
    port integer NOT NULL,
    token character varying(200) NOT NULL
);

ALTER TABLE ONLY flex_router ADD CONSTRAINT flex_router_pkey PRIMARY KEY (uuid);

CREATE INDEX flex_router_idx_account ON flex_router USING btree (account);
CREATE INDEX flex_router_idx_device ON flex_router USING btree (device);
CREATE INDEX flex_router_idx_active ON flex_router USING btree (active);
CREATE INDEX flex_router_idx_enabled ON flex_router USING btree (enabled);
CREATE INDEX flex_router_idx_initialized ON flex_router USING btree (initialized);
CREATE INDEX flex_router_idx_ip ON flex_router USING btree (ip);
CREATE UNIQUE INDEX flex_router_uniq_account_ip ON flex_router USING btree (account, ip);
CREATE UNIQUE INDEX flex_router_uniq_key_hash ON flex_router USING btree (key_hash);
CREATE UNIQUE INDEX flex_router_uniq_port ON flex_router USING btree (port);

ALTER TABLE ONLY flex_router ADD CONSTRAINT flex_router_fk_account FOREIGN KEY (account) REFERENCES account(uuid);
ALTER TABLE ONLY flex_router ADD CONSTRAINT flex_router_fk_device FOREIGN KEY (device) REFERENCES device(uuid);
