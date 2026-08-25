package com.zn.agentarena.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Type-safe runtime settings for model calls, game execution and streaming. */
@ConfigurationProperties(prefix = "agent-arena")
public record AgentArenaProperties(Model model, Game game, Stream stream) {

    public AgentArenaProperties {
        model = model == null ? new Model(null, null, null, null, null) : model;
        game = game == null ? new Game(null, null) : game;
        stream = stream == null ? new Stream(null, null, null) : stream;
    }

    public record Model(Duration readTimeout, Integer maxRetries,
                        Integer callsPerMinute, List<String> allowedCustomHosts,
                        Integer maxConcurrentCalls) {
        public Model {
            readTimeout = readTimeout == null ? Duration.ofSeconds(25) : readTimeout;
            maxRetries = maxRetries == null ? 0 : Math.max(0, maxRetries);
            callsPerMinute = callsPerMinute == null ? 20 : Math.max(1, callsPerMinute);
            allowedCustomHosts = allowedCustomHosts == null ? List.of() : allowedCustomHosts.stream()
                    .filter(host -> host != null && !host.isBlank())
                    .map(host -> host.trim().toLowerCase(Locale.ROOT)).distinct().toList();
            maxConcurrentCalls = maxConcurrentCalls == null ? 8 : Math.max(1, maxConcurrentCalls);
        }
    }

    public record Game(Integer snapshotRetention, Integer turnsPerMinute) {
        public Game {
            snapshotRetention = snapshotRetention == null ? 5 : Math.max(1, snapshotRetention);
            turnsPerMinute = turnsPerMinute == null ? 120 : Math.max(1, turnsPerMinute);
        }
    }

    public record Stream(Duration heartbeatInterval, Integer maxSubscribersPerGame,
                         Integer maxTotalSubscribers) {
        public Stream {
            heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(20) : heartbeatInterval;
            maxSubscribersPerGame = maxSubscribersPerGame == null ? 100 : Math.max(1, maxSubscribersPerGame);
            maxTotalSubscribers = maxTotalSubscribers == null ? 1_000 : Math.max(1, maxTotalSubscribers);
        }
    }
}
