package com.zn.agentarena.modules.spectator.service;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.config.AgentArenaProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameEventStreamServiceTest {

    @Test
    void enforcesConfiguredSubscriberLimit() {
        AgentArenaProperties properties = new AgentArenaProperties(null, null,
                new AgentArenaProperties.Stream(null, 1, 1));
        GameEventStreamService service = new GameEventStreamService(properties);

        service.subscribe(1L);
        CustomException exception = assertThrows(CustomException.class, () -> service.subscribe(1L));

        assertEquals(ResultCodeEnum.TOO_MANY_REQUESTS, exception.getResultCodeEnum());
    }
}
