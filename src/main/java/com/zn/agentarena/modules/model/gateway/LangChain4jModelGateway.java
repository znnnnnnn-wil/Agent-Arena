package com.zn.agentarena.modules.model.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.modules.model.domain.ModelRuntimeConfig;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 LangChain4j 的模型调用网关。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LangChain4jModelGateway implements ModelGateway, StructuredModelDiagnostics {

    private final ThreadLocal<String> lastRawResponse = new ThreadLocal<>();
    private final ThreadLocal<Boolean> lastParserRepair = ThreadLocal.withInitial(() -> false);

    private static final int MAX_REPAIR_CONTENT_LENGTH = 4_000;
    private static final List<String> WRAPPER_FIELD_NAMES = List.of("decision", "data", "result", "output");
    private static final Pattern FIRST_NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");
    private static final ObjectMapper RELAXED_JSON_READER = new ObjectMapper(JsonFactory.builder()
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build());

    private final ModelFactory modelFactory;
    private final ObjectMapper objectMapper;
    private final ModelCallConcurrencyLimiter concurrencyLimiter;

    @Override
    /** 调用模型生成纯文本响应。 */
    public String generate(ModelRuntimeConfig config, String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "模型提示词不能为空");
        }
        return concurrencyLimiter.execute(() -> {
            try {
                ChatModel model = modelFactory.create(config);
                return model.chat(prompt);
            } catch (CustomException exception) {
                throw exception;
            } catch (Exception exception) {
                log.warn("模型调用失败: provider={}, model={}, reason={}",
                        config == null ? null : config.provider(), config == null ? null : config.modelName(),
                        exception.getClass().getSimpleName());
                if (isTimeout(exception)) {
                    throw CustomException.create(ResultCodeEnum.MODEL_CALL_TIMEOUT, "模型调用超时");
                }
                throw CustomException.create(ResultCodeEnum.MODEL_CALL_FAILED, "模型调用失败");
            }
        });
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String typeName = current.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (typeName.contains("timeout") || typeName.contains("timedout")) return true;
            current = current.getCause();
        }
        return false;
    }

    @Override
    /** 调用模型并将 JSON 响应转换为指定类型。 */
    public <T> T generateStructured(ModelRuntimeConfig config, String prompt, Class<T> responseType) {
        if (responseType == null) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "结构化响应类型不能为空");
        }

        lastParserRepair.set(false);
        String firstResponse = generate(config, prompt);
        lastRawResponse.set(firstResponse);
        log.debug("模型结构化响应已返回: responseType={}, contentLength={}",
                responseType.getSimpleName(), firstResponse == null ? 0 : firstResponse.length());
        if (!StringUtils.hasText(firstResponse)) {
            throw CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, "模型返回了空响应");
        }
        try {
            return parseStructured(firstResponse, responseType);
        } catch (JsonProcessingException firstException) {
            log.debug("模型结构化响应首次解析失败，执行一次格式修复: responseType={}, reason={}",
                    responseType.getSimpleName(), firstException.getClass().getSimpleName());
        }

        String repairedResponse = generate(config, buildRepairPrompt(firstResponse, responseType));
        lastRawResponse.set(repairedResponse);
        lastParserRepair.set(true);
        log.debug("模型格式修复响应已返回: responseType={}, contentLength={}",
                responseType.getSimpleName(), repairedResponse == null ? 0 : repairedResponse.length());
        if (!StringUtils.hasText(repairedResponse)) {
            throw CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, "模型格式修复返回了空响应");
        }
        try {
            T result = parseStructured(repairedResponse, responseType);
            log.info("模型结构化响应经格式修复后解析成功: responseType={}", responseType.getSimpleName());
            return result;
        } catch (JsonProcessingException secondException) {
            log.warn("模型结构化响应连续两次解析失败: responseType={}, reason={}",
                    responseType.getSimpleName(), secondException.getClass().getSimpleName());
            throw CustomException.create(ResultCodeEnum.MODEL_RESPONSE_INVALID, "模型返回内容无法解析为有效决策");
        }
    }

    /** 最近一次结构化调用的模型正文，供受控诊断读取；不包含请求头或凭证。 */
    public String lastRawResponse() { return lastRawResponse.get(); }

    public boolean lastParserRepair() { return Boolean.TRUE.equals(lastParserRepair.get()); }

    private <T> T parseStructured(String content, Class<T> responseType) throws JsonProcessingException {
        JsonProcessingException lastException = null;
        for (String candidate : buildJsonCandidates(content)) {
            try {
                JsonNode root = readJsonTree(candidate);
                root = unwrapKnownEnvelope(root);
                normalizeFieldNames(root, responseType);
                normalizeScalarValues(root, responseType);
                return objectMapper.treeToValue(root, responseType);
            } catch (JsonProcessingException exception) {
                lastException = exception;
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        return objectMapper.readValue("", responseType);
    }

    private JsonNode readJsonTree(String candidate) throws JsonProcessingException {
        try {
            return objectMapper.readTree(candidate);
        } catch (JsonProcessingException strictException) {
            return RELAXED_JSON_READER.readTree(candidate);
        }
    }

    private Set<String> buildJsonCandidates(String content) {
        Set<String> candidates = new LinkedHashSet<>();
        String withoutFence = stripMarkdownCodeFence(content);
        addCandidate(candidates, withoutFence);
        addCandidate(candidates, extractFirstJsonObject(withoutFence));

        String punctuationNormalized = normalizeJsonPunctuation(withoutFence);
        addCandidate(candidates, punctuationNormalized);
        addCandidate(candidates, extractFirstJsonObject(punctuationNormalized));
        return candidates;
    }

    private void addCandidate(Set<String> candidates, String candidate) {
        if (StringUtils.hasText(candidate)) {
            candidates.add(candidate.trim());
        }
    }

    private JsonNode unwrapKnownEnvelope(JsonNode root) {
        JsonNode current = root;
        for (int depth = 0; depth < 3 && current != null; depth++) {
            if (current.isArray()) {
                if (current.isEmpty()) return current;
                current = current.get(0);
                continue;
            }
            if (!current.isObject()) break;
            JsonNode nested = null;
            var fields = current.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (WRAPPER_FIELD_NAMES.stream().anyMatch(name -> name.equalsIgnoreCase(field.getKey()))
                        && (field.getValue().isObject() || field.getValue().isArray())) {
                    nested = field.getValue();
                    break;
                }
            }
            if (nested == null) {
                break;
            }
            current = nested;
        }
        return current;
    }

    private void normalizeFieldNames(JsonNode root, Class<?> responseType) {
        if (!(root instanceof ObjectNode objectNode) || !responseType.isRecord()) {
            return;
        }
        List<String> existingNames = new ArrayList<>();
        objectNode.fieldNames().forEachRemaining(existingNames::add);

        for (RecordComponent component : responseType.getRecordComponents()) {
            String expectedName = component.getName();
            if (objectNode.has(expectedName)) {
                continue;
            }
            String snakeCaseName = toSnakeCase(expectedName);
            for (String existingName : existingNames) {
                if (existingName.equalsIgnoreCase(expectedName) || existingName.equalsIgnoreCase(snakeCaseName)) {
                    objectNode.set(expectedName, objectNode.get(existingName));
                    if (!existingName.equals(expectedName)) objectNode.remove(existingName);
                    break;
                }
            }
        }
    }

    private void normalizeScalarValues(JsonNode root, Class<?> responseType) {
        if (!(root instanceof ObjectNode objectNode) || !responseType.isRecord()) {
            return;
        }
        for (RecordComponent component : responseType.getRecordComponents()) {
            JsonNode value = objectNode.get(component.getName());
            Class<?> type = component.getType();
            if (value == null || value.isNull()) continue;
            if (Collection.class.isAssignableFrom(type)) {
                normalizeCollectionValue(objectNode, component, value);
                continue;
            }
            if (!value.isTextual()) continue;
            Matcher matcher = FIRST_NUMBER_PATTERN.matcher(value.textValue());
            if (!matcher.find()) continue;
            String numericValue = matcher.group();
            try {
                if (type == Long.class || type == long.class) {
                    objectNode.put(component.getName(), Long.parseLong(numericValue));
                } else if (type == Integer.class || type == int.class) {
                    objectNode.put(component.getName(), Integer.parseInt(numericValue));
                } else if (type == Double.class || type == double.class) {
                    objectNode.put(component.getName(), Double.parseDouble(numericValue));
                } else if (type == Float.class || type == float.class) {
                    objectNode.put(component.getName(), Float.parseFloat(numericValue));
                }
            } catch (NumberFormatException ignored) {
                // 保留原始文本，让标准类型转换给出明确的解析失败。
            }
        }
    }

    private void normalizeCollectionValue(ObjectNode objectNode, RecordComponent component, JsonNode value) {
        Class<?> elementType = collectionElementType(component.getGenericType());
        ArrayNode normalized = objectMapper.createArrayNode();
        if (value.isArray()) {
            value.forEach(item -> normalized.add(normalizeCollectionItem(item, elementType)));
        } else {
            normalized.add(normalizeCollectionItem(value, elementType));
        }
        objectNode.set(component.getName(), normalized);
    }

    private JsonNode normalizeCollectionItem(JsonNode item, Class<?> elementType) {
        if (item == null || !item.isTextual() || !Number.class.isAssignableFrom(elementType)) return item;
        Matcher matcher = FIRST_NUMBER_PATTERN.matcher(item.textValue());
        if (!matcher.find()) return item;
        try {
            if (elementType == Integer.class) return objectMapper.getNodeFactory().numberNode(Integer.parseInt(matcher.group()));
            if (elementType == Long.class) return objectMapper.getNodeFactory().numberNode(Long.parseLong(matcher.group()));
            if (elementType == Double.class) return objectMapper.getNodeFactory().numberNode(Double.parseDouble(matcher.group()));
        } catch (NumberFormatException ignored) {
            // 让标准转换器处理原值并返回明确的解析失败。
        }
        return item;
    }

    private Class<?> collectionElementType(Type type) {
        if (type instanceof ParameterizedType parameterizedType
                && parameterizedType.getActualTypeArguments().length == 1
                && parameterizedType.getActualTypeArguments()[0] instanceof Class<?> elementType) {
            return elementType;
        }
        return Object.class;
    }

    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private String stripMarkdownCodeFence(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replace("\uFEFF", "").trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("(?is)^```(?:json)?\\s*", "")
                    .replaceFirst("(?is)\\s*```\\s*$", "");
        }
        return normalized.trim();
    }

    private String normalizeJsonPunctuation(String content) {
        if (content == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(content.length());
        boolean inString = false;
        char delimiter = 0;
        boolean escaped = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (!inString) {
                if (current == '"' || current == '\'' || current == '\u201C') {
                    inString = true;
                    delimiter = current;
                    escaped = false;
                    result.append(current == '\u201C' ? '"' : current);
                } else if (current == '\uFF1A') {
                    result.append(':');
                } else if (current == '\uFF0C') {
                    result.append(',');
                } else {
                    result.append(current);
                }
                continue;
            }

            if (delimiter == '\u201C') {
                if (current == '\u201D') {
                    inString = false;
                    result.append('"');
                } else {
                    result.append(current);
                }
                continue;
            }
            result.append(current);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == delimiter) {
                inString = false;
            }
        }
        return removeTrailingCommasOutsideStrings(result.toString());
    }

    /** 仅移除 JSON 结构中的尾逗号，绝不改写公开发言字符串里的“,}”等正文。 */
    private String removeTrailingCommasOutsideStrings(String content) {
        StringBuilder result = new StringBuilder(content.length());
        boolean inString = false;
        char delimiter = 0;
        boolean escaped = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (inString) {
                result.append(current);
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == delimiter) inString = false;
                continue;
            }
            if (current == '"' || current == '\'') {
                inString = true;
                delimiter = current;
                result.append(current);
                continue;
            }
            if (current == ',') {
                int next = index + 1;
                while (next < content.length() && Character.isWhitespace(content.charAt(next))) next++;
                if (next < content.length() && (content.charAt(next) == '}' || content.charAt(next) == ']')) {
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }

    private String extractFirstJsonObject(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        int start = content.indexOf('{');
        if (start < 0) {
            return "";
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        char delimiter = 0;
        for (int index = start; index < content.length(); index++) {
            char current = content.charAt(index);
            if (inString) {
                if (delimiter == '\u201C' && current == '\u201D') {
                    inString = false;
                    continue;
                }
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == delimiter) {
                    inString = false;
                }
                continue;
            }
            if (current == '"' || current == '\'' || current == '\u201C') {
                inString = true;
                delimiter = current;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return content.substring(start, index + 1);
            }
        }
        return "";
    }

    private String buildRepairPrompt(String invalidResponse, Class<?> responseType) {
        String fields = responseType.isRecord()
                ? String.join(", ", java.util.Arrays.stream(responseType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList())
                : "目标类型要求的字段";
        String safeResponse = invalidResponse == null ? "" : invalidResponse;
        if (safeResponse.length() > MAX_REPAIR_CONTENT_LENGTH) {
            safeResponse = safeResponse.substring(0, MAX_REPAIR_CONTENT_LENGTH);
        }
        return """
                你上一条响应无法被系统解析。请仅修复格式，不要改变原本的行动意图。
                只返回一个合法 JSON 对象，不要包含 Markdown、代码围栏、说明文字或额外字段。
                必须使用双引号，字段名为：%s。
                JSON 结构示例：%s
                数字字段必须是 JSON 数字；如果原文使用“5号”或“80%%”，只保留其中数字。
                待修复响应如下：
                <invalid-response>
                %s
                </invalid-response>
                """.formatted(fields, buildJsonShape(responseType), safeResponse);
    }

    private String buildJsonShape(Class<?> responseType) {
        if (!responseType.isRecord()) return "{}";
        return "{" + String.join(",", java.util.Arrays.stream(responseType.getRecordComponents())
                .map(component -> "\"" + component.getName() + "\":" + exampleValue(component.getType()))
                .toList()) + "}";
    }

    private String exampleValue(Class<?> type) {
        if (type == String.class || type.isEnum()) return "\"\"";
        if (type == boolean.class || type == Boolean.class) return "false";
        if (type.isPrimitive() || Number.class.isAssignableFrom(type)) return "0";
        if (Collection.class.isAssignableFrom(type)) return "[]";
        return "null";
    }
}
