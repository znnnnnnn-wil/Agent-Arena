package com.zn.agentarena.modules.model.gateway;

/** 提供最近一次结构化响应正文和修复标记；不得包含请求头或凭证。 */
public interface StructuredModelDiagnostics {
    String lastRawResponse();
    boolean lastParserRepair();
}
