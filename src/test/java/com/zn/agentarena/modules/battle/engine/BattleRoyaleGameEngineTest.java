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
    void overkillOnlyCreditsActualHealthRemoved() {
        var state = state();
        state.findPlayer(2L).setHealth(5);
        var result = new BattleRoyaleGameEngine().execute(state,
                new BattleRoyaleAction(1L, BattleActionType.ATTACK, 2L, null));
        assertEquals(5, state.findPlayer(1L).getTotalDamage());
        assertEquals(10, BattleRoyaleRules.scoreBreakdown(state.findPlayer(1L)).get("damage"));
        var event = result.events().stream().filter(e -> "BATTLE_ACTION_EXECUTED".equals(e.type())).findFirst().orElseThrow();
        assertEquals(5, event.payload().get("creditedDamage"));
        assertEquals(10, event.payload().get("damageScoreGain"));
    }

    @Test
    void revivalCannotResetTargetDamageCreditBudget() {
        var state = state();
        var target = state.findPlayer(2L);
        target.setScoredDamageReceived(95);
        target.setHealth(5);
        var engine = new BattleRoyaleGameEngine();
        engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.ATTACK, 2L, null));
        assertEquals(100, target.getScoredDamageReceived());
        target.markAlive(30);
        engine.execute(state, new BattleRoyaleAction(3L, BattleActionType.ATTACK, 2L, null));
        assertEquals(0, state.findPlayer(3L).getTotalDamage());
        assertEquals(100, target.getScoredDamageReceived());
        assertTrue(target.getHealth() < 30);
    }

    @Test
    void repeatedHidingIsDetectedButChangingActionResetsIt() {
        var state = state();
        var engine = new BattleRoyaleGameEngine();
        var actor = state.findPlayer(1L);
        actor.setFood(20);
        actor.setWater(20);
        for (int i = 0; i < 2; i++) {
            engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.HIDE, null, null));
            assertTrue(actor.isHidden());
        }
        var detected = engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.HIDE, null, null));
        assertFalse(actor.isHidden());
        assertTrue(actor.isExposed());
        assertTrue(detected.events().stream().anyMatch(e -> "REPEATED_HIDING".equals(e.payload().get("reason"))
                && "A".equals(e.publicPayload().get("region"))));
        engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.GUARD, null, null));
        engine.execute(state, new BattleRoyaleAction(1L, BattleActionType.HIDE, null, null));
        assertTrue(actor.isHidden());
        assertFalse(actor.isExposed());
    }

    @Test
    void shortGameHasFinalZoneBeforeTimeLimitAndConsistentForecast() {
        for (int stage = 1; stage < 5; stage++) {
            int round = BattleRoyaleRules.nextShrinkRound(stage, 12);
            assertEquals(1 + stage * 2, round);
            assertEquals(stage, BattleRoyaleRules.ringStageForRound(round - 1, 12));
            assertEquals(stage + 1, BattleRoyaleRules.ringStageForRound(round, 12));
        }
        assertEquals(5, BattleRoyaleRules.ringStageForRound(9, 12));
        assertEquals(-1, BattleRoyaleRules.nextShrinkRound(5, 12));
        assertEquals(4, BattleRoyaleRules.nextShrinkRound(1, 20));
    }

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
        state.setMaxRounds(20);
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
        assertNotEquals(state.getSafeRegions().get(state.getSafeRegions().size() - 1), state.getSupplyDropRegion());
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

    @Test
    void guardReducesOnlyTheNextHitAndExpiresOnOwnAction() {
        var engine = new BattleRoyaleGameEngine();
        var baseline = state();
        var defended = state();
        defended.findPlayer(2L).setGuarded(true);
        engine.execute(baseline, new BattleRoyaleAction(1L, BattleActionType.ATTACK, 2L, null));
        engine.execute(defended, new BattleRoyaleAction(1L, BattleActionType.ATTACK, 2L, null));
        assertEquals(8, defended.findPlayer(2L).getHealth() - baseline.findPlayer(2L).getHealth());
        assertFalse(defended.findPlayer(2L).isGuarded());
        defended.findPlayer(2L).setGuarded(true);
        engine.execute(defended, new BattleRoyaleAction(2L, BattleActionType.REST, null, null));
        assertFalse(defended.findPlayer(2L).isGuarded());
    }

    @Test
    void guardCannotFarmPermanentArmor() {
        var state = state();
        new BattleRoyaleGameEngine().execute(state, new BattleRoyaleAction(1L, BattleActionType.GUARD, null, null));
        assertEquals(0, state.findPlayer(1L).getArmor());
        new BattleRoyaleGameEngine().execute(state, new BattleRoyaleAction(1L, BattleActionType.GUARD, null, null));
        assertEquals(0, state.findPlayer(1L).getArmor());
        assertTrue(state.findPlayer(1L).isGuarded());
    }

    @Test
    void regionCoverReducesIncomingDamage() {
        var engine = new BattleRoyaleGameEngine();
        for (String region : List.of("B", "D")) {
            var baseline = state();
            var covered = state();
            covered.getPlayers().forEach(player -> player.setRegion(region));
            engine.execute(baseline, new BattleRoyaleAction(1L, BattleActionType.ATTACK, 2L, null));
            engine.execute(covered, new BattleRoyaleAction(1L, BattleActionType.ATTACK, 2L, null));
            assertEquals(BattleRegionRules.profile(region).coverReduction(),
                    covered.findPlayer(2L).getHealth() - baseline.findPlayer(2L).getHealth());
        }
    }

    @Test
    void searchingForestStaysQuietButVillageSearchAndAttackExposeActor() {
        var engine = new BattleRoyaleGameEngine();
        var forest = state();
        forest.findPlayer(1L).setRegion("B");
        engine.execute(forest, new BattleRoyaleAction(1L, BattleActionType.SEARCH, null, null));
        assertFalse(forest.findPlayer(1L).isExposed());
        var village = state();
        engine.execute(village, new BattleRoyaleAction(1L, BattleActionType.SEARCH, null, null));
        assertTrue(village.findPlayer(1L).isExposed());
        engine.execute(village, new BattleRoyaleAction(1L, BattleActionType.HIDE, null, null));
        assertFalse(village.findPlayer(1L).isExposed());
        engine.execute(village, new BattleRoyaleAction(1L, BattleActionType.ATTACK, 2L, null));
        assertTrue(village.findPlayer(1L).isExposed());
    }

    @Test
    void campRestRestoresThreeMoreHealth() {
        var engine = new BattleRoyaleGameEngine();
        var village = state();
        var camp = state();
        village.findPlayer(1L).setHealth(50);
        camp.findPlayer(1L).setHealth(50);
        camp.findPlayer(1L).setRegion("E");
        engine.execute(village, new BattleRoyaleAction(1L, BattleActionType.REST, null, null));
        engine.execute(camp, new BattleRoyaleAction(1L, BattleActionType.REST, null, null));
        assertEquals(3, camp.findPlayer(1L).getHealth() - village.findPlayer(1L).getHealth());
    }

    @Test
    void publicWorldUsesActualSafeRegionsAndScoreBreakdownAddsUp() {
        var state = state();
        state.setRingStage(3);
        state.setSafeRegions(List.of("B", "D", "E"));
        state.setSupplyDropRegion("D");
        var world = com.zn.agentarena.modules.game.dto.response.BattleWorldResponse.from(state);
        assertEquals(List.of("B", "D", "E"), world.safeRegions());
        assertEquals("D", world.supplyDropRegion());
        assertEquals(6, world.regionProfiles().get("D").coverReduction());
        assertEquals(BattleRoyaleRules.survivalScore(state.findPlayer(1L)),
                BattleRoyaleRules.scoreBreakdown(state.findPlayer(1L)).values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void roundLimitResultExplainsWhyAndHowWinnerWasSelected() {
        var state = state();
        state.setRoundNo(state.getMaxRounds());
        state.findPlayer(2L).setKills(2);
        var events = new java.util.ArrayList<com.zn.agentarena.modules.game.engine.EngineEvent>();
        new BattleRoyaleGameEngine().finishIfNeeded(state, events);
        var finish = events.stream().filter(event -> "BATTLE_FINISHED".equals(event.type())).findFirst().orElseThrow();
        assertEquals("MAX_ROUNDS_SCORE", finish.payload().get("reason"));
        assertEquals(2L, finish.payload().get("winnerPlayerId"));
        assertEquals(BattleRoyaleRules.scoreBreakdown(state.findPlayer(2L)), finish.payload().get("scoreBreakdown"));
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
