package com.zn.agentarena.common.enums;

/**
 * 游戏状态。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
public enum GameStatus {
    /** 等待开始。 */
    WAITING,
    /** 正在进行。 */
    RUNNING,
    /** 已暂停。 */
    PAUSED,
    /** 已正常结束。 */
    FINISHED,
    /** 已被终止。 */
    TERMINATED,
    /** 执行失败。 */
    FAILED
}
