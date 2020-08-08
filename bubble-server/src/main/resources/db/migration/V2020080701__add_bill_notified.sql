ALTER TABLE ONLY bill ADD COLUMN notified boolean;
UPDATE bill SET notified = false;
ALTER TABLE ONLY bill ALTER COLUMN notified SET NOT NULL;
