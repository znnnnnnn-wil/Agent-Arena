package com.zn.agentarena.modules.battle.engine;

import com.zn.agentarena.modules.battle.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 大逃杀游戏引擎测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@DisplayName("大逃杀游戏引擎测试")
class BattleRoyaleGameEngineTest {

    @Test
    @DisplayName("attack: 同一区域攻击应扣除生命值")
    void testAttack() {
        BattleRoyaleGameState state = state();
        BattleRoyaleGameEngine engine = new BattleRoyaleGameEngine();
        state.findPlayer(1L).setArmed(true);
        state.findPlayer(1L).setWeaponLevel(1);
        state.findPlayer(1L).setWeaponName("简易手枪");
        state.findPlayer(1L).setAmmo(3);

        engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.ATTACK, 2L, null));

        assertEquals(60, state.findPlayer(2L).getHealth());
    }

    @Test
    @DisplayName("move: 不存在区域应拒绝行动")
    void testMoveInvalidRegion() {
        BattleRoyaleGameEngine engine = new BattleRoyaleGameEngine();
        assertFalse(engine.validate(state(), new BattleRoyaleAction(1L, BattleActionType.MOVE, null, "Z")).valid());
    }

    @Test
    @DisplayName("search: 连续搜索三次内应获得武器保底")
    void testSearchWeaponPity() {
        BattleRoyaleGameState state = state();
        BattleRoyaleGameEngine engine = new BattleRoyaleGameEngine();

        for (int i = 0; i < 3 && !state.findPlayer(1L).isArmed(); i++) {
            engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.SEARCH, null, null));
        }

        assertTrue(state.findPlayer(1L).isArmed());
    }

    @Test
    @DisplayName("zone: 第四轮开始第一次缩圈并造成圈外伤害")
    void testFirstZoneShrink() {
        BattleRoyaleGameState state = state();
        state.setRoundNo(3);
        state.setActionsInRound(2);
        state.findPlayer(3L).setRegion("D");
        BattleRoyaleGameEngine engine = new BattleRoyaleGameEngine();

        var result = engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.SKIP, null, null));

        assertEquals(4, state.getRoundNo());
        assertEquals(2, state.getRingStage());
        assertFalse(state.getSafeRegions().contains("D"));
        assertEquals(92, state.findPlayer(3L).getHealth());
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_ZONE_UPDATED".equals(event.type())));
        assertNotNull(state.getSupplyDropRegion());
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_SUPPLY_DROP".equals(event.type())));
    }

    @Test
    @DisplayName("heal: 医疗包可恢复生命并被消耗")
    void testHeal() {
        BattleRoyaleGameState state = state();
        BattlePlayerState player = state.findPlayer(1L);
        player.setHealth(40);
        player.setMedkits(1);

        new BattleRoyaleGameEngine().execute(state, new BattleRoyaleAction(1L, BattleActionType.HEAL, null, null));

        assertEquals(75, player.getHealth());
        assertEquals(0, player.getMedkits());
    }

    @Test
    @DisplayName("hide: 公开事件不应泄露隐蔽玩家位置")
    void testHideRedactsPublicRegion() {
        var result = new BattleRoyaleGameEngine().execute(state(),
                new BattleRoyaleAction(1L, BattleActionType.HIDE, null, null));

        var event = result.events().stream()
                .filter(item -> "BATTLE_ACTION_EXECUTED".equals(item.type()))
                .findFirst().orElseThrow();
        assertEquals("A", event.payload().get("region"));
        assertFalse(event.publicPayload().containsKey("region"));
    }

    private BattleRoyaleGameState state() {
        return BattleRoyaleGameState.builder().gameId(1L).maxRounds(10).roundNo(1).randomSeed(42L)
                .regions(List.of("A", "B", "C", "D", "E"))
                .players(List.of(
                        player(1L), player(2L), player(3L))).safeRegion("A").build();
    }

    private BattlePlayerState player(Long id) {
        return BattlePlayerState.builder().playerId(id).displayName(id + "号")
                .region("A").health(100).food(5).water(5).alive(true).build();
    }
}
