ALTER TABLE ONLY app_matcher ADD COLUMN request_modifier boolean;
UPDATE app_matcher SET request_modifier = false;
UPDATE app_matcher SET request_modifier = true WHERE fqdn != '*';
UPDATE app_matcher SET request_modifier = true WHERE name = 'BubbleBlockMatcher';
ALTER TABLE ONLY app_matcher ALTER COLUMN request_modifier SET NOT NULL;
