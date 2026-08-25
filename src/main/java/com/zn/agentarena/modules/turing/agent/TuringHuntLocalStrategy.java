package com.zn.agentarena.modules.turing.agent;

import com.zn.agentarena.modules.turing.domain.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 六种玩家阶段均可用的确定性合法回退；策略从不读取真人标记。 */
@Component
public class TuringHuntLocalStrategy {

    private static final List<String> FIRST_ROUND_ANSWERS = List.of(
            "我会删除镜子。地图和时钟帮助定位，镜子的功能更容易由其他反馈替代。",
            "我会删除时钟。地图解决方向问题，镜子提供自我校准，而时间可以用事件顺序理解。",
            "我会删除地图。稳定的时间感和自我观察更基础，方向可以依靠共同约定补足。",
            "我选择保留地图和镜子，删除时钟，因为期限也能通过明确的阶段约定表达。"
    );

    public TuringHuntAction create(TuringHuntGameState state, TuringHuntPlayerState actor,
                                   TuringHuntActionType required) {
        return switch (required) {
            case ANSWER -> answer(state, actor);
            case SUSPECT -> suspicion(state, actor);
            case QUESTION -> question(state, actor);
            case RESPOND -> response(state, actor);
            case DEFEND -> defense(state, actor);
            case VOTE -> vote(state, actor);
            case ADVANCE -> TuringHuntAction.advance();
        };
    }

    private TuringHuntAction answer(TuringHuntGameState state, TuringHuntPlayerState actor) {
        String value;
        if (state.getRoundNo() == 1) {
            value = firstRoundAnswer(state, actor);
        } else if (state.getRoundNo() == 2) {
            TuringHuntPlayerState target = rankedOthers(state, actor).get(0);
            value = "我最想质疑" + target.getSeatNo() + "号：结论很明确，但公开理由还不足以排除另一种选择。";
        } else {
            List<TuringHuntPlayerState> targets = rankedOthers(state, actor);
            value = "我的判断从" + targets.get(0).getSeatNo() + "号转向" + targets.get(1).getSeatNo()
                    + "号，变化来自后者在质询后的解释与原立场不一致。";
        }
        return action(actor, TuringHuntActionType.ANSWER, value, null, List.of(), List.of(),
                "基于公开问题给出可检验选择", null, null, null, null, null);
    }

    private String firstRoundAnswer(TuringHuntGameState state, TuringHuntPlayerState actor) {
        String question = Objects.toString(state.getCurrentQuestion(), "");
        List<String> answers;
        if (question.contains("不会迷路") || question.contains("不会忘记")) {
            answers = List.of(
                    "我选择永远不会忘记。方向错误通常还能借助他人和工具修正，重要信息一旦遗失却可能无法重建。",
                    "我选择永远不会迷路。可靠地抵达目标能减少大量试错，记忆则可以通过记录和复述进行外部补偿。",
                    "我选择不会忘记，但会主动给旧信息设置失效条件，否则完整记忆也可能让判断被过时经验绑住。");
        } else if (question.contains("规则必须") || question.contains("结果不一定公平")) {
            answers = List.of(
                    "我可以接受，但规则需要允许申诉和定期复核。程序公平不能自动证明结果合理。",
                    "我暂不接受。若结果长期系统性偏向同一群体，就说明所谓一致规则遗漏了重要差异。",
                    "有条件接受：规则公开、适用一致，并且能用结果反馈修正，而不是把形式公平当成终点。");
        } else if (question.contains("看似没用") || question.contains("特殊情况下")) {
            answers = List.of(
                    "我会选一枚普通回形针。平时价值很低，但能临时固定、标记、复位小孔，也能作为简易导电连接。",
                    "我会选一段细绳。它不解决主要任务，却能在捆扎、测距、定位和留下路径标记时发挥作用。",
                    "我会选空白纸片。它看似没有信息，却能用于隔离、记录、校准尺度或在紧急时留下可见信号。");
        } else if (question.contains("否决权")) {
            answers = List.of(
                    "我会限制否决权只能暂停决定，不能永久阻止；行使者必须给出可验证理由，并在限定时间内接受复议。",
                    "否决权应限定在安全和不可逆风险上，每次使用都公开理由，连续使用还需要另一人共同确认。",
                    "我会设置次数上限和冷却期，并允许团队以更高票数推翻，让它成为风险刹车而不是个人统治工具。");
        } else if (question.contains("效率") && question.contains("可解释性")) {
            answers = List.of(
                    "我优先保留可解释性，因为无法复核的高效率会放大错误；若决策可逆且风险低，才允许效率优先。",
                    "低风险、可撤销任务优先效率，高风险或影响他人的任务优先可解释性，判断标准是错误成本和可逆性。",
                    "我会先保留最低限度的解释链，再优化效率。完全无法说明依据的结果不适合用于重要决定。");
        } else if (question.contains("信息不足") || question.contains("一个原则")) {
            answers = List.of(
                    "我选择可逆性原则：信息不足时先做容易撤回的小步行动，用反馈缩小不确定性。",
                    "我选择最小后悔原则，比较最坏结果后避开不可承受的损失，而不是假装能算出精确概率。",
                    "我选择先区分可逆与不可逆决定；前者快速试验，后者延迟并主动寻找能改变结论的信息。");
        } else {
            answers = FIRST_ROUND_ANSWERS;
        }
        return answers.get(stableIndex(state, actor, answers.size()));
    }

    private TuringHuntAction suspicion(TuringHuntGameState state, TuringHuntPlayerState actor) {
        List<TuringHuntPlayerState> targets = rankedOthers(state, actor);
        List<Long> suspects = targets.stream().limit(2).map(TuringHuntPlayerState::getPlayerId).toList();
        return action(actor, TuringHuntActionType.SUSPECT, null, targets.get(0).getPlayerId(), suspects,
                List.of("POSITION_CONSISTENCY", "REQUEST_EXPLANATION"),
                targets.get(0).getSeatNo() + "号公开回答里的取舍依据仍然需要更具体的解释", null, null, null, null, null);
    }

    private TuringHuntAction question(TuringHuntGameState state, TuringHuntPlayerState actor) {
        TuringHuntPlayerState target = state.findPlayer(state.getCurrentQuestionTargetPlayerId());
        return action(actor, TuringHuntActionType.QUESTION, null, null, List.of(), List.of(), "",
                target.getPlayerId(), "请" + target.getSeatNo() + "号说明哪条公开信息最能支持你现在的判断？",
                null, null, null);
    }

    private TuringHuntAction response(TuringHuntGameState state, TuringHuntPlayerState actor) {
        TuringHuntPlayerState questioner = state.findPlayer(state.getCurrentQuestionerPlayerId());
        return action(actor, TuringHuntActionType.RESPOND, null, null, List.of(), List.of(),
                "直接回应质询中的公开依据", questioner.getPlayerId(), null,
                "我的判断来自已公开回答之间的取舍差异；若后续解释补足，我会明确更新立场。", null, null);
    }

    private TuringHuntAction defense(TuringHuntGameState state, TuringHuntPlayerState actor) {
        TuringHuntPlayerState counter = rankedOthers(state, actor).get(0);
        return action(actor, TuringHuntActionType.DEFEND, null, null, List.of(), List.of(),
                "说明自身立场并指出可复核对象", null, null, null,
                "我的表达选择来自公开人格和当前证据，不代表身份；相比之下，"
                        + counter.getSeatNo() + "号的立场变化更值得复核。", counter.getPlayerId());
    }

    private TuringHuntAction vote(TuringHuntGameState state, TuringHuntPlayerState actor) {
        Long targetId = null;
        if (state.isRevote()) {
            targetId = state.getRevoteCandidates().stream()
                    .filter(id -> !Objects.equals(id, actor.getPlayerId()))
                    .sorted(Comparator.comparing(id -> state.findPlayer(id).getSeatNo())).findFirst().orElse(null);
        }
        if (targetId == null) {
            TuringHuntSuspicion own = state.getSuspicionsByPlayerId().get(actor.getPlayerId());
            if (own != null && legalTarget(state, actor, own.primarySuspectPlayerId())) {
                targetId = own.primarySuspectPlayerId();
            }
        }
        if (targetId == null) {
            targetId = state.getControversyPlayerIds().stream().filter(id -> legalTarget(state, actor, id))
                    .findFirst().orElse(null);
        }
        if (targetId == null) targetId = rankedOthers(state, actor).get(0).getPlayerId();
        return action(actor, TuringHuntActionType.VOTE, null, null, List.of(), List.of(),
                state.findPlayer(targetId).getSeatNo() + "号的公开解释前后存在差异", targetId,
                null, null, null, null);
    }

    private List<TuringHuntPlayerState> rankedOthers(TuringHuntGameState state, TuringHuntPlayerState actor) {
        List<TuringHuntPlayerState> result = new ArrayList<>(state.alivePlayers().stream()
                .filter(player -> !player.getPlayerId().equals(actor.getPlayerId())).toList());
        result.sort(Comparator.comparingInt((TuringHuntPlayerState player) -> stableRank(state, actor, player))
                .thenComparing(TuringHuntPlayerState::getSeatNo));
        return result;
    }

    private int stableIndex(TuringHuntGameState state, TuringHuntPlayerState actor, int size) {
        return Math.floorMod(Objects.hash(state.getRandomSeed(), state.getRoundNo(), actor.getSeatNo()), size);
    }

    private int stableRank(TuringHuntGameState state, TuringHuntPlayerState actor,
                           TuringHuntPlayerState candidate) {
        return Math.floorMod(Objects.hash(state.getRandomSeed(), state.getRoundNo(),
                actor.getSeatNo(), candidate.getSeatNo()), 10_000);
    }

    private boolean legalTarget(TuringHuntGameState state, TuringHuntPlayerState actor, Long id) {
        TuringHuntPlayerState player = state.findPlayer(id);
        return player != null && player.isAlive() && !player.getPlayerId().equals(actor.getPlayerId());
    }

    private TuringHuntAction action(TuringHuntPlayerState actor, TuringHuntActionType type,
                                    String answer, Long primary, List<Long> suspects, List<String> tags,
                                    String reason, Long target, String question, String response,
                                    String defense, Long counter) {
        return new TuringHuntAction(actor.getPlayerId(), type, answer, primary, suspects, tags,
                reason, target, question, response, defense, counter);
    }
}
