package com.wyx.travelagent.planning.task;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanningTaskExecutorConfigurationTest {
    @Test
    void executorUsesFixedWorkersBoundedQueueAndAbortPolicy() {
        ThreadPoolTaskExecutor executor = new PlanningTaskExecutorConfiguration()
                .planningTaskExecutor(2, 3, 20);
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(3);
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class)
                    .isNotInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("planning-task-");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void saturationRejectsThirdTaskWithoutRunningItOnCallerOrCancellingAcceptedWork() throws Exception {
        ThreadPoolTaskExecutor executor = new PlanningTaskExecutorConfiguration().planningTaskExecutor(1, 1, 5);
        CountDownLatch firstStarted = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        CountDownLatch firstDone = new CountDownLatch(1); CountDownLatch secondDone = new CountDownLatch(1);
        try {
            executor.execute(() -> { firstStarted.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } firstDone.countDown(); });
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            executor.execute(secondDone::countDown);
            assertThatThrownBy(() -> executor.execute(() -> { throw new AssertionError("must not run"); }))
                    .isInstanceOf(RejectedExecutionException.class);
            release.countDown();
            assertThat(firstDone.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(secondDone.await(2, TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); executor.shutdown(); }
    }
}
