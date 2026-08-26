package com.zn.agentarena.modules.battle.agent;

import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.model.gateway.ModelGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("大逃杀 AI 信息边界测试")
class BattleAgentInformationBoundaryTest {

    @Test
    @DisplayName("Prompt 不包含完全不可见玩家的精确状态")
    void promptDoesNotLeakInvisiblePlayerState() {
        BattlePlayerState actor = player(1L, "A");
        BattlePlayerState invisible = player(2L, "E");
        invisible.setDisplayName("Invisible-Secret");
        invisible.setHealth(17);
        invisible.setArmed(true);
        invisible.setWeaponName("精确步枪");
        invisible.setWeaponLevel(3);
        invisible.setAmmo(99);
        invisible.setArmor(57);
        BattleRoyaleGameState state = state(actor, invisible);
        BattleAiGameRunner runner = new BattleAiGameRunner(mock(ModelGateway.class), new BattleAgentContextBuilder());

        String prompt = new BattleAgentContextBuilder().build(runner.createContext(state, actor));

        assertFalse(prompt.contains("Invisible-Secret"));
        assertFalse(prompt.contains("health=17"));
        assertFalse(prompt.contains("weaponLevel=3"));
        assertFalse(prompt.contains("ammo=99"));
        assertFalse(prompt.contains("armor=57"));
        assertFalse(prompt.contains("currentObservedRegion=E"));
    }

    @Test
    @DisplayName("本地回退看不见 hidden 敌人时不得自动攻击")
    void fallbackCannotAttackHiddenEnemy() {
        BattlePlayerState actor = player(1L, "A");
        actor.setArmed(true);
        actor.setWeaponName("简易手枪");
        actor.setWeaponLevel(1);
        actor.setAmmo(5);
        BattlePlayerState hidden = player(2L, "A");
        hidden.setHealth(5);
        hidden.setHidden(true);
        BattleRoyaleGameState state = state(actor, hidden);

        var result = new BattleAiGameRunner(mock(ModelGateway.class), new BattleAgentContextBuilder())
                .runNextTurn(state, actor, null);

        assertNotEquals("ATTACK", result.events().get(0).payload().get("action"));
        assertNull(result.events().get(0).payload().get("targetPlayerId"));
        assertEquals(5, hidden.getHealth());
    }

    private BattleRoyaleGameState state(BattlePlayerState... players) {
        return BattleRoyaleGameState.builder().gameId(1L).roundNo(4).maxRounds(20).randomSeed(42L)
                .safeRegions(List.of("A", "B", "C", "D", "E"))
                .players(new ArrayList<>(List.of(players))).build();
    }

    private BattlePlayerState player(Long id, String region) {
        return BattlePlayerState.builder().playerId(id).displayName(id + "号")
                .region(region).health(100).food(5).water(5).alive(true).build();
    }
}
