package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.auth.CurrentUser;
import com.wyx.travelagent.auth.CurrentUserContext;
import com.wyx.travelagent.common.api.ApiResponse;
import com.wyx.travelagent.planning.memory.PreferenceMemoryService;
import com.wyx.travelagent.planning.memory.UserTravelPreference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 业务问题：让已认证用户读取、整体替换和幂等删除自己的长期旅行偏好。
 * 输入来自 HTTP JSON 和 CurrentUserContext，输出统一 ApiResponse；本 Controller 访问偏好数据库，不调用模型、缓存、文件、线程池或网络。
 * 它承接任务十三偏好输入；认证由上层入口负责，不接受客户端 userId、不做局部 PATCH，也不负责规划任务或任务十五页面。
 */
@RestController
@RequestMapping("/api/user/travel-preferences")
public final class UserTravelPreferenceController {
    private final PreferenceMemoryService service;
    private final Clock clock;

    /**
     * 创建偏好 Controller。
     * @param service 非空偏好服务
     * @param clock 非空服务端时钟
     * 构造不访问数据库、模型、缓存、文件或网络；认证由上层入口负责，请求时从认证上下文取得用户。
     */
    public UserTravelPreferenceController(PreferenceMemoryService service, Clock clock) {
        this.service = Objects.requireNonNull(service, "service"); this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 读取当前用户偏好。
     * @return HTTP 200；无记录时 data 为 {}
     * 本方法读取偏好数据库，不访问模型、缓存、文件或网络；认证由上层入口负责，用户身份只来自 CurrentUserContext。
     */
    @GetMapping
    public ResponseEntity<ApiResponse<UserTravelPreferenceResponse>> get() {
        int userId = currentUser().userId();
        return ResponseEntity.ok(ApiResponse.ok(service.find(userId)
                .map(UserTravelPreferenceResponse::from).orElseGet(UserTravelPreferenceResponse::empty)));
    }

    /**
     * 整体替换当前用户偏好。
     * @param request 非空 JSON 请求，不含也不接受 userId 语义
     * @return HTTP 200 和服务端 updatedAt
     * 本方法写偏好数据库，不访问模型、缓存、文件或网络；认证由上层入口负责，null 字段清除旧值。
     */
    @PutMapping
    public ResponseEntity<ApiResponse<UserTravelPreferenceResponse>> put(@RequestBody UserTravelPreferenceRequest request) {
        int userId = currentUser().userId();
        UserTravelPreference preference = Objects.requireNonNull(request, "request").toDomain(userId, Instant.now(clock));
        service.replace(userId, preference);
        return ResponseEntity.ok(ApiResponse.ok(UserTravelPreferenceResponse.from(preference)));
    }

    /**
     * 幂等删除当前用户偏好。
     * @return HTTP 200 的成功响应，无记录时也成功
     * 本方法写偏好数据库，不访问模型、缓存、文件或网络；认证由上层入口负责，用户身份只来自 CurrentUserContext。
     */
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete() {
        service.clear(currentUser().userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private static CurrentUser currentUser() {
        return CurrentUserContext.get().orElseThrow(() -> new IllegalStateException("AUTHENTICATION_REQUIRED"));
    }
}
