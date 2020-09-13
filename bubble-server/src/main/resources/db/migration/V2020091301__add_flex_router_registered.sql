ALTER TABLE flex_router ADD COLUMN registered BOOLEAN;
UPDATE flex_router SET registered = false;
ALTER TABLE flex_router ALTER COLUMN registered SET NOT NULL;
