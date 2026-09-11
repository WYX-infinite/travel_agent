package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.auth.CurrentUser;
import com.wyx.travelagent.auth.CurrentUserContext;
import com.wyx.travelagent.common.api.ApiResponse;
import com.wyx.travelagent.planning.task.PlanningTask;
import com.wyx.travelagent.planning.task.PlanningTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.wyx.travelagent.common.api.RequestValidationException;

import java.util.Objects;

/**
 * 业务问题：为已认证用户创建可持久化异步规划任务并查询自己的状态。
 * 输入来自 HTTP 和 CurrentUserContext，输出统一 ApiResponse；本 Controller 访问任务与偏好数据库并提交有界执行器，但不访问模型、文件或网络。
 * 它承接任务十三同步编排；认证由上层入口负责，不负责信任请求体身份或直接写 Mapper，SSE 与行程读取由各自服务负责。
 */
@RestController
@RequestMapping("/api/planning-tasks")
public final class TravelPlanningController {
    private final PlanningTaskService service;
    private final PlanningSseService sseService;

    /**
     * 创建任务 Controller。
     * @param service 非空异步任务服务
     * @param sseService 非空共享 SSE 重放服务
     * 构造不访问数据库、模型、缓存、文件或网络；认证由上层入口负责，请求时用户身份只从认证上下文读取。
     */
    public TravelPlanningController(PlanningTaskService service, PlanningSseService sseService) {
        this.service = Objects.requireNonNull(service, "service");
        this.sseService = Objects.requireNonNull(sseService, "sseService");
    }

    /**
     * 冻结请求并创建后台任务。
     * @param request 非空结构化请求，不含用户身份
     * @return HTTP 202 和 PENDING 快照
     * 本方法访问偏好与任务数据库并提交有界线程池，不访问文件或外部网络；认证由上层入口负责，身份只来自 CurrentUserContext。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PlanningTaskResponse>> create(@RequestBody TravelPlanningRequest request) {
        PlanningTask task = service.create(currentUser().userId(), Objects.requireNonNull(request, "request").toInput());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(PlanningTaskResponse.from(task)));
    }

    /**
     * 查询当前用户拥有的任务状态。
     * @param taskId 正任务编号
     * @return HTTP 200 和最新状态；不存在或跨用户统一由服务映射为 404
     * 本方法只查询任务数据库，不访问模型、缓存、文件或网络；认证由上层入口负责，身份只来自 CurrentUserContext。
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<PlanningTaskResponse>> get(@PathVariable long taskId) {
        return ResponseEntity.ok(ApiResponse.ok(PlanningTaskResponse.from(
                service.getOwned(taskId, currentUser().userId()))));
    }

    /**
     * 订阅当前用户任务的持久化进度事件。
     * @param taskId 正任务编号
     * @param lastEventId 可为空的 Last-Event-ID；存在时必须是非负十进制整数
     * @return text/event-stream 发射器，终态或断连时清理
     * 本方法查询任务/Trace 数据库并写 HTTP 流，不访问模型、缓存、文件或外部网络；认证由上层入口负责，不取消后台任务。
     */
    @GetMapping(value = "/{taskId}/events", produces = "text/event-stream")
    public SseEmitter events(@PathVariable long taskId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        long cursor = parseCursor(lastEventId);
        return sseService.subscribe(taskId, currentUser().userId(), cursor);
    }

    private static long parseCursor(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) throw new NumberFormatException("negative");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new RequestValidationException(java.util.Map.of("Last-Event-ID", "must be a non-negative integer"));
        }
    }

    private static CurrentUser currentUser() {
        return CurrentUserContext.get().orElseThrow(() -> new IllegalStateException("AUTHENTICATION_REQUIRED"));
    }
}
