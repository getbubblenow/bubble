ALTER TABLE account DROP COLUMN data_updates;
ALTER TABLE account DROP COLUMN new_stuff;
ALTER TABLE account RENAME driver_updates TO jar_updates;
ALTER TABLE rule_driver DROP COLUMN needs_update;
ALTER TABLE bubble_app DROP COLUMN needs_update;
