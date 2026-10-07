package com.zn.agentarena.modules.spectator.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.zn.agentarena.dao.entity.GameEvent;
import com.zn.agentarena.dao.mapper.GameEventMapper;
import com.zn.agentarena.modules.game.engine.EngineEvent;
import com.zn.agentarena.modules.spectator.dto.response.GameEventView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 游戏事件持久化服务。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Service
@RequiredArgsConstructor
public class GameEventRecorder {

    private final GameEventMapper gameEventMapper;
    private final GameEventStreamService streamService;

    /**
     * 保存并广播一个游戏事件。
     *
     * @param gameId 游戏ID
     * @param roundNo 轮次
     * @param phase 阶段
     * @param sequenceNo 序号
     * @param event 引擎事件
     */
    @Transactional(rollbackFor = Exception.class)
    /**
     * 将引擎事件转换为可持久化、可公开筛选的游戏事件。
     *
     * @param gameId 游戏标识
     * @param roundNo 当前回合号
     * @param phase 当前阶段
     * @param sequenceNo 游戏内事件序号
     * @param event 引擎产生的事件
     */
    public void record(Long gameId, int roundNo, String phase, long sequenceNo, EngineEvent event) {
        GameEvent entity = GameEvent.builder()
                .gameId(gameId)
                .sequenceNo(sequenceNo)
                .eventType(event.type())
                .actorPlayerId(event.actorPlayerId())
                .roundNo(roundNo)
                .phase(phase)
                .payload(event.payload())
                .publicPayload(event.publicPayload())
                .build();
        gameEventMapper.insertSelective(entity);
        GameEventView view = GameEventView.from(gameId, sequenceNo, event);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
    /**
     * 在事务提交后向实时订阅者发布暂存的事件。
     */
    public void afterCommit() {
                    streamService.publish(view);
                }
            });
        } else {
            streamService.publish(view);
        }
    }

    /**
     * 获取指定游戏的公开事件列表。
     *
     * @param gameId 游戏ID
     * @return 事件列表
     */
    public List<GameEvent> listEvents(Long gameId, long afterSequence, int requestedLimit) {
        int limit = Math.max(1, Math.min(500, requestedLimit));
        QueryWrapper query = QueryWrapper.create().eq(GameEvent::getGameId, gameId);
        if (afterSequence > 0) query.gt(GameEvent::getSequenceNo, afterSequence);
        return gameEventMapper.selectListByQuery(query.orderBy(GameEvent::getSequenceNo, true).limit(limit));
    }

    /** Read the complete event log in bounded database pages for summaries and projections. */
    public List<GameEvent> listAllEvents(Long gameId) {
        List<GameEvent> all = new java.util.ArrayList<>();
        long afterSequence = 0;
        while (true) {
            List<GameEvent> page = listEvents(gameId, afterSequence, 500);
            if (page == null || page.isEmpty()) return all;
            all.addAll(page);
            if (page.size() < 500) return all;
            afterSequence = page.get(page.size() - 1).getSequenceNo();
        }
    }

    public long countEvents(Long gameId) {
        return gameEventMapper.selectCountByQuery(QueryWrapper.create().eq(GameEvent::getGameId, gameId));
    }

    public long countEliminations(Long gameId) {
        return gameEventMapper.selectCountByQuery(QueryWrapper.create().eq(GameEvent::getGameId, gameId)
                .in(GameEvent::getEventType, List.of("PLAYER_ELIMINATED", "BATTLE_PLAYER_ELIMINATED",
                        "TURING_PLAYER_ELIMINATED")));
    }

    /** Bounded internal history for views that explicitly show recent events. */
    public List<GameEvent> listRecentEvents(Long gameId, int requestedLimit) {
        int limit = Math.max(1, Math.min(5_000, requestedLimit));
        List<GameEvent> latest = gameEventMapper.selectListByQuery(QueryWrapper.create()
                .eq(GameEvent::getGameId, gameId)
                .orderBy(GameEvent::getSequenceNo, false).limit(limit));
        java.util.Collections.reverse(latest);
        return latest;
    }
}
