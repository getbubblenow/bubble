
ALTER TABLE ONLY account ADD CONSTRAINT account_fk_preferred_plan FOREIGN KEY (preferred_plan) REFERENCES bubble_plan(uuid);
