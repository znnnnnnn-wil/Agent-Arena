package com.zn.agentarena.modules.battle.communication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 一条持久化在对局快照中的玩家消息；内容只是说法，不代表世界事实。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleMessage {
    private String id;
    private int round;
    private long sequenceNo;
    private Long senderPlayerId;
    private Long recipientPlayerId;
    private BattleCommunicationChannel channel;
    private String content;
    /** LOCAL 消息发出时的区域。 */
    private String region;
    /** 消息产生时真正能够听见的玩家，避免移动后读取旧 LOCAL 消息。 */
    @Builder.Default
    private List<Long> audiencePlayerIds = new ArrayList<>();
}
