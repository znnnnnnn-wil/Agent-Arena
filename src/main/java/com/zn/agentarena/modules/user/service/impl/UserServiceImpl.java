package com.zn.agentarena.modules.user.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.dao.entity.User;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.user.dto.request.CreateVisitorUserRequest;
import com.zn.agentarena.modules.user.dto.request.LoginRequest;
import com.zn.agentarena.modules.user.dto.request.RegisterAccountRequest;
import com.zn.agentarena.modules.user.dto.response.AuthSessionResponse;
import com.zn.agentarena.modules.user.dto.response.CurrentUserResponse;
import com.zn.agentarena.modules.user.service.IUserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * 用户服务实现类。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Service
public class UserServiceImpl implements IUserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("agent-arena-invalid-login-probe");
    }

    /** 创建游客账号并返回可用于后续访问的令牌。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthSessionResponse createVisitorUser(CreateVisitorUserRequest request) {
        String nickname = normalizeNickname(request == null ? null : request.getNickname());
        String visitorToken = generateToken();
        User user = User.builder()
                .nickname(nickname)
                .visitorToken(sha256(visitorToken))
                .status("ACTIVE")
                .build();
        userMapper.insertSelective(user);
        return toSession(user, visitorToken);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 将当前访客升级为账号用户，或创建并登录一个账号。
     *
     * @param currentUserId 当前访客用户标识，可为空
     * @param request 注册信息
     * @return 已认证的会话信息
     */
    public AuthSessionResponse registerAccount(Long currentUserId, RegisterAccountRequest request) {
        String username = normalizeUsername(request.getUsername());
        if (findByUsername(username) != null) {
            throw CustomException.create(ResultCodeEnum.USERNAME_ALREADY_EXISTS);
        }

        User user = currentUserId == null ? null : userMapper.selectOneById(currentUserId);
        if (user != null && StringUtils.hasText(user.getUsername())) {
            throw CustomException.create(ResultCodeEnum.USERNAME_ALREADY_EXISTS, "当前身份已经绑定账号，请先退出再注册");
        }

        String rawToken = generateToken();
        if (user == null) {
            user = User.builder()
                    .nickname(normalizeAccountNickname(request.getNickname(), username))
                    .username(username)
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .visitorToken(sha256(rawToken))
                    .status("ACTIVE")
                    .build();
            insertAccount(user);
        } else {
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setVisitorToken(sha256(rawToken));
            if (StringUtils.hasText(request.getNickname())) {
                user.setNickname(normalizeAccountNickname(request.getNickname(), username));
            }
            updateAccount(user);
        }
        return toSession(user, rawToken);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 校验账号凭据并创建登录会话。
     *
     * @param request 登录凭据
     * @return 已认证的会话信息
     */
    public AuthSessionResponse login(LoginRequest request) {
        String username = normalizeUsername(request.getUsername());
        User user = findByUsername(username);
        String storedHash = user == null || !StringUtils.hasText(user.getPasswordHash())
                ? dummyPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), storedHash);
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !passwordMatches) {
            throw CustomException.create(ResultCodeEnum.LOGIN_FAILED);
        }

        String rawToken = generateToken();
        user.setVisitorToken(sha256(rawToken));
        userMapper.update(user);
        return toSession(user, rawToken);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 使指定用户当前的服务端会话失效。
     *
     * @param userId 用户标识
     */
    public void logout(Long userId) {
        User user = requireUser(userId);
        user.setVisitorToken(sha256(generateToken()));
        userMapper.update(user);
    }

    @Override
    /**
     * 查询当前认证用户的基本信息。
     *
     * @param userId 用户标识
     * @return 当前用户信息
     */
    public CurrentUserResponse getCurrentUser(Long userId) {
        User user = requireUser(userId);
        return CurrentUserResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .username(user.getUsername())
                .registered(StringUtils.hasText(user.getUsername()))
                .build();
    }

    /** 校验游客令牌并返回对应用户标识。 */
    @Override
    public Long getUserIdByVisitorToken(String visitorToken) {
        if (!StringUtils.hasText(visitorToken)) {
            throw CustomException.create(ResultCodeEnum.NO_LOGIN);
        }
        User user = userMapper.selectOneByQuery(QueryWrapper.create()
                .eq(User::getVisitorToken, sha256(visitorToken.trim())));
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw CustomException.create(ResultCodeEnum.NO_LOGIN);
        }
        return user.getId();
    }

    private User findByUsername(String username) {
        return userMapper.selectOneByQuery(QueryWrapper.create().eq(User::getUsername, username));
    }

    private User requireUser(Long userId) {
        User user = userId == null ? null : userMapper.selectOneById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw CustomException.create(ResultCodeEnum.USER_NOT_FOUND);
        }
        return user;
    }

    private void insertAccount(User user) {
        try {
            userMapper.insertSelective(user);
        } catch (DuplicateKeyException exception) {
            throw CustomException.create(ResultCodeEnum.USERNAME_ALREADY_EXISTS);
        }
    }

    private void updateAccount(User user) {
        try {
            userMapper.update(user);
        } catch (DuplicateKeyException exception) {
            throw CustomException.create(ResultCodeEnum.USERNAME_ALREADY_EXISTS);
        }
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAccountNickname(String nickname, String username) {
        return StringUtils.hasText(nickname) ? nickname.trim() : username;
    }

    private AuthSessionResponse toSession(User user, String rawToken) {
        return AuthSessionResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .username(user.getUsername())
                .visitorToken(rawToken)
                .registered(StringUtils.hasText(user.getUsername()))
                .build();
    }

    private String normalizeNickname(String nickname) {
        if (StringUtils.hasText(nickname)) {
            return nickname.trim();
        }
        return "观众-" + String.format("%04d", SECURE_RANDOM.nextInt(10000));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception exception) {
            throw CustomException.create(ResultCodeEnum.FAIL, "用户令牌处理失败");
        }
    }
}
