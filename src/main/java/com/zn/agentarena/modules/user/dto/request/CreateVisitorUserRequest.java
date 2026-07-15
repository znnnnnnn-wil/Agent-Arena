package com.zn.agentarena.modules.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建游客用户请求。
 *
 * @author zhangxin
 * @date 2026/07/12
 */
@Data
public class CreateVisitorUserRequest {

    /** 用户昵称，为空时由系统生成。 */
    @Size(max = 32, message = "用户昵称不能超过32个字符")
    private String nickname;
}
