package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.task.PlanningTask;
import com.wyx.travelagent.planning.task.PlanningTaskException;
import com.wyx.travelagent.planning.task.PlanningTaskService;
import com.wyx.travelagent.trace.PersistedPlanningTrace;
import com.wyx.travelagent.trace.PlanningStep;
import com.wyx.travelagent.trace.PlanningTraceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;

/**
 * 业务问题：用一个共享定时轮询器向有界 SSE 连接重放持久化 Trace，并在终态后清理。
 * 输入是已认证用户、任务和 Last-Event-ID，输出 SseEmitter；本服务查询任务/Trace 数据库并写 HTTP 流，不调用模型、缓存、文件或外部网络。
 * 它承接任务十三 Trace；认证由上层入口负责，断连只清理订阅而不取消后台任务，不创建每连接线程，也不负责写 Trace。
 */
@Service
public final class PlanningSseService {
    private static final int BATCH_SIZE = 100;
    private final PlanningTaskService taskService;
    private final PlanningTraceService traceService;
    private final long timeoutMs;
    private final int maxConnections;
    private final LongFunction<SseEmitter> emitterFactory;
    private final ConcurrentHashMap<UUID, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final AtomicInteger connectionCount = new AtomicInteger();

    /**
     * 创建共享 SSE 服务。
     * @param taskService 非空用户隔离任务服务
     * @param traceService 非空 Trace 查询服务
     * @param timeoutMs 正连接超时毫秒
     * @param maxConnections 正全局连接上限
     * 构造不访问数据库、模型、缓存、文件或网络；轮询只使用 Spring 共享调度线程，认证由上层入口负责。
     */
    @Autowired
    public PlanningSseService(PlanningTaskService taskService, PlanningTraceService traceService,
                              @Value("${travel.planning.sse.timeout-ms:1800000}") long timeoutMs,
                              @Value("${travel.planning.sse.max-connections:100}") int maxConnections) {
        this.taskService = Objects.requireNonNull(taskService, "taskService");
        this.traceService = Objects.requireNonNull(traceService, "traceService");
        if (timeoutMs <= 0 || maxConnections <= 0) throw new IllegalArgumentException("INVALID_SSE_LIMITS");
        this.timeoutMs = timeoutMs; this.maxConnections = maxConnections;
        this.emitterFactory = SseEmitter::new;
    }

    PlanningSseService(PlanningTaskService taskService, PlanningTraceService traceService,
                       long timeoutMs, int maxConnections, LongFunction<SseEmitter> emitterFactory) {
        this.taskService = Objects.requireNonNull(taskService, "taskService");
        this.traceService = Objects.requireNonNull(traceService, "traceService");
        if (timeoutMs <= 0 || maxConnections <= 0) throw new IllegalArgumentException("INVALID_SSE_LIMITS");
        this.timeoutMs = timeoutMs; this.maxConnections = maxConnections;
        this.emitterFactory = Objects.requireNonNull(emitterFactory, "emitterFactory");
    }

    /**
     * 创建经过所有权检查的订阅，并立即重放游标后的持久化事件。
     * @param taskId 正任务编号
     * @param userId 认证层提供的正用户编号
     * @param lastEventId 非负排他游标，缺失时为零
     * @return 非空 SSE 发射器
     * @throws PlanningTaskException 任务不存在/跨用户或连接上限已满时抛出稳定错误
     * 本方法查询数据库并写 HTTP 流，不访问模型、缓存、文件或外部网络；认证由上层入口负责，不取消任务。
     */
    public SseEmitter subscribe(long taskId, int userId, long lastEventId) {
        if (lastEventId < 0) throw new IllegalArgumentException("lastEventId");
        taskService.getOwned(taskId, userId);
        if (connectionCount.incrementAndGet() > maxConnections) {
            connectionCount.decrementAndGet();
            throw new PlanningTaskException("PLANNING_SSE_LIMIT_REACHED",
                    "planning event connection limit reached", HttpStatus.SERVICE_UNAVAILABLE);
        }
        UUID id = UUID.randomUUID();
        SseEmitter emitter = emitterFactory.apply(timeoutMs);
        Subscription subscription = new Subscription(taskId, userId, emitter, lastEventId);
        subscriptions.put(id, subscription);
        Runnable cleanup = () -> remove(id);
        emitter.onCompletion(cleanup); emitter.onTimeout(cleanup); emitter.onError(error -> cleanup.run());
        drain(id, subscription);
        return emitter;
    }

    /**
     * 使用单个 Spring 调度任务轮询全部有界订阅。
     * 无参数且无返回值；查询任务/Trace 数据库并写 HTTP 流，不访问模型、缓存、文件或外部网络；认证由上层入口负责。
     */
    @Scheduled(fixedDelayString = "${travel.planning.sse.poll-interval-ms:500}")
    public void poll() {
        subscriptions.forEach(this::drain);
    }

    private void drain(UUID id, Subscription subscription) {
        // 即时重放和共享轮询可能相遇；每个订阅一次只允许一个 drain，防止两个查询从同一游标重复发送。
        if (!subscription.draining.compareAndSet(false, true)) return;
        try {
            boolean sentTerminalTrace = false;
            while (true) {
                List<PersistedPlanningTrace> events = traceService.findAfter(
                        subscription.taskId, subscription.cursor, BATCH_SIZE);
                if (events.isEmpty()) break;
                for (PersistedPlanningTrace persisted : events) {
                    if (persisted.eventId() <= subscription.cursor) continue;
                    PlanningSseEvent projected = project(persisted);
                    subscription.emitter.send(SseEmitter.event().id(Long.toString(persisted.eventId()))
                            .name("planning-progress").data(projected));
                    subscription.cursor = persisted.eventId();
                    sentTerminalTrace |= isTerminalStatus(projected.status());
                }
                if (events.size() < BATCH_SIZE) break;
            }
            PlanningTask task = taskService.getOwned(subscription.taskId, subscription.userId);
            if (task.status().isTerminal()) {
                if (!sentTerminalTrace) sendTaskTerminalSnapshot(subscription, task);
                subscription.emitter.complete();
                remove(id);
            }
        } catch (IOException | RuntimeException exception) {
            // 客户端断开或一次查询失败只终止该连接；后台规划及其他订阅继续运行。
            subscription.emitter.completeWithError(exception);
            remove(id);
        } finally {
            subscription.draining.set(false);
        }
    }

    private PlanningSseEvent project(PersistedPlanningTrace persisted) {
        var event = persisted.event();
        return new PlanningSseEvent(persisted.eventId(), event.taskId(), event.step().name(),
                publicStatus(event.step(), event.status()), progress(event.step()), message(event.step(), event.status()),
                event.errorCode(), event.createdAt());
    }

    private void sendTaskTerminalSnapshot(Subscription subscription, PlanningTask task) throws IOException {
        // 重启恢复可能只有 planning_task 终态而没有 Trace；发送任务事实不等于伪造一条数据库 Trace。
        PlanningTaskSseEvent snapshot = new PlanningTaskSseEvent(subscription.taskId, task.status().name(),
                task.currentStep(), PlanningTaskResponse.from(task).progressPercent(), task.planId(),
                task.failureCode(), taskMessage(task), task.updatedAt());
        // planning-status 不携带 Trace id，避免把任务快照伪装成 Last-Event-ID 可确认的 planning_trace 行。
        subscription.emitter.send(SseEmitter.event().name("planning-status").data(snapshot));
    }

    private void remove(UUID id) {
        if (subscriptions.remove(id) != null) connectionCount.decrementAndGet();
    }

    private static int progress(PlanningStep step) {
        return switch (step) {
            case REQUIREMENT -> 10;
            case KNOWLEDGE_RETRIEVAL -> 25;
            case TOOL_EXECUTION, PREPARATION -> 40;
            case GENERATION -> 60;
            case VALIDATION, REPAIR -> 80;
            case COMPLETION -> 100;
        };
    }

    private static String publicStatus(PlanningStep step, com.wyx.travelagent.trace.TraceStatus status) {
        if (status == com.wyx.travelagent.trace.TraceStatus.FAILED) return "FAILED";
        if (step != PlanningStep.COMPLETION) return "RUNNING";
        return status == com.wyx.travelagent.trace.TraceStatus.DEGRADED
                ? "NEEDS_ADJUSTMENT" : "COMPLETED";
    }

    private static boolean isTerminalStatus(String status) {
        return "COMPLETED".equals(status) || "NEEDS_ADJUSTMENT".equals(status) || "FAILED".equals(status);
    }

    private static String message(PlanningStep step, com.wyx.travelagent.trace.TraceStatus status) {
        if (status == com.wyx.travelagent.trace.TraceStatus.FAILED) return "planning failed";
        if (step == PlanningStep.COMPLETION && status == com.wyx.travelagent.trace.TraceStatus.DEGRADED) {
            return "planning needs adjustment";
        }
        return switch (step) {
            case REQUIREMENT -> "requirements prepared";
            case KNOWLEDGE_RETRIEVAL -> "knowledge retrieval finished";
            case TOOL_EXECUTION -> "travel tools finished";
            case PREPARATION -> "planning facts prepared";
            case GENERATION -> "itinerary generation finished";
            case VALIDATION -> "itinerary validation finished";
            case REPAIR -> "itinerary repair finished";
            case COMPLETION -> "planning completed";
        };
    }

    private static String taskMessage(PlanningTask task) {
        return switch (task.status()) {
            case COMPLETED -> "planning completed";
            case NEEDS_ADJUSTMENT -> "planning needs adjustment";
            case FAILED -> switch (task.failureCode() == null ? "" : task.failureCode()) {
                case "SERVER_RESTARTED" -> "planning stopped because the server restarted";
                case "CONTEXT_BUDGET_EXCEEDED" -> "planning context budget exceeded";
                default -> "planning task execution failed";
            };
            case PENDING, RUNNING -> "planning is running";
        };
    }

    private static final class Subscription {
        private final long taskId; private final int userId; private final SseEmitter emitter;
        private final AtomicBoolean draining = new AtomicBoolean();
        private volatile long cursor;
        private Subscription(long taskId, int userId, SseEmitter emitter, long cursor) {
            this.taskId = taskId; this.userId = userId; this.emitter = emitter; this.cursor = cursor;
        }
    }
}
