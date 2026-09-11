package com.wyx.travelagent.planning.task;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class PlanningTaskRecoveryTest {
    @Test
    void startupRecoveryFailsOnlyRunningTasksAndIsIdempotent() {
        RecordingRepository repository = new RecordingRepository();
        PlanningTaskRecovery recovery = new PlanningTaskRecovery(repository,
                Clock.fixed(Instant.parse("2026-10-01T02:00:00Z"), ZoneOffset.UTC));

        assertThat(recovery.recover()).isEqualTo(2);
        assertThat(recovery.recover()).isZero();
        assertThat(repository.code).isEqualTo("SERVER_RESTARTED");
        assertThat(repository.reason).isEqualTo("planning stopped because the server restarted");
    }

    @Test
    void applicationReadyDoesNotAbortStartupWhenStorageIsTemporarilyUnavailable() {
        PlanningTaskRepository repository = new RecordingRepository() {
            @Override
            public int failRunningTasks(Instant updatedAt, String code, String reason) {
                throw PlanningTaskException.storageFailure();
            }
        };
        PlanningTaskRecovery recovery = new PlanningTaskRecovery(repository,
                Clock.fixed(Instant.parse("2026-10-01T02:00:00Z"), ZoneOffset.UTC));

        assertThatCode(() -> recovery.onApplicationReady(mock(org.springframework.boot.context.event.ApplicationReadyEvent.class)))
                .doesNotThrowAnyException();
    }

    private static class RecordingRepository implements PlanningTaskRepository {
        int running = 2; String code; String reason;
        @Override public Optional<PlanningTask> createPendingIfNoActive(PendingTask draft) { return Optional.empty(); }
        @Override public Optional<PlanningTask> findOwned(long taskId, int userId) { return Optional.empty(); }
        @Override public Optional<PlanningTask> findLatestByPlanAndUser(long planId, int userId) { return Optional.empty(); }
        @Override public boolean markRunning(long taskId, Instant updatedAt) { return false; }
        @Override public boolean updateStep(long taskId, String step, Instant updatedAt) { return false; }
        @Override public boolean finish(long taskId, PlanningTaskStatus status, Long planId, String code, String reason, Instant updatedAt) { return false; }
        @Override public boolean deletePending(long taskId) { return false; }
        @Override public int failRunningTasks(Instant updatedAt, String code, String reason) {
            int result = running; running = 0; this.code = code;
            this.reason = reason; return result;
        }
    }
}
