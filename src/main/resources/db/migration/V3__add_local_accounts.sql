ALTER TABLE aa_user
    ADD COLUMN username VARCHAR(32) NULL COMMENT '本地登录用户名' AFTER nickname,
    ADD COLUMN password_hash VARCHAR(100) NULL COMMENT 'BCrypt密码摘要' AFTER username,
    ADD UNIQUE KEY uk_user_username (username);
