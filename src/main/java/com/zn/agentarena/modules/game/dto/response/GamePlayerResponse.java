package com.zn.agentarena.modules.game.dto.response;

import com.zn.agentarena.common.enums.PlayerType;
import com.zn.agentarena.modules.game.domain.PlayerStatus;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.List;

/** 对局席位响应。 */
@Data
@Builder
public class GamePlayerResponse {
    /** 游戏玩家记录 ID。 */
    private Long id;
    /** 玩家座位号。 */
    private Integer seatNo;
    /** 玩家来源类型，如真人或 AI。 */
    private PlayerType playerType;
    /** 适合公开展示的模型供应商，不包含连接地址或密钥。 */
    private ModelProvider modelProvider;
    /** 适合公开展示的模型名称。 */
    private String modelName;
    /** 用户为模型配置设置的展示名称。 */
    private String modelDisplayName;
    /** 未绑定外部模型时为 true，前端统一显示为“规则 AI”。 */
    private Boolean localStrategy;
    /** 人格展示信息不包含隐藏指令，可用于公开区分 Agent。 */
    private String agentProfileName;
    private String agentProfileCode;
    private String agentDescription;
    private List<String> agentTraits;
    /** Agent表达风格 */
    private String agentSpeakingStyle;
    /** Agent风险偏好 */
    private String agentRiskPreference;
    /** Agent推理偏好 */
    private String agentReasoningPreference;
    /** 是否为内置Agent */
    private Boolean agentBuiltIn;
    /** Agent头像表情 */
    private String agentAvatarEmoji;
    /** Agent主题颜色 */
    private String agentAccentColor;
    /** 对局内显示名称。 */
    private String displayName;
    /** 当前玩家状态。 */
    private PlayerStatus status;
    /** 游戏角色编码。 */
    private String roleCode;
    /** 狼人杀女巫解药是否可用。 */
    private Boolean antidoteAvailable;
    /** 狼人杀女巫毒药是否可用。 */
    private Boolean poisonAvailable;
    /** 狼人杀预言家查验记录（上帝视角）。 */
    private Map<Long, Boolean> seerChecks;
    /** 大逃杀所在区域；其他模式为空。 */
    private String region;
    /** 大逃杀当前生命值；其他模式为空。 */
    private Integer health;
    /** 大逃杀当前食物；其他模式为空。 */
    private Integer food;
    /** 大逃杀当前饮水；其他模式为空。 */
    private Integer water;
    /** 大逃杀是否持有武器；其他模式为空。 */
    private Boolean armed;
    /** 大逃杀武器名称。 */
    private String weaponName;
    /** 大逃杀武器等级。 */
    private Integer weaponLevel;
    /** 大逃杀剩余弹药。 */
    private Integer ammo;
    /** 大逃杀当前护甲。 */
    private Integer armor;
    /** 大逃杀持有的医疗包数量。 */
    private Integer medkits;
    /** 大逃杀淘汰数。 */
    private Integer kills;
    /** 大逃杀累计造成伤害。 */
    private Integer totalDamage;
    /** 大逃杀是否处于隐蔽状态；其他模式为空。 */
    private Boolean hidden;
    /** 大逃杀是否因长期避战而被公开标记。 */
    private Boolean exposed;
}
