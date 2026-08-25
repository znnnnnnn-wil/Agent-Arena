package com.zn.agentarena.modules.game.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.zn.agentarena.dao.entity.GameSnapshot;
import com.zn.agentarena.dao.mapper.GameSnapshotMapper;
import com.zn.agentarena.modules.werewolf.domain.WerewolfGameState;
import com.zn.agentarena.modules.battle.domain.BattleRoyaleGameState;
import com.zn.agentarena.modules.battle.social.BattleSocialStateInitializer;
import com.zn.agentarena.modules.turing.domain.TuringHuntGameState;
import com.zn.agentarena.config.AgentArenaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 游戏状态快照服务。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Service
@RequiredArgsConstructor
public class GameSnapshotService {

    private final GameSnapshotMapper snapshotMapper;
    private final ObjectMapper objectMapper;
    private final AgentArenaProperties properties;
    private final BattleSocialStateInitializer battleSocialStateInitializer;

    /** 保存狼人杀状态快照。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveWerewolfSnapshot(WerewolfGameState state) {
        Map<String, Object> gameState = objectMapper.convertValue(state,
                new TypeReference<Map<String, Object>>() {
                });
        snapshotMapper.insertSelective(GameSnapshot.builder()
                .gameId(state.getGameId())
                .sequenceNo(state.getSequenceNo())
                .gameState(gameState)
                .build());
        pruneOldSnapshots(state.getGameId());
    }

    /** 获取指定游戏的最新快照。 */
    public WerewolfGameState loadLatestWerewolfSnapshot(Long gameId) {
        GameSnapshot snapshot = snapshotMapper.selectOneByQuery(QueryWrapper.create()
                .eq(GameSnapshot::getGameId, gameId)
                .orderBy(GameSnapshot::getSequenceNo, false)
                .limit(1));
        if (snapshot == null) {
            return null;
        }
        return objectMapper.convertValue(snapshot.getGameState(), WerewolfGameState.class);
    }

    /** 保存大逃杀状态快照。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveBattleSnapshot(BattleRoyaleGameState state) {
        battleSocialStateInitializer.ensure(state);
        Map<String, Object> gameState = objectMapper.convertValue(state,
                new TypeReference<Map<String, Object>>() { });
        snapshotMapper.insertSelective(GameSnapshot.builder().gameId(state.getGameId())
                .sequenceNo(state.getSequenceNo()).gameState(gameState).build());
        pruneOldSnapshots(state.getGameId());
    }

    /** 获取指定游戏的最新大逃杀快照。 */
    public BattleRoyaleGameState loadLatestBattleSnapshot(Long gameId) {
        GameSnapshot snapshot = snapshotMapper.selectOneByQuery(QueryWrapper.create()
                .eq(GameSnapshot::getGameId, gameId).orderBy(GameSnapshot::getSequenceNo, false).limit(1));
        if (snapshot == null) return null;
        BattleRoyaleGameState state = objectMapper.convertValue(snapshot.getGameState(), BattleRoyaleGameState.class);
        return battleSocialStateInitializer.ensure(state);
    }

    /** 保存图灵潜伏局内部状态。 */
    @Transactional(rollbackFor = Exception.class)
    public void saveTuringSnapshot(TuringHuntGameState state) {
        Map<String, Object> gameState = objectMapper.convertValue(state,
                new TypeReference<Map<String, Object>>() { });
        snapshotMapper.insertSelective(GameSnapshot.builder().gameId(state.getGameId())
                .sequenceNo(state.getSequenceNo()).gameState(gameState).build());
        pruneOldSnapshots(state.getGameId());
    }

    /** 读取最新图灵潜伏局快照；不会影响旧模式快照的反序列化。 */
    public TuringHuntGameState loadLatestTuringSnapshot(Long gameId) {
        GameSnapshot snapshot = snapshotMapper.selectOneByQuery(QueryWrapper.create()
                .eq(GameSnapshot::getGameId, gameId).orderBy(GameSnapshot::getSequenceNo, false).limit(1));
        return snapshot == null ? null : objectMapper.convertValue(snapshot.getGameState(), TuringHuntGameState.class);
    }

    private void pruneOldSnapshots(Long gameId) {
        java.util.List<GameSnapshot> snapshots = snapshotMapper.selectListByQuery(QueryWrapper.create()
                .eq(GameSnapshot::getGameId, gameId)
                .orderBy(GameSnapshot::getSequenceNo, false));
        snapshots.stream().skip(properties.game().snapshotRetention())
                .forEach(snapshot -> snapshotMapper.deleteById(snapshot.getId()));
    }
}
