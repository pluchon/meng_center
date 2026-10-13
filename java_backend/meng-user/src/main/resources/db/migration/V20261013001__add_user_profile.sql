ALTER TABLE user ADD COLUMN bio VARCHAR(50) NULL, ADD COLUMN username_update_time DATETIME NULL;
ALTER TABLE user DROP CHECK ck_user_one_contact;
