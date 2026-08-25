package com.zn.agentarena.modules.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Local account login request. */
@Data
public class LoginRequest {

    /** 账号用户名 */
    @NotBlank(message = "请输入用户名")
    @Size(max = 24, message = "用户名不能超过 24 个字符")
    private String username;

    /** 账号密码 */
    @NotBlank(message = "请输入密码")
    @Size(max = 72, message = "密码不能超过 72 个字符")
    private String password;
}
