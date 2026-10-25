ALTER TABLE companion_message ADD move VARCHAR(20), ADD inner_thought VARCHAR(120), ADD lint JSON, ADD feeling_level INT NOT NULL DEFAULT 1;
ALTER TABLE companion_bond ADD user_style JSON;
