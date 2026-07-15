package com.zn.agentarena.modules.game.engine;

import java.util.List;

/**
 * 游戏引擎执行结果。
 *
 * @param state 执行后的游戏状态
 * @param events 本次执行产生的事件
 * @param <S> 游戏状态类型
 * @author zhangxin
 * @date 2026/07/12
 */
public record EngineResult<S extends GameState>(S state, List<EngineEvent> events) {
}
