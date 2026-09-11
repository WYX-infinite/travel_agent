package com.wyx.travelagent.planning.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 业务问题：在服务启动后把无法安全续跑的遗留 RUNNING 任务失败化。
 * 输入是任务仓储与时钟，输出为恢复数量；本组件只访问数据库，不调用模型、缓存、文件、线程池或网络，也不处理认证。
 * 它承接任务十三执行结果并处理启动恢复；不负责恢复执行到一半的模型/工具序列，也不改写终态或实现前端重试。
 */
@Component
public final class PlanningTaskRecovery {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanningTaskRecovery.class);
    private static final String CODE = "SERVER_RESTARTED";
    private static final String REASON = "planning stopped because the server restarted";
    private final PlanningTaskRepository repository;
    private final Clock clock;

    /**
     * 创建启动恢复器。
     * @param repository 非空任务仓储
     * @param clock 非空可测试时钟
     * 构造不访问外部资源，也不处理认证；真正数据库更新在 recover 中发生。
     */
    public PlanningTaskRecovery(PlanningTaskRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 幂等失败化全部遗留 RUNNING 任务。
     * @return 本次实际更新的任务数，可能为零
     * 服务重启后不能从模型或工具序列中点续跑，因为缺少可验证执行上下文；本方法写数据库，不访问模型、缓存、文件或网络；认证由上层入口负责。
     */
    public int recover() {
        return repository.failRunningTasks(Instant.now(clock), CODE, REASON);
    }

    /**
     * 在 Spring 应用就绪时触发一次恢复。
     * @param event 非空应用就绪事件
     * 本方法无返回值，只委托 recover 写数据库；存储暂时不可用时记录脱敏告警而不阻断启动，不调用模型、网络或线程池，也不处理认证。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            recover();
        } catch (PlanningTaskException exception) {
            LOGGER.warn("Planning task startup recovery was skipped: {}", exception.code());
        }
    }
}
