package com.zn.agentarena.modules.battle.engine;

import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.game.engine.EngineResult;
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
    @DisplayName("move: 只能沿地图连线移动")
    void testMoveRequiresAdjacentRegion() {
        BattleRoyaleGameEngine engine = new BattleRoyaleGameEngine();

        assertTrue(engine.validate(state(), new BattleRoyaleAction(1L, BattleActionType.MOVE, null, "B")).valid());
        assertFalse(engine.validate(state(), new BattleRoyaleAction(1L, BattleActionType.MOVE, null, "E")).valid());
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
        String firstUnsafe = BattleRoyaleRules.ringOrder(state.getRegions(), state.getRandomSeed()).get(4);
        state.findPlayer(3L).setRegion(firstUnsafe);
        BattleRoyaleGameEngine engine = new BattleRoyaleGameEngine();

        var result = engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.SKIP, null, null));

        assertEquals(4, state.getRoundNo());
        assertEquals(2, state.getRingStage());
        assertFalse(state.getSafeRegions().contains(firstUnsafe));
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

    @Test
    @DisplayName("search: 区域反复搜索后会枯竭")
    void testSearchDepletesRegion() {
        BattleRoyaleGameState state = state();
        BattleRoyaleGameEngine engine = new BattleRoyaleGameEngine();
        EngineResult<BattleRoyaleGameState> result = null;

        for (int i = 0; i < 7; i++) {
            result = engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.SEARCH, null, null));
        }

        assertNotNull(result);
        assertTrue(result.events().stream().anyMatch(event ->
                "EMPTY".equals(event.payload().get("lootType"))));
    }

    @Test
    @DisplayName("supply: 占领者守住完整行动窗口后获得空投")
    void testSupplyDropMustBeHeld() {
        BattleRoyaleGameState state = state();
        state.setSupplyDropRegion("A");
        BattleRoyaleGameEngine engine = new BattleRoyaleGameEngine();

        engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.CAPTURE, null, null));
        assertEquals(1L, state.getSupplyDropClaimPlayerId());
        assertNotNull(state.getSupplyDropRegion());

        engine.execute(state, new BattleRoyaleAction(2L, BattleActionType.SKIP, null, null));
        engine.execute(state, new BattleRoyaleAction(3L, BattleActionType.SKIP, null, null));
        var result = engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.SKIP, null, null));

        assertNull(state.getSupplyDropRegion());
        assertTrue(state.findPlayer(1L).isArmed());
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_SUPPLY_CAPTURED".equals(event.type())));
    }

    @Test
    @DisplayName("pressure: 连续两轮无交战会暴露一名强者")
    void testNoCombatExposesPlayer() {
        BattleRoyaleGameState state = state();
        BattleRoyaleGameEngine engine = new BattleRoyaleGameEngine();
        EngineResult<BattleRoyaleGameState> result = null;

        for (int i = 0; i < 6; i++) {
            long playerId = i % 3 + 1L;
            result = engine.execute(state, new BattleRoyaleAction(playerId, BattleActionType.SKIP, null, null));
        }

        assertNotNull(result);
        assertTrue(state.alivePlayers().stream().anyMatch(BattlePlayerState::isExposed));
        assertTrue(result.events().stream().anyMatch(event -> "BATTLE_PLAYER_EXPOSED".equals(event.type())));
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
