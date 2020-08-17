ALTER TABLE ONLY account ADD COLUMN show_block_stats boolean;
UPDATE account SET show_block_stats = true;
ALTER TABLE ONLY account ALTER COLUMN show_block_stats SET NOT NULL;
