package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.auth.CurrentUserContext;
import com.wyx.travelagent.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Objects;

/**
 * 业务问题：向已认证用户公开自己的行程列表、详情、版本历史和重新规划入口。
 * 输入来自路径及 CurrentUserContext，输出统一 ApiResponse；本 Controller 查询数据库并可提交有界规划线程池，不访问模型、文件或网络。
 * 它承接任务十三行程结果；认证由上层入口负责，跨用户统一 404，不接收 userId，不暴露 Mapper/JSON/Trace，也不负责任务十五页面。
 */
@RestController
@RequestMapping("/api/plans")
public final class PlanController {
    private final TravelPlanQueryService service;
    /**
     * @param service 非空用户隔离查询服务
     * 构造不访问数据库、模型、缓存、文件或网络；认证由上层入口负责，身份只在请求时读取。
     */
    public PlanController(TravelPlanQueryService service) { this.service = Objects.requireNonNull(service, "service"); }

    /**
     * @return HTTP 200 和当前用户全部行程摘要
     * 本方法只查询数据库，不访问模型、缓存、文件或网络；认证由上层入口负责，身份来自 CurrentUserContext。
     */
    @GetMapping public ResponseEntity<ApiResponse<List<PlanSummaryResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(service.list(userId())));
    }

    /**
     * @param planId 正行程编号
     * @return HTTP 200 详情；不存在或跨用户统一 404
     * 本方法只查询数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @GetMapping("/{planId}") public ResponseEntity<ApiResponse<PlanDetailResponse>> detail(@PathVariable long planId) {
        return ResponseEntity.ok(ApiResponse.ok(service.detail(planId, userId())));
    }

    /**
     * @param planId 正行程编号
     * @return HTTP 200 版本号升序列表；不存在或跨用户统一 404
     * 本方法只查询数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    @GetMapping("/{planId}/versions") public ResponseEntity<ApiResponse<List<PlanVersionResponse>>> versions(@PathVariable long planId) {
        return ResponseEntity.ok(ApiResponse.ok(service.versions(planId, userId())));
    }

    /**
     * @param planId 正行程编号
     * @return HTTP 202 和新 PENDING 任务
     * 本方法访问数据库、偏好与有界线程池，不访问文件或外部网络；认证由上层入口负责，不直接调用模型。
     */
    @PostMapping("/{planId}/replan") public ResponseEntity<ApiResponse<PlanningTaskResponse>> replan(@PathVariable long planId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(
                PlanningTaskResponse.from(service.replan(planId, userId()))));
    }

    private static int userId() {
        return CurrentUserContext.get().orElseThrow(() -> new IllegalStateException("AUTHENTICATION_REQUIRED")).userId();
    }
}
