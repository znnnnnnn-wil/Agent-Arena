package com.zn.agentarena.modules.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Register a local account or upgrade the current visitor account. */
@Data
public class RegisterAccountRequest {

    @NotBlank(message = "请输入用户名")
    @Size(min = 3, max = 24, message = "用户名长度需要在 3 到 24 个字符之间")
    @Pattern(regexp = "^[\\p{L}\\p{N}_-]+$", message = "用户名只能包含文字、数字、下划线和短横线")
    private String username;

    @Size(max = 32, message = "昵称不能超过 32 个字符")
    private String nickname;

    @NotBlank(message = "请输入密码")
    @Size(min = 8, max = 72, message = "密码长度需要在 8 到 72 个字符之间")
    private String password;
}
