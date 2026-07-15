ALTER TABLE aa_model_config
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已删除' AFTER enabled;

CREATE INDEX idx_model_config_user_deleted
    ON aa_model_config (user_id, deleted, create_time);
