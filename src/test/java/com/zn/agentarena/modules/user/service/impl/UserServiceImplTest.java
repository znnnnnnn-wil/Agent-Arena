package com.zn.agentarena.modules.user.service.impl;

import com.zn.agentarena.common.enums.ResultCodeEnum;
import com.zn.agentarena.common.exceptions.CustomException;
import com.zn.agentarena.dao.entity.User;
import com.zn.agentarena.dao.mapper.UserMapper;
import com.zn.agentarena.modules.user.dto.request.CreateVisitorUserRequest;
import com.zn.agentarena.modules.user.dto.request.LoginRequest;
import com.zn.agentarena.modules.user.dto.request.RegisterAccountRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 用户服务单元测试。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@DisplayName("用户服务单元测试")
class UserServiceImplTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Test
    @DisplayName("createVisitorUser: 应创建游客并返回一次性令牌")
    void testCreateVisitorUserSuccess() {
        UserMapper mapper = mock(UserMapper.class);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            return 1;
        }).when(mapper).insertSelective(any(User.class));
        UserServiceImpl service = new UserServiceImpl(mapper, passwordEncoder);
        CreateVisitorUserRequest request = new CreateVisitorUserRequest();
        request.setNickname("  测试观众  ");

        var response = service.createVisitorUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(mapper).insertSelective(captor.capture());
        assertEquals(7L, response.getUserId());
        assertEquals("测试观众", response.getNickname());
        assertNotNull(response.getVisitorToken());
        assertNotEquals(response.getVisitorToken(), captor.getValue().getVisitorToken());
    }

    @Test
    @DisplayName("getUserIdByVisitorToken: 无效令牌应抛出未登录异常")
    void testVerifyInvalidToken() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.selectOneByQuery(any())).thenReturn(null);
        UserServiceImpl service = new UserServiceImpl(mapper, passwordEncoder);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.getUserIdByVisitorToken("invalid-token"));

        assertEquals(ResultCodeEnum.NO_LOGIN, exception.getResultCodeEnum());
    }

    @Test
    @DisplayName("registerAccount: 应升级当前游客并保留用户ID")
    void registerUpgradesExistingVisitor() {
        UserMapper mapper = mock(UserMapper.class);
        User visitor = User.builder().id(9L).nickname("原游客").visitorToken("old-hash").status("ACTIVE").build();
        when(mapper.selectOneByQuery(any())).thenReturn(null);
        when(mapper.selectOneById(9L)).thenReturn(visitor);
        when(mapper.update(any(User.class))).thenReturn(1);
        UserServiceImpl service = new UserServiceImpl(mapper, passwordEncoder);
        RegisterAccountRequest request = new RegisterAccountRequest();
        request.setUsername("  Arena_User  ");
        request.setNickname("新昵称");
        request.setPassword("safe-password");

        var response = service.registerAccount(9L, request);

        assertEquals(9L, response.getUserId());
        assertEquals("arena_user", response.getUsername());
        assertEquals("新昵称", response.getNickname());
        assertTrue(response.getRegistered());
        assertTrue(passwordEncoder.matches("safe-password", visitor.getPasswordHash()));
        assertNotEquals(response.getVisitorToken(), visitor.getVisitorToken());
        verify(mapper).update(visitor);
    }

    @Test
    @DisplayName("login: 应校验密码并轮换访问令牌")
    void loginRotatesAccessToken() {
        UserMapper mapper = mock(UserMapper.class);
        User account = User.builder().id(11L).nickname("Alice").username("alice")
                .passwordHash(passwordEncoder.encode("correct-password"))
                .visitorToken("old-hash").status("ACTIVE").build();
        when(mapper.selectOneByQuery(any())).thenReturn(account);
        when(mapper.update(account)).thenReturn(1);
        UserServiceImpl service = new UserServiceImpl(mapper, passwordEncoder);
        LoginRequest request = new LoginRequest();
        request.setUsername("ALICE");
        request.setPassword("correct-password");

        var response = service.login(request);

        assertEquals(11L, response.getUserId());
        assertEquals("alice", response.getUsername());
        assertNotNull(response.getVisitorToken());
        assertNotEquals(response.getVisitorToken(), account.getVisitorToken());
        verify(mapper).update(account);
    }

    @Test
    @DisplayName("login: 错误密码不得返回会话")
    void loginRejectsWrongPassword() {
        UserMapper mapper = mock(UserMapper.class);
        User account = User.builder().id(12L).username("alice")
                .passwordHash(passwordEncoder.encode("correct-password")).status("ACTIVE").build();
        when(mapper.selectOneByQuery(any())).thenReturn(account);
        UserServiceImpl service = new UserServiceImpl(mapper, passwordEncoder);
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong-password");

        CustomException exception = assertThrows(CustomException.class, () -> service.login(request));

        assertEquals(ResultCodeEnum.LOGIN_FAILED, exception.getResultCodeEnum());
        verify(mapper, never()).update(any(User.class));
    }
}
