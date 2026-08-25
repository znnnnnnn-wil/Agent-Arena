package com.zn.agentarena.modules.lineup.service;

import com.zn.agentarena.common.enums.GameMode;
import com.zn.agentarena.common.enums.GameStatus;
import com.zn.agentarena.dao.entity.Game;
import com.zn.agentarena.dao.entity.GamePlayer;
import com.zn.agentarena.dao.entity.LineupTemplate;
import com.zn.agentarena.dao.mapper.GameMapper;
import com.zn.agentarena.dao.mapper.GamePlayerMapper;
import com.zn.agentarena.dao.mapper.LineupTemplateMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.game.dto.request.CreateWerewolfGameRequest;
import com.zn.agentarena.modules.game.dto.response.GameResponse;
import com.zn.agentarena.modules.game.service.IGameService;
import com.zn.agentarena.modules.lineup.dto.request.CreateGameFromTemplateRequest;
import com.zn.agentarena.modules.lineup.dto.request.CreateLineupTemplateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LineupTemplateServiceTest {

    private LineupTemplateMapper templateMapper;
    private GameMapper gameMapper;
    private GamePlayerMapper playerMapper;
    private IGameService gameService;
    private LineupTemplateService service;

    @BeforeEach
    void setUp() {
        templateMapper = mock(LineupTemplateMapper.class);
        gameMapper = mock(GameMapper.class);
        playerMapper = mock(GamePlayerMapper.class);
        gameService = mock(IGameService.class);
        service = new LineupTemplateService(templateMapper, gameMapper, playerMapper,
                mock(UserMapper.class), gameService);
    }

    @Test
    void savesModelsPersonasNamesAndRulesAsLineupSnapshot() {
        Game source = Game.builder().id(9L).ownerUserId(1L).mode(GameMode.BATTLE_ROYALE)
                .maxPlayers(6).publicGame(true).ruleConfig(Map.of("maxRounds", 24)).build();
        when(gameMapper.selectOneById(9L)).thenReturn(source);
        when(playerMapper.selectListByQuery(any())).thenReturn(List.of(
                GamePlayer.builder().seatNo(1).displayName("Alpha").modelConfigId(11L).agentProfileId(21L).build(),
                GamePlayer.builder().seatNo(2).displayName("Bravo").modelConfigId(null).agentProfileId(22L).build()));
        doAnswer(invocation -> {
            LineupTemplate template = invocation.getArgument(0);
            template.setId(3L);
            return 1;
        }).when(templateMapper).insertSelective(any(LineupTemplate.class));
        CreateLineupTemplateRequest request = new CreateLineupTemplateRequest();
        request.setName("  双人格阵容  ");

        var response = service.createFromGame(1L, 9L, request);

        assertEquals(3L, response.getId());
        assertEquals("双人格阵容", response.getName());
        assertEquals(1, response.getModelSeatCount());
        assertEquals(2, response.getPersonaSeatCount());
        ArgumentCaptor<LineupTemplate> captor = ArgumentCaptor.forClass(LineupTemplate.class);
        verify(templateMapper).insertSelective(captor.capture());
        Map<String, Object> config = captor.getValue().getTemplateConfig();
        assertEquals(Map.of("maxRounds", 24), config.get("ruleConfig"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> slots = (List<Map<String, Object>>) config.get("slots");
        assertEquals("Alpha", slots.get(0).get("displayName"));
        assertNull(slots.get(1).get("modelConfigId"));
        assertEquals(22L, slots.get(1).get("agentProfileId"));
    }

    @Test
    void recreatesWerewolfGameWithoutCollapsingEmptySeatAssignments() {
        List<Map<String, Object>> slots = new ArrayList<>();
        List<Long> modelIds = Arrays.asList(11L, null, 11L, null, null, 11L, null, null);
        List<Long> personaIds = Arrays.asList(21L, 22L, null, null, 21L, null, null, 22L);
        for (int index = 0; index < 8; index++) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("displayName", "Agent-" + (index + 1));
            slot.put("modelConfigId", modelIds.get(index));
            slot.put("agentProfileId", personaIds.get(index));
            slots.add(slot);
        }
        LineupTemplate template = LineupTemplate.builder().id(3L).userId(1L).name("推理阵容")
                .mode(GameMode.WEREWOLF).templateConfig(Map.of(
                        "publicGame", true, "maxPlayers", 8, "ruleConfig", Map.of(), "slots", slots)).build();
        when(templateMapper.selectOneById(3L)).thenReturn(template);
        when(gameService.createWerewolfGame(eq(1L), any())).thenReturn(GameResponse.builder()
                .id(44L).mode(GameMode.WEREWOLF).status(GameStatus.WAITING).build());

        var response = service.createGame(1L, 3L, new CreateGameFromTemplateRequest());

        assertEquals(44L, response.getId());
        ArgumentCaptor<CreateWerewolfGameRequest> captor = ArgumentCaptor.forClass(CreateWerewolfGameRequest.class);
        verify(gameService).createWerewolfGame(eq(1L), captor.capture());
        assertEquals(modelIds, captor.getValue().getModelConfigIds());
        assertEquals(personaIds, captor.getValue().getAgentProfileIds());
        assertEquals("Agent-8", captor.getValue().getPlayerDisplayNames().get(7));
        assertEquals("推理阵容 · 新对局", captor.getValue().getName());
    }
}
