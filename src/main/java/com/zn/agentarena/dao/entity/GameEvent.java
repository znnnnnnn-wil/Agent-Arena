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
 * 游戏事件表(GameEvent)，存储对局内发生的全部事件。
 *
 * @author zhangxin
 * @since 2026-07-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("aa_game_event")
public class GameEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件ID */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 游戏ID */
    private Long gameId;

    /** 游戏内事件序号 */
    private Long sequenceNo;

    /** 事件类型 */
    private String eventType;

    /** 行动玩家ID */
    private Long actorPlayerId;

    /** 发生轮次 */
    private Integer roundNo;

    /** 发生阶段 */
    private String phase;

    /** 完整事件载荷 */
    @Column(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;

    /** 对普通观众公开的事件载荷 */
    @Column(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> publicPayload;

    /** 创建时间 */
    @Column(onInsertValue = "now(3)")
    private LocalDateTime createTime;
}
