package com.zn.agentarena.common.enums;

/**
 * 游戏玩家状态。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public enum PlayerStatus {
    /** 等待对局开始。 */
    WAITING,
    /** 当前存活。 */
    ALIVE,
    /** 已被淘汰。 */
    ELIMINATED,
    /** 已断开连接。 */
    DISCONNECTED
}
