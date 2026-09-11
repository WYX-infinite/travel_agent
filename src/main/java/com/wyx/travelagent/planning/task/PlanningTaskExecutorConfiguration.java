package com.wyx.travelagent.planning.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 业务问题：为耗时规划提供小型固定线程池和有界排队能力，避免请求洪峰耗尽内存。
 * 输入是可覆盖的池大小、队列容量和停机等待秒数，输出为 Spring 任务执行器；本配置创建线程池但不访问数据库、模型、缓存、文件或网络，也不处理认证。
 * 它承接任务十三同步业务并服务异步边界；不负责执行任务十三业务、不创建 SSE 每连接线程，也不实现任务十五页面。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class PlanningTaskExecutorConfiguration {
    /**
     * 创建固定大小、有界队列且明确拒绝的规划执行器。
     * @param poolSize 正固定工作线程数，core 与 max 相同
     * @param queueCapacity 正有界队列容量
     * @param awaitTerminationSeconds 非负停机等待秒数
     * @return 已初始化的非空执行器
     * 队列满时使用 AbortPolicy；不能用 CallerRunsPolicy，否则 HTTP 请求线程会执行完整规划。本方法创建线程池，不访问数据库、模型、缓存、文件或网络；认证由上层入口负责。
     */
    @Bean(name = "planningTaskExecutor")
    public ThreadPoolTaskExecutor planningTaskExecutor(
            @Value("${travel.planning.executor.pool-size:2}") int poolSize,
            @Value("${travel.planning.executor.queue-capacity:8}") int queueCapacity,
            @Value("${travel.planning.executor.await-termination-seconds:30}") int awaitTerminationSeconds) {
        if (poolSize <= 0 || queueCapacity <= 0 || awaitTerminationSeconds < 0) {
            throw new IllegalArgumentException("INVALID_PLANNING_EXECUTOR_CONFIGURATION");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("planning-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        return executor;
    }
}
