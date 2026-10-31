ALTER TABLE companion_message
  ADD COLUMN voice_path VARCHAR(80) NULL,
  ADD COLUMN voice_ms INT NULL;
