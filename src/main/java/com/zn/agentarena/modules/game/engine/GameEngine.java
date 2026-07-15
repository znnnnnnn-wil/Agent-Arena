package com.zn.agentarena.modules.game.engine;

/**
 * 确定性游戏引擎接口。
 *
 * @param <S> 游戏状态类型
 * @param <A> 游戏行动类型
 * @author zhangxin
 * @date 2026/07/12
 */
public interface GameEngine<S extends GameState, A extends GameAction> {

    /** 校验行动是否可以执行。 */
    ActionValidation validate(S state, A action);

    /** 执行已经通过校验的行动。 */
    EngineResult<S> execute(S state, A action);

    /** 判断游戏是否结束。 */
    boolean isFinished(S state);
}
