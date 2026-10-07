package com.zn.agentarena.modules.battle.decision;

import com.zn.agentarena.modules.battle.agent.BattleAgentContext;
import com.zn.agentarena.modules.battle.alliance.BattleAllianceStatus;
import com.zn.agentarena.modules.battle.communication.BattleCommunicationDecision;
import com.zn.agentarena.modules.battle.domain.*;
import com.zn.agentarena.modules.battle.information.*;
import com.zn.agentarena.modules.battle.objective.*;
import com.zn.agentarena.modules.battle.perception.ObservedBattlePlayer;
import com.zn.agentarena.modules.battle.promise.*;
import com.zn.agentarena.modules.battle.social.BattleSocialDecision;
import com.zn.agentarena.modules.battle.trade.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 根据有限观察生成具体社会选择；模型只选择 ID，不再手写嵌套社会载荷。 */
@Component
public class BattleSocialDecisionCandidateGenerator {
    private static final int MAX_CANDIDATES = 32;

    public BattleSocialDecisionCandidateSet generate(BattleRoyaleGameState state, BattlePlayerState actor,
                                                      BattleAgentContext context) {
        List<Draft> pending = new ArrayList<>();
        List<Draft> objectives = new ArrayList<>();
        List<Draft> encounters = new ArrayList<>();
        List<Draft> rumors = new ArrayList<>();
        Long actorId = actor.getPlayerId();
        if (!actor.isDowned()) {
            addPendingResponses(pending, state, actor, context);
            addObjectiveContributions(objectives, actor, context);
            addVisiblePlayerOptions(encounters, actor, context);
            addRumorOption(rumors, context);
        }
        if (!encounters.isEmpty()) {
            Collections.rotate(encounters, Math.floorMod(state.getRoundNo() + actorId.intValue(), encounters.size()));
        }
        pending.sort(Comparator.comparingInt(d -> switch (d.decision().type()) {
            case "FULFILL_PROMISE" -> 0;
            case "ACCEPT_PROMISE", "ACCEPT_ALLIANCE", "ACCEPT_TRADE", "ACCEPT_INFORMATION_TRADE" -> 1;
            default -> 2;
        }));
        List<Draft> drafts = new ArrayList<>();
        appendUpTo(drafts, pending, 16);
        appendUpTo(drafts, objectives, 4);
        appendUpTo(drafts, encounters, 10);
        appendUpTo(drafts, rumors, 1);
        appendUpTo(drafts, pending, MAX_CANDIDATES - 1);
        appendUpTo(drafts, objectives, MAX_CANDIDATES - 1);
        appendUpTo(drafts, encounters, MAX_CANDIDATES - 1);
        Draft noAction = new Draft("NO_SOCIAL_ACTION", "本回合不执行正式社会动作", "无", null, null);
        List<BattleSocialDecisionCandidate> candidates = new ArrayList<>();
        int actionableCount = Math.min(MAX_CANDIDATES - 1, drafts.size());
        for (int i = 0; i < actionableCount; i++) {
            Draft draft = drafts.get(i);
            candidates.add(new BattleSocialDecisionCandidate("S%02d".formatted(i + 1), draft.name(),
                    draft.effect(), draft.cost(), draft.decision(), draft.communication()));
        }
        candidates.add(new BattleSocialDecisionCandidate("S%02d".formatted(candidates.size() + 1),
                noAction.name(), noAction.effect(), noAction.cost(), null, null));
        return new BattleSocialDecisionCandidateSet(state.getGameId(), state.getRoundNo(), state.getSequenceNo(),
                actorId, List.copyOf(candidates));
    }

    private void appendUpTo(List<Draft> selected, List<Draft> source, int limit) {
        while (!source.isEmpty() && selected.size() < MAX_CANDIDATES - 1 && limit-- > 0) {
            selected.add(source.remove(0));
        }
    }

    private void addPendingResponses(List<Draft> drafts, BattleRoyaleGameState state,
                                     BattlePlayerState actor, BattleAgentContext context) {
        Long actorId = actor.getPlayerId();
        context.social().alliances().stream()
                .filter(a -> a.getStatus() == BattleAllianceStatus.PROPOSED
                        && Objects.equals(a.getMemberBPlayerId(), actorId))
                .forEach(a -> {
                    add(drafts, "ACCEPT_ALLIANCE_" + a.getAllianceId(), "接受 P" + a.getProposerPlayerId()
                            + " 的联盟提案", null, social("ACCEPT_ALLIANCE", a.getProposerPlayerId(), a.getAllianceId(), null, null, null, null, null));
                    add(drafts, "REJECT_ALLIANCE_" + a.getAllianceId(), "拒绝该联盟提案", null,
                            social("REJECT_ALLIANCE", a.getProposerPlayerId(), a.getAllianceId(), null, null, null, null, null));
                });
        context.social().alliances().stream()
                .filter(a -> a.getStatus() == BattleAllianceStatus.ACTIVE && a.includes(actorId))
                .forEach(a -> add(drafts, "LEAVE_ALLIANCE_" + a.getAllianceId(), "退出与 P"
                                + a.otherMember(actorId) + " 的 ACTIVE 联盟", null,
                        social("LEAVE_ALLIANCE", a.otherMember(actorId), a.getAllianceId(), null, null, null, null, null)));

        context.social().promises().stream()
                .filter(p -> p.getStatus() == BattlePromiseStatus.PROPOSED
                        && Objects.equals(p.getPromiseePlayerId(), actorId))
                .forEach(p -> {
                    add(drafts, "ACCEPT_PROMISE_" + p.getPromiseId(), "接受该资源承诺", null,
                            social("ACCEPT_PROMISE", p.getPromiserPlayerId(), p.getPromiseId(), null, null, null, null, null));
                    add(drafts, "REJECT_PROMISE_" + p.getPromiseId(), "拒绝该资源承诺", null,
                            social("REJECT_PROMISE", p.getPromiserPlayerId(), p.getPromiseId(), null, null, null, null, null));
                });
        context.social().promises().stream()
                .filter(p -> p.getStatus() == BattlePromiseStatus.ACCEPTED
                        && Objects.equals(p.getPromiserPlayerId(), actorId))
                .filter(p -> {
                    BattlePlayerState promisee = state.findPlayer(p.getPromiseePlayerId());
                    return promisee != null && promisee.isActive()
                            && Objects.equals(actor.getRegion(), promisee.getRegion())
                            && BattleResourceInventory.amount(actor, p.getResourceType())
                            >= p.getAmount() + (p.getResourceType() == BattleResourceType.FOOD
                            || p.getResourceType() == BattleResourceType.WATER ? BattleRoyaleRules.METABOLISM_COST : 0);
                })
                .forEach(p -> add(drafts, "FULFILL_PROMISE_" + p.getPromiseId(),
                        "向同区 P" + p.getPromiseePlayerId() + " 履行 " + p.getResourceType() + " x" + p.getAmount()
                                + "；已为本回合代谢预留资源",
                        p.getResourceType() + " x" + p.getAmount(),
                        social("FULFILL_PROMISE", p.getPromiseePlayerId(), p.getPromiseId(), null, null, null, null, null)));

        context.social().tradeProposals().stream()
                .filter(t -> t.getStatus() == BattleTradeStatus.PROPOSED
                        && Objects.equals(t.getTargetPlayerId(), actorId))
                .forEach(t -> {
                    add(drafts, "ACCEPT_TRADE_" + t.getTradeId(), "接受交易：获得 " + t.getOffer()
                            + "，支付 " + t.getRequest(), String.valueOf(t.getRequest()),
                            social("ACCEPT_TRADE", t.getProposerPlayerId(), t.getTradeId(), null, null, null, null, null));
                    add(drafts, "REJECT_TRADE_" + t.getTradeId(), "拒绝该交易", null,
                            social("REJECT_TRADE", t.getProposerPlayerId(), t.getTradeId(), null, null, null, null, null));
                });
        context.social().informationTradeProposals().stream()
                .filter(t -> t.getStatus() == BattleInformationTradeStatus.PROPOSED
                        && Objects.equals(t.getBuyerPlayerId(), actorId))
                .forEach(t -> {
                    boolean alreadyKnown = context.social().rumors().stream()
                            .anyMatch(r -> Objects.equals(r.getRumorId(), t.getRumorId()));
                    if (!alreadyKnown) {
                        add(drafts, "ACCEPT_INFORMATION_TRADE_" + t.getTradeId(), "支付 " + t.getPrice()
                                + " 购买一条私密传闻", String.valueOf(t.getPrice()),
                                social("ACCEPT_INFORMATION_TRADE", t.getSellerPlayerId(), t.getTradeId(), null, null, null, null, null));
                    }
                    add(drafts, "REJECT_INFORMATION_TRADE_" + t.getTradeId(), "拒绝该情报交易", null,
                            social("REJECT_INFORMATION_TRADE", t.getSellerPlayerId(), t.getTradeId(), null, null, null, null, null));
                });
    }

    private void addObjectiveContributions(List<Draft> drafts, BattlePlayerState actor, BattleAgentContext context) {
        for (BattleCooperativeObjective objective : context.social().cooperativeObjectives()) {
            if (objective.getStatus() != BattleCooperativeObjectiveStatus.ACTIVE
                    || !Objects.equals(objective.getRegion(), actor.getRegion())) continue;
            int totalRequired = objective.getRequired().values().stream().mapToInt(Integer::intValue).sum();
            int actorContributed = objective.getContributionByPlayer().getOrDefault(actor.getPlayerId(), 0);
            int missingContributors = Math.max(0,
                    objective.getMinimumContributors() - objective.getContributionByPlayer().size());
            int reservedForOthers = objective.getContributionByPlayer().containsKey(actor.getPlayerId())
                    ? missingContributors : Math.max(0, missingContributors - 1);
            if (actorContributed >= totalRequired - reservedForOthers) continue;
            for (BattleResourceType type : List.of(BattleResourceType.FOOD, BattleResourceType.WATER)) {
                int remaining = objective.getRequired().getOrDefault(type, 0)
                        - objective.getContributed().getOrDefault(type, 0);
                int available = BattleResourceInventory.amount(actor, type);
                if (remaining <= 0 || available <= 2) continue;
                add(drafts, "CONTRIBUTE_" + type + "_" + objective.getObjectiveId(),
                        "向公共目标贡献 " + type + " x1；至少需" + objective.getMinimumContributors()
                                + "名贡献者；立即获得2护甲，完成后另获10护甲",
                        type + " x1", social("CONTRIBUTE_OBJECTIVE", null, null, null, null,
                                new BattleObjectiveContributionDecision(objective.getObjectiveId(), type.name(), 1), null, null));
                int bulk = Math.min(Math.min(remaining, available - 2),
                        totalRequired - reservedForOthers - actorContributed);
                if (bulk > 1) {
                    add(drafts, "CONTRIBUTE_" + type + "_X" + bulk + "_" + objective.getObjectiveId(),
                            "向公共目标贡献 " + type + " x" + bulk + "；至少需"
                                    + objective.getMinimumContributors() + "名贡献者；保留本回合代谢资源",
                            type + " x" + bulk,
                            social("CONTRIBUTE_OBJECTIVE", null, null, null, null,
                                    new BattleObjectiveContributionDecision(objective.getObjectiveId(), type.name(), bulk), null, null));
                }
            }
        }
    }

    private void addVisiblePlayerOptions(List<Draft> drafts, BattlePlayerState actor, BattleAgentContext context) {
        Long actorId = actor.getPlayerId();
        boolean hasOpenPromise = context.social().promises().stream()
                .anyMatch(p -> Objects.equals(p.getPromiserPlayerId(), actorId)
                        && (p.getStatus() == BattlePromiseStatus.PROPOSED
                        || p.getStatus() == BattlePromiseStatus.ACCEPTED));
        List<ObservedBattlePlayer> visible = context.observation().visiblePlayers().stream()
                .filter(p -> p.lifeStatus() == BattlePlayerLifeStatus.ALIVE).toList();
        for (ObservedBattlePlayer target : visible) {
            boolean alreadyLinked = context.social().alliances().stream()
                    .anyMatch(a -> a.includes(actorId) && a.includes(target.playerId())
                            && (a.getStatus() == BattleAllianceStatus.PROPOSED
                            || a.getStatus() == BattleAllianceStatus.ACTIVE));
            if (!alreadyLinked) {
                add(drafts, "PROPOSE_ALLIANCE_P" + target.playerId(),
                        "向 P" + target.playerId() + " 提议联盟；同区 ACTIVE 盟友可提供战斗掩护", null,
                        social("PROPOSE_ALLIANCE", target.playerId(), null, null, null, null, null, null));
            }
            String offered = actor.getFood() >= actor.getWater() ? "FOOD" : "WATER";
            String requested = "FOOD".equals(offered) ? "WATER" : "FOOD";
            if (BattleResourceInventory.amount(actor, BattleResourceType.valueOf(offered)) > 2) {
                if (!hasOpenPromise) {
                    BattlePromiseDecision promise = new BattlePromiseDecision("GIVE_RESOURCE", offered, 1,
                            Math.min(context.observation().publicWorld().maxRounds(),
                                    context.observation().publicWorld().roundNo() + 5));
                    add(drafts, "PROPOSE_PROMISE_P" + target.playerId(), "向 P" + target.playerId()
                                    + " 承诺在期限前给予 " + offered + " x1", offered + " x1",
                            social("PROPOSE_PROMISE", target.playerId(), null, promise, null, null, null, null));
                }
                BattleTradeDecision trade = new BattleTradeDecision(
                        List.of(new BattleTradeItemDecision(offered, 1)),
                        List.of(new BattleTradeItemDecision(requested, 1)));
                add(drafts, "PROPOSE_TRADE_P" + target.playerId(), "向 P" + target.playerId()
                        + " 提议用 " + offered + " x1 换 " + requested + " x1", offered + " x1",
                        social("PROPOSE_TRADE", target.playerId(), null, null, trade, null, null, null));
            }
            if (!context.social().rumors().isEmpty() && context.social().informationTradeProposals().stream()
                    .noneMatch(t -> Objects.equals(t.getSellerPlayerId(), actorId)
                            && t.getStatus() == BattleInformationTradeStatus.PROPOSED)) {
                var rumor = context.social().rumors().get(context.social().rumors().size() - 1);
                boolean alreadySold = context.social().informationTradeProposals().stream().anyMatch(t ->
                        Objects.equals(t.getSellerPlayerId(), actorId) && Objects.equals(t.getBuyerPlayerId(), target.playerId())
                                && Objects.equals(t.getRumorId(), rumor.getRumorId())
                                && t.getStatus() == BattleInformationTradeStatus.COMPLETED);
                if (!alreadySold && !rumor.getKnownByPlayerIds().contains(target.playerId())) add(drafts, "PROPOSE_INFORMATION_TRADE_P" + target.playerId(), "向 P" + target.playerId()
                                + " 出售已知传闻 " + rumor.getRumorId() + "，索取 FOOD x1", null,
                        social("PROPOSE_INFORMATION_TRADE", target.playerId(), null, null, null, null,
                                new BattleInformationTradeDecision(rumor.getRumorId(), "FOOD", 1), null));
            }
        }
    }

    private void addRumorOption(List<Draft> drafts, BattleAgentContext context) {
        if (context.social().rumors().isEmpty()) {
            BattleCommunicationDecision message = new BattleCommunicationDecision("LOCAL", null,
                    "未经证实：当前区域可能仍有补给");
            drafts.add(new Draft("CREATE_LOCAL_RESOURCE_RUMOR",
                    "向当前区域实际听众发布一条低置信度资源传闻", "占用本回合消息",
                    social("SHARE_RUMOR", null, null, null, null, null, null,
                            new BattleRumorDecision(null, null, "RESOURCE_CACHE",
                                    "当前区域可能仍有补给", 45)), message));
            return;
        }
        var rumor = context.social().rumors().get(context.social().rumors().size() - 1);
        BattleCommunicationDecision message = new BattleCommunicationDecision("LOCAL", null,
                "分享一条未经证实的传闻：" + rumor.getRumorId());
        drafts.add(new Draft("SHARE_RUMOR_" + rumor.getRumorId(),
                "向当前区域实际听众转述传闻 " + rumor.getRumorId(), "占用本回合消息",
                social("SHARE_RUMOR", null, null, null, null, null, null,
                        new BattleRumorDecision(rumor.getRumorId(), null, null, null, null)), message));
    }

    private BattleSocialDecision social(String type, Long target, String reference,
                                        BattlePromiseDecision promise, BattleTradeDecision trade,
                                        BattleObjectiveContributionDecision objective,
                                        BattleInformationTradeDecision information, BattleRumorDecision rumor) {
        return new BattleSocialDecision(type, target, reference, promise, trade, objective, information, rumor);
    }

    private void add(List<Draft> drafts, String name, String effect, String cost, BattleSocialDecision decision) {
        drafts.add(new Draft(name, effect, cost == null ? "不占用战术动作" : cost + "；不占用战术动作",
                decision, null));
    }

    private record Draft(String name, String effect, String cost, BattleSocialDecision decision,
                         BattleCommunicationDecision communication) { }
}
