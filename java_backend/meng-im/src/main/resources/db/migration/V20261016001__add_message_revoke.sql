ALTER TABLE private_message ADD COLUMN revoked_time DATETIME(3) NULL AFTER client_msg_id;
