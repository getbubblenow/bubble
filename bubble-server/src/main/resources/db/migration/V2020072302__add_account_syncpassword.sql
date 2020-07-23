ALTER TABLE account ADD COLUMN sync_password BOOLEAN;
UPDATE account set sync_password = true;
