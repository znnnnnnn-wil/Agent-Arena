package com.zn.agentarena.modules.game.dto.response;

import com.zn.agentarena.common.enums.PlayerStatus;
import com.zn.agentarena.common.enums.PlayerType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

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
    /** AI 玩家关联的模型配置 ID。 */
    private Long modelConfigId;
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
}
