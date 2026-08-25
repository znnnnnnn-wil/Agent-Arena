package com.zn.agentarena.modules.battle.simulation;

public record BattlePromptSample(int round, int characters, int estimatedTokens, int memories,
                                 int rumors, int messages, int relationships) {}
