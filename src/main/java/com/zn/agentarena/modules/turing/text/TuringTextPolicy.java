package com.zn.agentarena.modules.turing.text;

import com.zn.agentarena.modules.turing.domain.TuringHuntAction;
import com.zn.agentarena.modules.turing.domain.TuringHuntActionType;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 图灵潜伏局唯一的公开文本规范。真人输入、模型输出和引擎落库都必须经过这里，
 * 规则只依据公开内容，不使用模型、耗时、重试或提交顺序等身份旁路信息。
 */
public final class TuringTextPolicy {

    public static final TuringTextPolicy INSTANCE = new TuringTextPolicy();

    private static final Map<TuringHuntActionType, Rule> RULES = rules();
    private static final Pattern LEADING_HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s*");
    private static final Pattern LEADING_LIST_ITEM = Pattern.compile("(?m)^\\s*(?:[-*+]\\s+|\\d{1,2}[.)、]\\s*)");
    private static final Pattern LEADING_LABEL = Pattern.compile(
            "^(?:答案|回答|我的回答(?:是)?|答复|结论|理由|辩护|质询|回应)\\s*[：:]\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[。！？；!?;]+");
    private static final Pattern PUBLIC_EVIDENCE = Pattern.compile(
            "公开|回答|解释|理由|质询|回应|立场|选择|判断|前后|变化|记录|信息|行为|表述|投票|跟随|反驳|取舍");
    private static final Pattern ANSWER_STANCE = Pattern.compile(
            "我|选择|接受|不接受|保留|删除|愿意|倾向|支持|反对|应该|会|不会|判断|主张");
    private static final Pattern REASON_LINK = Pattern.compile(
            "因为|由于|所以|否则|理由|来自|在于|能|可以|需要|避免|让|但|而|却|若|如果|依据");
    private static final Pattern QUESTION_FORM = Pattern.compile("为什么|为何|如何|哪|什么|是否|能否|请.{0,12}(?:解释|说明|回答|指出)");
    private static final Pattern ESSAY_STYLE = Pattern.compile(
            "首先|其次|再次|最后|综上|总而言之|第一|第二|第三|从.+角度|以下(?:几点|内容)|作为(?:一个|一名)");

    private static final Set<String> EMPTY_SUSPICIONS = Set.of(
            "像ai", "像人工智能", "像真人", "感觉是他", "感觉就是他", "就是感觉", "凭感觉", "没有理由");
    private static final Set<String> EMPTY_RESPONSES = Set.of(
            "不是", "不知道", "不清楚", "你猜", "无可奉告", "没什么好说的");
    private static final Set<String> FORBIDDEN_DISCLOSURES = Set.of(
            "我是ai", "我是人工智能", "我是真人", "openai", "chatgpt", "claude", "gemini",
            "deepseek", "qwen", "通义千问", "kimi", "系统提示词", "api key", "base url");

    private TuringTextPolicy() {
    }

    public Rule rule(TuringHuntActionType type) {
        Rule rule = RULES.get(type);
        if (rule == null) throw new IllegalArgumentException("该行动没有公开文本规则: " + type);
        return rule;
    }

    /** 清洗公开文本，但不改变其中的否定、选择或观点。 */
    public String normalize(String raw) {
        if (raw == null) return "";
        String value = raw.replace("\u0000", "").replace("\uFEFF", "").trim();
        value = value.replaceFirst("(?is)^```(?:json|markdown|text)?\\s*", "")
                .replaceFirst("(?is)\\s*```\\s*$", "");
        value = LEADING_HEADING.matcher(value).replaceAll("");
        value = LEADING_LIST_ITEM.matcher(value).replaceAll("");
        value = LEADING_LABEL.matcher(value.trim()).replaceFirst("");
        value = normalizeQuotes(value);
        value = value.replace('\r', '\n').replaceAll("\\n+", "；")
                .replace(',', '，').replace(':', '：').replace(';', '；')
                .replace('?', '？').replace('!', '！');
        value = value.replaceAll("[ \\t\\f]+", " ")
                .replaceAll("\\s*([，。：；！？])\\s*", "$1")
                .replaceAll("，{2,}", "，")
                .replaceAll("。{2,}", "。")
                .replaceAll("：{2,}", "：")
                .replaceAll("；{2,}", "；")
                .replaceAll("！{2,}", "！")
                .replaceAll("？{2,}", "？")
                .replaceAll("(?:！？|？！){2,}", "？")
                .trim();
        return LEADING_LABEL.matcher(value).replaceFirst("").trim();
    }

    /** 模型超长时只在标点边界安全压缩；过短和语义不完整仍交给调用方纠偏重试。 */
    public String normalizeModelText(TuringHuntActionType type, String raw) {
        String normalized = normalize(raw);
        Rule rule = rule(type);
        if (codePointCount(normalized) <= rule.maximum()) return normalized;
        return compressAtPunctuation(normalized, rule.minimum(), rule.maximum());
    }

    public TextValidation validate(TuringHuntActionType type, String raw) {
        Rule rule = rule(type);
        String value = normalize(raw);
        int count = codePointCount(value);
        if (count < rule.minimum()) {
            return TextValidation.invalid(rule.label() + "还需 " + (rule.minimum() - count) + " 字");
        }
        if (count > rule.maximum()) {
            return TextValidation.invalid(rule.label() + "最多 " + rule.maximum() + " 字，当前 " + count + " 字");
        }
        int sentences = sentenceCount(value);
        if (sentences > rule.maximumSentences()) {
            return TextValidation.invalid(rule.label() + "最多 " + rule.maximumSentences() + " 句");
        }
        if (containsForbiddenDisclosure(value)) {
            return TextValidation.invalid("请只讨论公开游戏内容，不要提及身份、模型或系统信息");
        }

        return switch (type) {
            case ANSWER -> validateAnswer(value);
            case SUSPECT -> validateSuspicion(value);
            case QUESTION -> validateQuestion(value);
            case RESPOND -> validateResponse(value);
            case DEFEND -> validateDefense(value);
            case VOTE -> validateVote(value);
            case ADVANCE -> TextValidation.success();
        };
    }

    /** 只规范行动里的公开文本字段；compressModelText=true 仅供模型边界使用。 */
    public TuringHuntAction normalizeAction(TuringHuntAction action, boolean compressModelText) {
        if (action == null || action.type() == null || action.type() == TuringHuntActionType.ADVANCE) return action;
        String main = compressModelText
                ? normalizeModelText(action.type(), mainText(action))
                : normalize(mainText(action));
        String answer = action.answer();
        String reason = normalize(action.reasonSummary());
        String question = action.question();
        String response = action.response();
        String defense = action.defense();
        switch (action.type()) {
            case ANSWER -> answer = main;
            case SUSPECT, VOTE -> reason = main;
            case QUESTION -> question = main;
            case RESPOND -> response = main;
            case DEFEND -> defense = main;
            case ADVANCE -> { }
        }
        return new TuringHuntAction(action.actorPlayerId(), action.type(), answer,
                action.primarySuspectPlayerId(), action.suspectPlayerIds(), action.reasonTags(), reason,
                action.targetPlayerId(), question, response, defense, action.counterSuspectPlayerId());
    }

    public String mainText(TuringHuntAction action) {
        if (action == null || action.type() == null) return "";
        return switch (action.type()) {
            case ANSWER -> action.answer();
            case SUSPECT, VOTE -> action.reasonSummary();
            case QUESTION -> action.question();
            case RESPOND -> action.response();
            case DEFEND -> action.defense();
            case ADVANCE -> "";
        };
    }

    public int codePointCount(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    /** 仅供内部质量指标使用，不应进入游戏投影。 */
    public StyleSignals inspectStyle(String raw) {
        String value = normalize(raw);
        return new StyleSignals(ESSAY_STYLE.matcher(value).find(), sentenceCount(value),
                codePointCount(value), raw != null && !raw.equals(value));
    }

    private TextValidation validateAnswer(String value) {
        if (!ANSWER_STANCE.matcher(value).find()) {
            return TextValidation.invalid("请明确写出你的选择或立场");
        }
        if (!REASON_LINK.matcher(value).find()) {
            return TextValidation.invalid("请补充支持这个选择的具体理由");
        }
        return TextValidation.success();
    }

    private TextValidation validateSuspicion(String value) {
        String compact = compact(value);
        if (EMPTY_SUSPICIONS.stream().anyMatch(compact::contains)
                || (!PUBLIC_EVIDENCE.matcher(value).find() && !REASON_LINK.matcher(value).find())) {
            return TextValidation.invalid("请引用一条公开回答、立场变化或质询表现作为怀疑理由");
        }
        return TextValidation.success();
    }

    private TextValidation validateQuestion(String value) {
        long marks = value.chars().filter(ch -> ch == '？' || ch == '?').count();
        if (marks > 1 || !QUESTION_FORM.matcher(value).find()) {
            return TextValidation.invalid("一次只问一个围绕公开内容的核心问题");
        }
        if (!PUBLIC_EVIDENCE.matcher(value).find()) {
            return TextValidation.invalid("请围绕对方已经公开的回答或立场提问");
        }
        return TextValidation.success();
    }

    private TextValidation validateResponse(String value) {
        String compact = compact(value);
        if (EMPTY_RESPONSES.contains(compact)
                || compact.matches("^(?:(?:不知道|不清楚|不是|你猜)){2,}$")) {
            return TextValidation.invalid("请直接回答质询，并说明你的判断依据");
        }
        return TextValidation.success();
    }

    private TextValidation validateDefense(String value) {
        if (!PUBLIC_EVIDENCE.matcher(value).find() && !REASON_LINK.matcher(value).find()) {
            return TextValidation.invalid("请解释你为何被怀疑，并回应一条公开依据");
        }
        return TextValidation.success();
    }

    private TextValidation validateVote(String value) {
        if (!PUBLIC_EVIDENCE.matcher(value).find()) {
            return TextValidation.invalid("请用一条公开回答、质询或立场表现说明投票理由");
        }
        return TextValidation.success();
    }

    private String compressAtPunctuation(String value, int minimum, int maximum) {
        String window = prefixByCodePoints(value, maximum);
        int boundary = lastBoundary(window, "。！？；");
        if (boundary < 0 || codePointCount(window.substring(0, boundary + 1)) < minimum) {
            boundary = lastBoundary(window, "， ");
        }
        if (boundary >= 0) {
            String candidate = window.substring(0, boundary + 1).stripTrailing();
            if (codePointCount(candidate) >= minimum) return candidate;
        }
        // 没有任何可用边界时保持原文超长，让调用方进行一次纠偏；绝不在词中间硬截断。
        return value;
    }

    private int lastBoundary(String value, String boundaries) {
        for (int index = value.length() - 1; index >= 0; index--) {
            if (boundaries.indexOf(value.charAt(index)) >= 0) return index;
        }
        return -1;
    }

    private String prefixByCodePoints(String value, int maximum) {
        if (codePointCount(value) <= maximum) return value;
        return value.substring(0, value.offsetByCodePoints(0, maximum));
    }

    private int sentenceCount(String value) {
        if (value == null || value.isBlank()) return 0;
        return (int) List.of(SENTENCE_BOUNDARY.split(value)).stream()
                .filter(part -> !part.isBlank()).count();
    }

    private boolean containsForbiddenDisclosure(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return FORBIDDEN_DISCLOSURES.stream().anyMatch(item -> normalized.contains(item.replace(" ", "")));
    }

    private String compact(String value) {
        return normalize(value).toLowerCase(Locale.ROOT).replaceAll("[\\s，。：；！？、,.!?;:'\"“”‘’]+", "");
    }

    private String normalizeQuotes(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean doubleOpen = false;
        boolean singleOpen = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '"') {
                result.append(doubleOpen ? '”' : '“');
                doubleOpen = !doubleOpen;
            } else if (current == '\'') {
                result.append(singleOpen ? '’' : '‘');
                singleOpen = !singleOpen;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static Map<TuringHuntActionType, Rule> rules() {
        Map<TuringHuntActionType, Rule> result = new EnumMap<>(TuringHuntActionType.class);
        result.put(TuringHuntActionType.ANSWER, new Rule("回答", 30, 90, 2));
        result.put(TuringHuntActionType.SUSPECT, new Rule("怀疑理由", 15, 50, 2));
        result.put(TuringHuntActionType.QUESTION, new Rule("质询", 12, 45, 1));
        result.put(TuringHuntActionType.RESPOND, new Rule("回应", 20, 70, 2));
        result.put(TuringHuntActionType.DEFEND, new Rule("辩护", 25, 80, 2));
        result.put(TuringHuntActionType.VOTE, new Rule("投票理由", 12, 40, 1));
        return Map.copyOf(result);
    }

    public record Rule(String label, int minimum, int maximum, int maximumSentences) {
    }

    public record TextValidation(boolean valid, String message) {
        public static TextValidation success() {
            return new TextValidation(true, "校验通过");
        }

        public static TextValidation invalid(String message) {
            return new TextValidation(false, message);
        }
    }

    public record StyleSignals(boolean essayLike, int sentenceCount, int codePointCount, boolean normalized) {
    }
}
