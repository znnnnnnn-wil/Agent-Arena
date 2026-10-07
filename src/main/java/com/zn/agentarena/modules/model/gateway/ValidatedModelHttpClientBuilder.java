package com.zn.agentarena.modules.model.gateway;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Connects to validated DNS results while retaining the original host for SNI and certificate verification. */
final class ValidatedModelHttpClientBuilder implements HttpClientBuilder {
    private final ModelEndpointPolicy policy;
    private final URI endpoint;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(25);

    ValidatedModelHttpClientBuilder(ModelEndpointPolicy policy, String endpoint) {
        this.policy = policy;
        this.endpoint = URI.create(endpoint);
    }

    @Override public Duration connectTimeout() { return connectTimeout; }
    @Override public HttpClientBuilder connectTimeout(Duration value) { connectTimeout = value; return this; }
    @Override public Duration readTimeout() { return readTimeout; }
    @Override public HttpClientBuilder readTimeout(Duration value) { readTimeout = value; return this; }

    DnsResolver dnsResolver() {
        return new DnsResolver() {
            @Override public InetAddress[] resolve(String host) throws UnknownHostException {
                if (!endpoint.getHost().equalsIgnoreCase(host)) throw new UnknownHostException("Unexpected model host");
                return policy.resolvePublicAddresses(host);
            }
            @Override public String resolveCanonicalHostname(String host) { return host; }
        };
    }

    private CloseableHttpClient newClient() {
        var manager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(dnsResolver())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(timeout(connectTimeout))
                        .setSocketTimeout(timeout(readTimeout)).build()).build();
        return HttpClients.custom().setConnectionManager(manager)
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(timeout(readTimeout)).build())
                .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement().build();
    }

    private static Timeout timeout(Duration value) {
        return Timeout.ofMilliseconds((value == null ? Duration.ofSeconds(25) : value).toMillis());
    }

    private HttpUriRequestBase request(HttpRequest source) {
        URI uri = URI.create(source.url());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !endpoint.getHost().equalsIgnoreCase(uri.getHost())
                || effectivePort(endpoint) != effectivePort(uri) || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Model request must stay on its validated endpoint");
        }
        HttpUriRequestBase request = new HttpUriRequestBase(source.method().name(), uri);
        source.headers().forEach((name, values) -> values.forEach(value -> request.addHeader(name, value)));
        if (source.body() != null) request.setEntity(new StringEntity(source.body(), ContentType.APPLICATION_JSON));
        return request;
    }

    private static int effectivePort(URI uri) { return uri.getPort() == -1 ? 443 : uri.getPort(); }

    private static Map<String, List<String>> headers(ClassicHttpResponse response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (var header : response.getHeaders()) {
            headers.computeIfAbsent(header.getName(), ignored -> new ArrayList<>()).add(header.getValue());
        }
        return headers;
    }

    @Override public HttpClient build() {
        return new HttpClient() {
            @Override public SuccessfulHttpResponse execute(HttpRequest request) {
                try (CloseableHttpClient client = newClient()) {
                    return client.execute(ValidatedModelHttpClientBuilder.this.request(request), response -> {
                        String body = response.getEntity() == null ? ""
                                : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        if (response.getCode() < 200 || response.getCode() >= 300) {
                            throw new HttpException(response.getCode(), body);
                        }
                        return SuccessfulHttpResponse.builder().statusCode(response.getCode())
                                .headers(headers(response)).body(body).build();
                    });
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }

            @Override public void execute(HttpRequest request, ServerSentEventParser parser,
                                          ServerSentEventListener listener) {
                try (CloseableHttpClient client = newClient()) {
                    client.execute(ValidatedModelHttpClientBuilder.this.request(request), response -> {
                        if (response.getCode() < 200 || response.getCode() >= 300) {
                            throw new HttpException(response.getCode(), response.getEntity() == null ? ""
                                    : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
                        }
                        listener.onOpen(SuccessfulHttpResponse.builder().statusCode(response.getCode())
                                .headers(headers(response)).build());
                        if (response.getEntity() != null) parser.parse(response.getEntity().getContent(), listener);
                        listener.onClose();
                        return null;
                    });
                } catch (Exception exception) {
                    listener.onError(exception);
                }
            }
        };
    }
}
