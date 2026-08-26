package com.zn.agentarena.modules.werewolf.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 确定性争议识别产生的一组质询者与回应者。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WerewolfQuestionPair {
    /** questionerPlayerId 字段 */
    private Long questionerPlayerId;
    /** respondentPlayerId 字段 */
    private Long respondentPlayerId;
}
