package com.zn.agentarena.modules.battle.perception;

import com.zn.agentarena.modules.battle.domain.BattlePlayerState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("大逃杀有限感知测试")
class BattleObservationSystemTest {
    private final BattleObservationSystem system = new BattleObservationSystem();

    @Test
    @DisplayName("不同区域玩家不应进入当前观察")
    void remotePlayerIsNotVisible() {
        BattlePlayerState actor = player(1L, "A");
        BattlePlayerState remote = player(2L, "E");
        remote.setHealth(17);
        remote.setArmed(true);
        remote.setWeaponName("精确步枪");
        remote.setWeaponLevel(3);
        remote.setAmmo(99);

        BattleAgentObservation observation = system.observe(state(actor, remote), actor.getPlayerId());

        assertTrue(observation.visiblePlayers().isEmpty());
        assertEquals(2, observation.publicWorld().remainingPlayers());
    }

    @Test
    @DisplayName("同区域 hidden 玩家默认不应进入观察")
    void hiddenPlayerIsNotVisibleByServerOmniscience() {
        BattlePlayerState actor = player(1L, "A");
        BattlePlayerState hidden = player(2L, "A");
        hidden.setHidden(true);

        BattleAgentObservation observation = system.observe(state(actor, hidden), actor.getPlayerId());

        assertTrue(observation.visiblePlayers().isEmpty());
    }

    private BattleRoyaleGameState state(BattlePlayerState... players) {
        return BattleRoyaleGameState.builder().gameId(1L).roundNo(2).maxRounds(20)
                .safeRegions(List.of("A", "B", "C", "D", "E"))
                .players(new ArrayList<>(List.of(players))).build();
    }

    private BattlePlayerState player(Long id, String region) {
        return BattlePlayerState.builder().playerId(id).displayName(id + "号")
                .region(region).health(100).food(5).water(5).alive(true).build();
    }
}
