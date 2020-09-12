ALTER TABLE sent_notification ALTER COLUMN payload_json TYPE VARCHAR(500100);
ALTER TABLE received_notification ALTER COLUMN payload_json TYPE VARCHAR(500100);
