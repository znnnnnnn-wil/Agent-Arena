package com.zn.agentarena.modules.turing.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 真人操作的 seatNo 边界 DTO。数据库 playerId 字段仅用于显式拒绝客户端注入，
 * 从不会被转换为领域动作。
 */
@Data
public class TuringHuntHumanActionRequest {

    /** 动作类型 */
    @NotBlank
    @Size(max = 32)
    private String action;

    /** 回答内容 */
    @Size(max = 90)
    private String answer;

    /** primarySuspectSeatNo 字段 */
    @Min(1)
    @Max(6)
    private Integer primarySuspectSeatNo;

    @Valid
    @Size(max = 2)
    /** 怀疑对象的座位号列表 */
    private List<@Min(1) @Max(6) Integer> suspectSeatNos;

    @Size(max = 3)
    /** 理由标签列表 */
    private List<@Size(max = 32) String> reasonTags;

    /** 理由摘要 */
    @Size(max = 50)
    private String reasonSummary;

    /** targetSeatNo 字段 */
    @Min(1)
    @Max(6)
    private Integer targetSeatNo;

    /** 问题内容 */
    @Size(max = 45)
    private String question;

    /** 回应内容 */
    @Size(max = 70)
    private String response;

    /** 辩护内容 */
    @Size(max = 80)
    private String defense;

    /** counterSuspectSeatNo 字段 */
    @Min(1)
    @Max(6)
    private Integer counterSuspectSeatNo;

    /** 玩家ID */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Object playerId;

    /** 行动玩家ID */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Object actorPlayerId;
}
