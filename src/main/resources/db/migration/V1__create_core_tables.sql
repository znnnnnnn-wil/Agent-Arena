CREATE TABLE aa_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    nickname VARCHAR(64) NOT NULL COMMENT '用户昵称',
    visitor_token VARCHAR(128) NOT NULL COMMENT '游客访问令牌摘要',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_visitor_token (visitor_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE aa_model_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模型配置ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    name VARCHAR(64) NOT NULL COMMENT '配置名称',
    provider VARCHAR(32) NOT NULL COMMENT '模型供应商',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
    base_url VARCHAR(512) NULL COMMENT '自定义服务地址',
    encrypted_api_key TEXT NULL COMMENT '加密后的API Key',
    model_options JSON NULL COMMENT '模型参数',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_model_config_user (user_id),
    CONSTRAINT fk_model_config_user FOREIGN KEY (user_id) REFERENCES aa_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模型配置表';

CREATE TABLE aa_agent_profile (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '智能体人格ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    name VARCHAR(64) NOT NULL COMMENT '人格名称',
    description VARCHAR(512) NULL COMMENT '人格描述',
    personality JSON NOT NULL COMMENT '人格配置',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_agent_profile_user (user_id),
    CONSTRAINT fk_agent_profile_user FOREIGN KEY (user_id) REFERENCES aa_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能体人格表';

CREATE TABLE aa_game (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '游戏ID',
    owner_user_id BIGINT NOT NULL COMMENT '创建用户ID',
    name VARCHAR(128) NOT NULL COMMENT '游戏名称',
    mode VARCHAR(32) NOT NULL COMMENT '游戏模式',
    status VARCHAR(32) NOT NULL COMMENT '游戏状态',
    current_phase VARCHAR(64) NULL COMMENT '当前阶段',
    current_round INT NOT NULL DEFAULT 0 COMMENT '当前轮次',
    max_players INT NOT NULL COMMENT '最大玩家数',
    public_game TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否公开',
    rule_config JSON NOT NULL COMMENT '规则配置',
    random_seed BIGINT NOT NULL COMMENT '随机种子',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    start_time DATETIME(3) NULL COMMENT '开始时间',
    finish_time DATETIME(3) NULL COMMENT '结束时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_game_owner (owner_user_id),
    KEY idx_game_status_public (status, public_game, create_time),
    CONSTRAINT fk_game_owner FOREIGN KEY (owner_user_id) REFERENCES aa_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏表';

CREATE TABLE aa_game_player (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '游戏玩家ID',
    game_id BIGINT NOT NULL COMMENT '游戏ID',
    seat_no INT NOT NULL COMMENT '座位号',
    player_type VARCHAR(16) NOT NULL COMMENT '玩家类型',
    user_id BIGINT NULL COMMENT '真人用户ID',
    model_config_id BIGINT NULL COMMENT '模型配置ID',
    agent_profile_id BIGINT NULL COMMENT '人格配置ID',
    display_name VARCHAR(64) NOT NULL COMMENT '显示名称',
    status VARCHAR(32) NOT NULL COMMENT '玩家状态',
    role_code VARCHAR(64) NULL COMMENT '游戏角色代码',
    public_state JSON NULL COMMENT '公开状态',
    private_state JSON NULL COMMENT '私有状态',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_game_player_seat (game_id, seat_no),
    KEY idx_game_player_game_status (game_id, status),
    CONSTRAINT fk_game_player_game FOREIGN KEY (game_id) REFERENCES aa_game (id),
    CONSTRAINT fk_game_player_user FOREIGN KEY (user_id) REFERENCES aa_user (id),
    CONSTRAINT fk_game_player_model FOREIGN KEY (model_config_id) REFERENCES aa_model_config (id),
    CONSTRAINT fk_game_player_profile FOREIGN KEY (agent_profile_id) REFERENCES aa_agent_profile (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏玩家表';

CREATE TABLE aa_game_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    game_id BIGINT NOT NULL COMMENT '游戏ID',
    sequence_no BIGINT NOT NULL COMMENT '游戏内事件序号',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    actor_player_id BIGINT NULL COMMENT '行动玩家ID',
    round_no INT NOT NULL DEFAULT 0 COMMENT '发生轮次',
    phase VARCHAR(64) NULL COMMENT '发生阶段',
    payload JSON NOT NULL COMMENT '完整事件载荷',
    public_payload JSON NULL COMMENT '公开事件载荷',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_game_event_sequence (game_id, sequence_no),
    KEY idx_game_event_game_time (game_id, create_time),
    CONSTRAINT fk_game_event_game FOREIGN KEY (game_id) REFERENCES aa_game (id),
    CONSTRAINT fk_game_event_actor FOREIGN KEY (actor_player_id) REFERENCES aa_game_player (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏事件表';

CREATE TABLE aa_game_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '快照ID',
    game_id BIGINT NOT NULL COMMENT '游戏ID',
    sequence_no BIGINT NOT NULL COMMENT '快照对应事件序号',
    game_state JSON NOT NULL COMMENT '完整游戏状态',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_game_snapshot_sequence (game_id, sequence_no),
    CONSTRAINT fk_game_snapshot_game FOREIGN KEY (game_id) REFERENCES aa_game (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='游戏状态快照表';

CREATE TABLE aa_model_invocation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模型调用ID',
    game_id BIGINT NULL COMMENT '游戏ID',
    player_id BIGINT NULL COMMENT '游戏玩家ID',
    model_config_id BIGINT NOT NULL COMMENT '模型配置ID',
    provider VARCHAR(32) NOT NULL COMMENT '供应商',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
    request_id VARCHAR(128) NULL COMMENT '供应商请求ID',
    status VARCHAR(32) NOT NULL COMMENT '调用状态',
    input_tokens INT NULL COMMENT '输入Token',
    output_tokens INT NULL COMMENT '输出Token',
    latency_ms BIGINT NULL COMMENT '调用耗时',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    error_message VARCHAR(1024) NULL COMMENT '脱敏错误信息',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_model_invocation_game (game_id, create_time),
    KEY idx_model_invocation_config (model_config_id, create_time),
    CONSTRAINT fk_model_invocation_game FOREIGN KEY (game_id) REFERENCES aa_game (id),
    CONSTRAINT fk_model_invocation_player FOREIGN KEY (player_id) REFERENCES aa_game_player (id),
    CONSTRAINT fk_model_invocation_config FOREIGN KEY (model_config_id) REFERENCES aa_model_config (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模型调用记录表';
