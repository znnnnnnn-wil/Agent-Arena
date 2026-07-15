package com.zn.agentarena.dao.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户表(User)，存储游客和正式用户的基础身份。
 *
 * @author zhangxin
 * @since 2026-07-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("aa_user")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 用户昵称 */
    private String nickname;

    /** 登录用户名；游客账号为空。 */
    private String username;

    /** BCrypt 密码摘要；游客账号为空。 */
    private String passwordHash;

    /** 游客访问令牌摘要 */
    private String visitorToken;

    /** 用户状态 */
    private String status;

    /** 创建时间 */
    @Column(onInsertValue = "now(3)")
    private LocalDateTime createTime;

    /** 更新时间 */
    @Column(onInsertValue = "now(3)", onUpdateValue = "now(3)")
    private LocalDateTime updateTime;
}
