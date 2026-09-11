package com.wyx.travelagent.planning.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wyx.travelagent.planning.memory.UserTravelPreference;

import java.time.Instant;
import java.util.List;

/**
 * 业务问题：以显式、安全字段返回当前用户长期偏好，缺失记录仍保留四个稳定偏好字段。
 * 输入是领域偏好，输出为 JSON DTO；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它只服务任务十四偏好 API；不暴露 userId、数据库行或其他用户数据，也不负责保存。
 * @param interests 非空；缺失记录时为空列表
 * @param pace 可为空枚举名称
 * @param travelMode 可为空枚举名称
 * @param accommodationPreference 可为空住宿偏好
 * @param updatedAt 可为空；存在记录时为服务端更新时间
 */
public record UserTravelPreferenceResponse(List<String> interests, String pace, String travelMode,
                                           String accommodationPreference,
                                           @JsonInclude(JsonInclude.Include.NON_NULL) Instant updatedAt) {
    /**
     * 创建缺失记录响应。
     * @return interests 为空列表、其余四个值为 null 的响应；JSON 仍明确包含四个偏好字段
     * 不访问外部资源，也不处理认证。
     */
    public static UserTravelPreferenceResponse empty() { return new UserTravelPreferenceResponse(List.of(), null, null, null, null); }

    /**
     * 从领域偏好创建显式 DTO。
     * @param value 非空且已经用户隔离的偏好
     * @return 不含 userId 的响应
     * 不访问外部资源；所有权检查由服务层完成，方法不处理认证。
     */
    public static UserTravelPreferenceResponse from(UserTravelPreference value) {
        return new UserTravelPreferenceResponse(value.interests().stream().map(Enum::name).toList(),
                value.pace() == null ? null : value.pace().name(),
                value.travelMode() == null ? null : value.travelMode().name(),
                value.accommodationPreference(), value.updatedAt());
    }
}
