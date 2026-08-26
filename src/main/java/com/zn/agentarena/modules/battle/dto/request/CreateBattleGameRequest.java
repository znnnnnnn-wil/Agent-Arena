package com.zn.agentarena.modules.battle.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建大逃杀游戏请求。 */
@Data
public class CreateBattleGameRequest {
    /** 对局名称。 */
    @NotBlank
    @Size(max = 128)
    private String name;
    /** 是否允许其他用户公开查看或加入该对局。 */
    @NotNull
    private Boolean publicGame;
    /** 参与对局的 AI 模型配置 ID 列表。 */
    private List<@Positive Long> modelConfigIds;
    /** 每个席位的人格配置 ID；为空表示使用中性人格。 */
    private List<@Positive Long> agentProfileIds;
    /** 每个席位的显示名称；为空时使用默认名称。 */
    private List<@Size(max = 64) String> playerDisplayNames;
    /** 对局玩家总人数，默认八人。 */
    @Min(6)
    @Max(10)
    private Integer playerCount = 8;
    /** 最大回合数。 */
    @Min(12)
    @Max(30)
    private Integer maxRounds = 20;
}
