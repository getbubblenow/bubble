-- Copyright (c) 2020 Bubble, Inc.  All rights reserved.
-- For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/

CREATE TABLE public.account_payment_archived (
    uuid character varying(100) NOT NULL,
    ctime bigint NOT NULL,
    mtime bigint NOT NULL,
    account_uuid character varying(100),
    bills_json character varying NOT NULL,
    payment_methods_json character varying NOT NULL,
    payments_json character varying NOT NULL
);
ALTER TABLE account_payment_archived OWNER TO bubble;
ALTER TABLE account_payment_archived ADD CONSTRAINT account_payment_archived_pkey PRIMARY KEY (uuid);
ALTER TABLE account_payment_archived ADD CONSTRAINT account_payment_archived_uk_account UNIQUE (account_uuid);
CREATE UNIQUE INDEX account_payment_archived_uniq_account_uuid ON account_payment_archived USING btree (account_uuid);
