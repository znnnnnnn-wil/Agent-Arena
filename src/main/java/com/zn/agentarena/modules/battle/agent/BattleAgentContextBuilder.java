package com.zn.agentarena.modules.battle.agent;

import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import org.springframework.stereotype.Component;

/** 构建大逃杀智能体可见的决策上下文。 */
@Component
public class BattleAgentContextBuilder {
    public String build(BattleRoyaleGameState state, BattlePlayerState actor) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你正在参加一局多智能体大逃杀。目标是成为最后的幸存者。\n")
                .append("你的玩家ID: ").append(actor.getPlayerId()).append("\n")
                .append("当前轮次: ").append(state.getRoundNo()).append("/").append(state.getMaxRounds()).append("\n")
                .append("当前安全区域: ").append(state.getSafeRegions()).append("，缩圈阶段: ").append(state.getRingStage()).append("/5\n")
                .append("每3轮缩圈一次；圈外会持续受到递增伤害，请提前移动。\n")
                .append("当前空投区域: ").append(state.getSupplyDropRegion() == null ? "暂无" : state.getSupplyDropRegion()).append("\n")
                .append("你的状态: region=").append(actor.getRegion()).append(", health=").append(actor.getHealth())
                .append(", food=").append(actor.getFood()).append(", water=").append(actor.getWater())
                .append(", weapon=").append(actor.getWeaponName()).append(" Lv.").append(actor.getWeaponLevel())
                .append(", ammo=").append(actor.getAmmo()).append(", armor=").append(actor.getArmor())
                .append(", medkits=").append(actor.getMedkits()).append(", hidden=").append(actor.isHidden()).append("\n")
                .append("最近行动: ").append(actor.getLastAction()).append("，连续重复次数: ").append(actor.getRepeatedActions()).append("\n")
                .append("存活玩家公开状态:\n");
        for (BattlePlayerState player : state.alivePlayers()) {
            prompt.append("- ID=").append(player.getPlayerId()).append(", name=").append(player.getDisplayName())
                    .append(", region=").append(player.getRegion()).append(", health=").append(player.getHealth())
                    .append(", weaponLevel=").append(player.getWeaponLevel()).append(", ammo=").append(player.getAmmo())
                    .append(", armor=").append(player.getArmor()).append(", kills=").append(player.getKills())
                    .append(", hidden=").append(player.isHidden()).append("\n");
        }
        prompt.append("可用区域: ").append(state.getRegions()).append("\n")
                .append("只返回合法 JSON，不要 Markdown 或解释。\n")
                .append("格式: {\"action\":\"SEARCH\",\"targetPlayerId\":null,\"targetRegion\":null,\"reasonSummary\":\"简短策略摘要\",\"confidence\":80}\n")
                .append("action 只能是 MOVE、SEARCH、REST、TRADE、ATTACK、HIDE、HEAL、GUARD、SKIP。\n")
                .append("SEARCH 可获得武器、食物、水、医疗包或护甲；连续搜索未获得武器时会触发保底。\n")
                .append("区域特色: A武器与战斗资源、B食物、C饮水与弹药、D护甲、E医疗；空投区有高级奖励。\n")
                .append("HEAL 需要医疗包并恢复35生命；GUARD 增加护甲；武器会提高攻击伤害。\n")
                .append("避免无意义地连续执行同一种行动；根据缩圈、敌人、弹药和资源主动调整策略。\n")
                .append("ATTACK 和 TRADE 的目标必须与自己在同一区域；MOVE 必须填写 targetRegion。\n");
        return prompt.toString();
    }
}
