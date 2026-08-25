package com.zn.agentarena.modules.battle.information;

public record BattleRumorDecision(String rumorId, Long subjectPlayerId, String claimType, String claim, Integer confidence) {}
