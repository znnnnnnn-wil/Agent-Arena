ALTER TABLE aa_agent_profile
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已删除' AFTER personality;

CREATE INDEX idx_agent_profile_user_deleted
    ON aa_agent_profile (user_id, deleted, create_time);

CREATE TABLE aa_lineup_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '阵容模板ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    name VARCHAR(64) NOT NULL COMMENT '模板名称',
    mode VARCHAR(32) NOT NULL COMMENT '游戏模式',
    template_config JSON NOT NULL COMMENT '规则与席位配置快照',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_lineup_template_user (user_id, update_time),
    CONSTRAINT fk_lineup_template_user FOREIGN KEY (user_id) REFERENCES aa_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI阵容模板表';
