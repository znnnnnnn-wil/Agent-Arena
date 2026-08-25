package com.zn.agentarena.modules.lineup.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.zn.agentarena.common.enums.GameMode;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.dao.entity.Game;
import com.zn.agentarena.dao.entity.GamePlayer;
import com.zn.agentarena.dao.entity.LineupTemplate;
import com.zn.agentarena.dao.mapper.GameMapper;
import com.zn.agentarena.dao.mapper.GamePlayerMapper;
import com.zn.agentarena.dao.mapper.LineupTemplateMapper;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.battle.dto.request.CreateBattleGameRequest;
import com.zn.agentarena.modules.game.dto.request.CreateWerewolfGameRequest;
import com.zn.agentarena.modules.game.dto.response.GameResponse;
import com.zn.agentarena.modules.game.service.IGameService;
import com.zn.agentarena.modules.lineup.dto.request.CreateGameFromTemplateRequest;
import com.zn.agentarena.modules.lineup.dto.request.CreateLineupTemplateRequest;
import com.zn.agentarena.modules.lineup.dto.response.LineupTemplateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 阵容模板保存和复用服务。 */
@Service
@RequiredArgsConstructor
public class LineupTemplateService {
    private final LineupTemplateMapper templateMapper;
    private final GameMapper gameMapper;
    private final GamePlayerMapper gamePlayerMapper;
    private final UserMapper userMapper;
    private final IGameService gameService;

    /**
     * 查询用户保存的阵容模板。
     *
     * @param userId 用户标识
     * @return 阵容模板列表
     */
    public List<LineupTemplateResponse> list(Long userId) {
        ensureUser(userId);
        return templateMapper.selectListByQuery(QueryWrapper.create()
                        .eq(LineupTemplate::getUserId, userId).orderBy(LineupTemplate::getUpdateTime, false).limit(50))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    /**
     * 将用户拥有的游戏阵容保存为模板。
     *
     * @param userId 用户标识
     * @param gameId 源游戏标识
     * @param request 模板创建参数
     * @return 已创建的阵容模板
     */
    public LineupTemplateResponse createFromGame(Long userId, Long gameId,
                                                 CreateLineupTemplateRequest request) {
        Game game = requireOwnedGame(userId, gameId);
        List<GamePlayer> players = players(gameId);
        List<Map<String, Object>> slots = new ArrayList<>();
        for (GamePlayer player : players) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("displayName", player.getDisplayName());
            slot.put("modelConfigId", player.getModelConfigId());
            slot.put("agentProfileId", player.getAgentProfileId());
            slots.add(slot);
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("publicGame", game.getPublicGame());
        config.put("maxPlayers", game.getMaxPlayers());
        config.put("ruleConfig", game.getRuleConfig() == null ? Map.of() : game.getRuleConfig());
        config.put("slots", slots);
        LineupTemplate template = LineupTemplate.builder().userId(userId).name(request.getName().trim())
                .mode(game.getMode()).templateConfig(config).build();
        templateMapper.insertSelective(template);
        return toResponse(template);
    }

    /**
     * 根据用户的阵容模板创建一场游戏。
     *
     * @param userId 用户标识
     * @param templateId 阵容模板标识
     * @param request 创建参数
     * @return 已创建的游戏
     */
    public GameResponse createGame(Long userId, Long templateId, CreateGameFromTemplateRequest request) {
        LineupTemplate template = requireOwnedTemplate(userId, templateId);
        Map<String, Object> config = template.getTemplateConfig() == null ? Map.of() : template.getTemplateConfig();
        List<Map<String, Object>> slots = mapList(config.get("slots"));
        List<Long> modelIds = slots.stream().map(slot -> longValue(slot.get("modelConfigId"))).toList();
        List<Long> profileIds = slots.stream().map(slot -> longValue(slot.get("agentProfileId"))).toList();
        List<String> names = slots.stream().map(slot -> String.valueOf(slot.getOrDefault("displayName", "AI 玩家"))).toList();
        String gameName = StringUtils.hasText(request.getName()) ? request.getName().trim() : template.getName() + " · 新对局";
        boolean publicGame = request.getPublicGame() != null ? request.getPublicGame()
                : Boolean.TRUE.equals(config.get("publicGame"));

        if (template.getMode() == GameMode.WEREWOLF) {
            CreateWerewolfGameRequest gameRequest = new CreateWerewolfGameRequest();
            gameRequest.setName(gameName);
            gameRequest.setPublicGame(publicGame);
            gameRequest.setModelConfigIds(hasAny(modelIds) ? modelIds : List.of());
            gameRequest.setAgentProfileIds(hasAny(profileIds) ? profileIds : List.of());
            gameRequest.setPlayerDisplayNames(names);
            return gameService.createWerewolfGame(userId, gameRequest);
        }
        CreateBattleGameRequest gameRequest = new CreateBattleGameRequest();
        gameRequest.setName(gameName);
        gameRequest.setPublicGame(publicGame);
        gameRequest.setPlayerCount(intValue(config.get("maxPlayers"), slots.size()));
        Map<String, Object> rules = mapValue(config.get("ruleConfig"));
        gameRequest.setMaxRounds(intValue(rules.get("maxRounds"), 20));
        gameRequest.setModelConfigIds(hasAny(modelIds) ? modelIds : List.of());
        gameRequest.setAgentProfileIds(hasAny(profileIds) ? profileIds : List.of());
        gameRequest.setPlayerDisplayNames(names);
        return gameService.createBattleGame(userId, gameRequest);
    }

    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除用户拥有的阵容模板。
     *
     * @param userId 用户标识
     * @param templateId 阵容模板标识
     */
    public void delete(Long userId, Long templateId) {
        LineupTemplate template = requireOwnedTemplate(userId, templateId);
        if (templateMapper.deleteById(template.getId()) != 1) {
            throw CustomException.create(ResultCodeEnum.FAIL, "阵容模板删除失败，请重试");
        }
    }

    private LineupTemplateResponse toResponse(LineupTemplate template) {
        Map<String, Object> config = template.getTemplateConfig() == null ? Map.of() : template.getTemplateConfig();
        List<Map<String, Object>> slots = mapList(config.get("slots"));
        return LineupTemplateResponse.builder().id(template.getId()).name(template.getName()).mode(template.getMode())
                .playerCount(slots.size()).modelSeatCount((int) slots.stream().filter(slot -> slot.get("modelConfigId") != null).count())
                .personaSeatCount((int) slots.stream().filter(slot -> slot.get("agentProfileId") != null).count())
                .publicGame(Boolean.TRUE.equals(config.get("publicGame")))
                .playerNames(slots.stream().map(slot -> String.valueOf(slot.getOrDefault("displayName", "AI 玩家"))).toList())
                .createTime(template.getCreateTime()).updateTime(template.getUpdateTime()).build();
    }

    private Game requireOwnedGame(Long userId, Long gameId) {
        Game game = gameId == null ? null : gameMapper.selectOneById(gameId);
        if (game == null || userId == null || !userId.equals(game.getOwnerUserId())) {
            throw CustomException.create(ResultCodeEnum.GAME_NOT_FOUND);
        }
        return game;
    }

    private LineupTemplate requireOwnedTemplate(Long userId, Long templateId) {
        LineupTemplate template = templateId == null ? null : templateMapper.selectOneById(templateId);
        if (template == null || userId == null || !userId.equals(template.getUserId())) {
            throw CustomException.create(ResultCodeEnum.RESOURCE_NOT_FOUND, "阵容模板不存在");
        }
        return template;
    }

    private List<GamePlayer> players(Long gameId) {
        return gamePlayerMapper.selectListByQuery(QueryWrapper.create().eq(GamePlayer::getGameId, gameId)
                .orderBy(GamePlayer::getSeatNo, true));
    }

    private void ensureUser(Long userId) {
        if (userId == null || userMapper.selectOneById(userId) == null) {
            throw CustomException.create(ResultCodeEnum.USER_NOT_FOUND);
        }
    }

    private boolean hasAny(List<Long> ids) {
        return ids.stream().anyMatch(id -> id != null);
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        return value instanceof List<?> list ? list.stream().filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item).toList() : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
