package com.zn.agentarena.dao.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.handler.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 游戏状态快照表(GameSnapshot)，用于快速恢复和回放对局。
 *
 * @author zhangxin
 * @since 2026-07-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("aa_game_snapshot")
public class GameSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 快照ID */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 游戏ID */
    private Long gameId;

    /** 快照对应事件序号 */
    private Long sequenceNo;

    /** 完整游戏状态 */
    @Column(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> gameState;

    /** 创建时间 */
    @Column(onInsertValue = "now(3)")
    private LocalDateTime createTime;
}
