package com.zn.agentarena.modules.turing.agent;

import com.zn.agentarena.modules.turing.domain.TuringHuntAction;
import com.zn.agentarena.modules.turing.domain.TuringHuntActionType;
import com.zn.agentarena.modules.turing.text.TuringTextPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** 内部文本公平性指标；任何计数和耗时都不会进入对局公开投影。 */
@Component
@Slf4j
public class TuringHuntFairnessMetrics {

    private final Map<TuringHuntActionType, Counters> counters = new EnumMap<>(TuringHuntActionType.class);

    public TuringHuntFairnessMetrics() {
        for (TuringHuntActionType type : TuringHuntActionType.values()) {
            if (type != TuringHuntActionType.ADVANCE) counters.put(type, new Counters());
        }
    }

    public void record(TuringHuntActionType type, TuringHuntAction action, long elapsedNanos,
                       boolean retried, boolean fallback, boolean compressed) {
        Counters value = counters.get(type);
        if (value == null) return;
        String text = TuringTextPolicy.INSTANCE.mainText(action);
        TuringTextPolicy.StyleSignals style = TuringTextPolicy.INSTANCE.inspectStyle(text);
        value.actions.increment();
        value.totalCodePoints.add(style.codePointCount());
        value.totalElapsedNanos.add(Math.max(0, elapsedNanos));
        long elapsedMs = Math.max(0, elapsedNanos) / 1_000_000L;
        if (elapsedMs < 500) value.latencyUnder500Ms.increment();
        else if (elapsedMs < 1_500) value.latencyUnder1500Ms.increment();
        else if (elapsedMs < 5_000) value.latencyUnder5000Ms.increment();
        else value.latencyOver5000Ms.increment();
        if (retried) value.retries.increment();
        if (fallback) value.fallbacks.increment();
        if (compressed) value.compressions.increment();
        if (style.essayLike()) value.essayStyle.increment();

        long count = value.actions.sum();
        log.debug("图灵潜伏局内部文本指标: action={}, count={}, avgChars={}, retry={}, fallback={}, "
                        + "compressed={}, essayStyle={}, avgLatencyMs={}, latencyBuckets=[<500:{},<1500:{},<5000:{},>=5000:{}]",
                type, count, value.totalCodePoints.sum() / Math.max(1, count), value.retries.sum(),
                value.fallbacks.sum(), value.compressions.sum(), value.essayStyle.sum(),
                value.totalElapsedNanos.sum() / Math.max(1, count) / 1_000_000L,
                value.latencyUnder500Ms.sum(), value.latencyUnder1500Ms.sum(),
                value.latencyUnder5000Ms.sum(), value.latencyOver5000Ms.sum());
    }

    private static final class Counters {
        private final LongAdder actions = new LongAdder();
        private final LongAdder totalCodePoints = new LongAdder();
        private final LongAdder totalElapsedNanos = new LongAdder();
        private final LongAdder retries = new LongAdder();
        private final LongAdder fallbacks = new LongAdder();
        private final LongAdder compressions = new LongAdder();
        private final LongAdder essayStyle = new LongAdder();
        private final LongAdder latencyUnder500Ms = new LongAdder();
        private final LongAdder latencyUnder1500Ms = new LongAdder();
        private final LongAdder latencyUnder5000Ms = new LongAdder();
        private final LongAdder latencyOver5000Ms = new LongAdder();
    }
}
