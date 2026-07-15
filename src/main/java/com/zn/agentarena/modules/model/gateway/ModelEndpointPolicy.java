package com.zn.agentarena.modules.model.gateway;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.config.AgentArenaProperties;
import com.zn.agentarena.modules.model.domain.ModelProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Prevents user-defined model endpoints from reaching local or private infrastructure. */
@Component
@lombok.RequiredArgsConstructor
public class ModelEndpointPolicy {

    private static final Map<ModelProvider, Set<String>> OFFICIAL_HOSTS = Map.ofEntries(
            Map.entry(ModelProvider.OPENAI, Set.of("api.openai.com")),
            Map.entry(ModelProvider.DEEPSEEK, Set.of("api.deepseek.com")),
            Map.entry(ModelProvider.QWEN, Set.of("dashscope.aliyuncs.com")),
            Map.entry(ModelProvider.DOUBAO, Set.of("ark.cn-beijing.volces.com")),
            Map.entry(ModelProvider.QIANFAN, Set.of("qianfan.baidubce.com")),
            Map.entry(ModelProvider.ZHIPU, Set.of("open.bigmodel.cn")),
            Map.entry(ModelProvider.MOONSHOT, Set.of("api.moonshot.cn")),
            Map.entry(ModelProvider.YI, Set.of("api.lingyiwanwu.com")),
            Map.entry(ModelProvider.MINIMAX, Set.of("api.minimaxi.com")),
            Map.entry(ModelProvider.HUNYUAN, Set.of("api.hunyuan.cloud.tencent.com")),
            Map.entry(ModelProvider.BAICHUAN, Set.of("api.baichuan-ai.com")),
            Map.entry(ModelProvider.SILICONFLOW, Set.of("api.siliconflow.cn")));

    private final AgentArenaProperties properties;

    public void validate(ModelProvider provider, String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            if (provider == ModelProvider.OPENAI_COMPATIBLE || provider == ModelProvider.SPARK) {
                throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "该供应商必须配置公开 HTTPS Base URL");
            }
            return;
        }
        if (provider == ModelProvider.OLLAMA) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "Ollama 地址由服务端管理员配置，不允许用户覆盖");
        }
        try {
            URI uri = URI.create(baseUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw rejected();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if ("localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local")) {
                throw rejected();
            }
            boolean official = OFFICIAL_HOSTS.getOrDefault(provider, Set.of()).contains(host);
            if (!official && !properties.model().allowedCustomHosts().contains(host)) {
                throw CustomException.create(ResultCodeEnum.NO_PERMISSION,
                        "模型 Base URL 主机不在服务端 MODEL_ALLOWED_HOSTS 白名单中");
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivate(address)) {
                    throw rejected();
                }
            }
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw CustomException.create(ResultCodeEnum.PARAMS_ERROR, "Base URL 无法解析为公开 HTTPS 地址");
        }
    }

    private boolean isPrivate(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            int first = address.getAddress()[0] & 0xff;
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    private CustomException rejected() {
        return CustomException.create(ResultCodeEnum.NO_PERMISSION, "模型 Base URL 不允许访问本机、私网或非 HTTPS 地址");
    }
}
