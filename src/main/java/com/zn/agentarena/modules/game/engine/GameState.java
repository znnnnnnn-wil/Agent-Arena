package com.zn.agentarena.modules.game.engine;

/**
 * 游戏状态标记接口。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public interface GameState {

    /** 获取游戏ID。 */
    Long getGameId();

    /** 获取当前事件序号。 */
    long getSequenceNo();

    /** 设置当前事件序号。 */
    void setSequenceNo(long sequenceNo);
}
