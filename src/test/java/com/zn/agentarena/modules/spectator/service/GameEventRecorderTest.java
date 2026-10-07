package com.zn.agentarena.modules.spectator.service;

import com.zn.agentarena.dao.entity.GameEvent;
import com.zn.agentarena.dao.mapper.GameEventMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GameEventRecorderTest {
    @Test
    void completeHistoryIncludesEventsBeyondFiveThousand() {
        GameEventMapper mapper = mock(GameEventMapper.class);
        List<GameEvent> history = LongStream.rangeClosed(1, 5_001)
                .mapToObj(sequence -> GameEvent.builder().gameId(7L).sequenceNo(sequence).build()).toList();
        AtomicInteger page = new AtomicInteger();
        when(mapper.selectListByQuery(any())).thenAnswer(ignored -> {
            int start = page.getAndIncrement() * 500;
            return new ArrayList<>(history.subList(start, Math.min(start + 500, history.size())));
        });
        GameEventRecorder recorder = new GameEventRecorder(mapper, mock(GameEventStreamService.class));

        List<GameEvent> result = recorder.listAllEvents(7L);

        assertEquals(5_001, result.size());
        assertEquals(1L, result.get(0).getSequenceNo());
        assertEquals(5_001L, result.get(5_000).getSequenceNo());
        verify(mapper, times(11)).selectListByQuery(any());
    }
}
