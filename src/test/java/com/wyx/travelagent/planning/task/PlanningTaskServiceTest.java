package com.wyx.travelagent.planning.task;

import com.wyx.travelagent.auth.CurrentUser;
import com.wyx.travelagent.auth.CurrentUserContext;
import com.wyx.travelagent.auth.Role;
import com.wyx.travelagent.planning.PlanningOutcome;
import com.wyx.travelagent.planning.PlanningOutcomeStatus;
import com.wyx.travelagent.planning.TravelPlanningCommand;
import com.wyx.travelagent.planning.TravelPlanningOrchestrator;
import com.wyx.travelagent.planning.memory.AppliedPreferenceSnapshot;
import com.wyx.travelagent.planning.memory.PreferenceMemoryService;
import com.wyx.travelagent.planning.memory.PreferenceMergeResult;
import com.wyx.travelagent.planning.memory.PreferenceReadStatus;
import com.wyx.travelagent.planning.memory.PreferenceSource;
import com.wyx.travelagent.planning.metrics.MetricRatio;
import com.wyx.travelagent.planning.metrics.PlanMetrics;
import com.wyx.travelagent.planning.model.CompanionType;
import com.wyx.travelagent.planning.model.ItineraryDay;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.PreferenceValue;
import com.wyx.travelagent.planning.model.ResolvedTravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirementInput;
import com.wyx.travelagent.planning.validation.RequirementValidator;
import com.wyx.travelagent.planning.validation.ValidationReport;
import com.wyx.travelagent.trace.PlanningStep;
import com.wyx.travelagent.trace.PlanningTraceRepository;
import com.wyx.travelagent.trace.PlanningTraceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class PlanningTaskServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-01T02:00:00Z");

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void freezesPreferencesBeforeSubmissionAndAsyncExecutionUsesExplicitUserId() {
        FakeRepository repository = new FakeRepository();
        CapturingExecutor executor = new CapturingExecutor();
        TravelPlanningOrchestrator orchestrator = mock(TravelPlanningOrchestrator.class);
        when(orchestrator.plan(any(), any())).thenReturn(outcome(PlanningOutcomeStatus.COMPLETED, 91L, null));
        PreferenceMemoryService memory = mock(PreferenceMemoryService.class);
        PreferenceMergeResult merged = merged(true);
        when(memory.merge(7, request(null))).thenReturn(merged);
        PlanningTaskService service = service(repository, memory, orchestrator, executor);

        PlanningTask created = service.create(7, request(null));
        CurrentUserContext.set(new CurrentUser(999, "wrong-thread-user", Role.USER));
        executor.runNext();

        assertThat(created.status()).isEqualTo(PlanningTaskStatus.PENDING);
        assertThat(repository.task.resolvedRequirement()).isEqualTo(merged.mergedInput() == null
                ? null : mergedRequirement(merged));
        assertThat(repository.task.appliedPreferences()).isSameAs(merged.snapshot());
        ArgumentCaptor<TravelPlanningCommand> command = ArgumentCaptor.forClass(TravelPlanningCommand.class);
        verify(orchestrator).plan(command.capture(), any());
        assertThat(command.getValue().userId()).isEqualTo(7);
        assertThat(command.getValue().resolvedRequirement()).isEqualTo(mergedRequirement(merged));
        assertThat(repository.task.status()).isEqualTo(PlanningTaskStatus.COMPLETED);
        assertThat(repository.task.planId()).isEqualTo(91L);
    }

    @Test
    void nullPreferenceFlagDefaultsTrueButExplicitFalseIsPreserved() {
        FakeRepository repository = new FakeRepository();
        CapturingExecutor executor = new CapturingExecutor();
        PreferenceMemoryService memory = mock(PreferenceMemoryService.class);
        TravelPlanningOrchestrator orchestrator = mock(TravelPlanningOrchestrator.class);
        when(memory.merge(7, request(null))).thenReturn(merged(true));
        PlanningTaskService service = service(repository, memory, orchestrator, executor);
        service.create(7, request(null));
        assertThat(repository.task.originalRequest().useSavedPreferences()).isNull();
        assertThat(repository.task.originalRequest().shouldUseSavedPreferences()).isTrue();

        FakeRepository secondRepository = new FakeRepository();
        when(memory.merge(8, request(false))).thenReturn(merged(false));
        service = service(secondRepository, memory, orchestrator, executor);
        service.create(8, request(false));
        assertThat(secondRepository.task.originalRequest().useSavedPreferences()).isFalse();
    }

    @Test
    void outcomeStatusesMapDeterministicallyAndCompletedRequiresPlanId() {
        assertThat(PlanningTaskStatus.fromOutcome(outcome(PlanningOutcomeStatus.COMPLETED, 91L, null)))
                .isEqualTo(PlanningTaskStatus.COMPLETED);
        assertThat(PlanningTaskStatus.fromOutcome(outcome(PlanningOutcomeStatus.NEEDS_ADJUSTMENT, 92L, null)))
                .isEqualTo(PlanningTaskStatus.NEEDS_ADJUSTMENT);
        assertThat(PlanningTaskStatus.fromOutcome(outcome(
                PlanningOutcomeStatus.CONTEXT_BUDGET_EXCEEDED, null, "CONTEXT_BUDGET_EXCEEDED")))
                .isEqualTo(PlanningTaskStatus.FAILED);
        assertThat(PlanningTaskStatus.fromOutcome(outcome(
                PlanningOutcomeStatus.FAILED, null, "MODEL_UNAVAILABLE")))
                .isEqualTo(PlanningTaskStatus.FAILED);
        assertThatThrownBy(() -> new PlanningTask(1, 7, request(false), resolved(false), snapshot(false),
                PlanningStep.COMPLETION.name(), PlanningTaskStatus.COMPLETED, null, null, null, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminalStatesCannotReturnToRunningAndUnknownStoredStatusIsRejected() {
        assertThat(PlanningTaskStatus.PENDING.canTransitionTo(PlanningTaskStatus.FAILED)).isFalse();
        assertThat(PlanningTaskStatus.COMPLETED.canTransitionTo(PlanningTaskStatus.RUNNING)).isFalse();
        assertThat(PlanningTaskStatus.NEEDS_ADJUSTMENT.canTransitionTo(PlanningTaskStatus.RUNNING)).isFalse();
        assertThat(PlanningTaskStatus.FAILED.canTransitionTo(PlanningTaskStatus.RUNNING)).isFalse();
        assertThatThrownBy(() -> PlanningTaskStatus.fromStoredValue("FUTURE_DONE"))
                .isInstanceOf(PlanningTaskException.class)
                .extracting("code").isEqualTo("PLANNING_TASK_STORAGE_FAILED");
    }

    @Test
    void concurrentCreatesForOneUserProduceExactlyOneActiveTask() throws Exception {
        FakeRepository repository = new FakeRepository();
        PreferenceMemoryService memory = mock(PreferenceMemoryService.class);
        when(memory.merge(any(), any())).thenReturn(merged(false));
        PlanningTaskService service = service(repository, memory,
                mock(TravelPlanningOrchestrator.class), new CapturingExecutor());
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < workers; index++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    service.create(7, request(false));
                    accepted.incrementAndGet();
                } catch (PlanningTaskException exception) {
                    if ("PLANNING_TASK_ALREADY_ACTIVE".equals(exception.code())) rejected.incrementAndGet();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }
        ready.await();
        start.countDown();
        for (Thread thread : threads) thread.join();

        assertThat(accepted).hasValue(1);
        assertThat(rejected).hasValue(workers - 1);
        assertThat(repository.createCount).isEqualTo(1);
    }

    @Test
    void queueRejectionRemovesPendingTaskAndReturnsStableCode() {
        FakeRepository repository = new FakeRepository();
        Executor rejecting = command -> { throw new RejectedExecutionException("executor internals"); };
        PreferenceMemoryService memory = mock(PreferenceMemoryService.class);
        when(memory.merge(7, request(false))).thenReturn(merged(false));

        assertThatThrownBy(() -> service(repository, memory,
                mock(TravelPlanningOrchestrator.class), rejecting).create(7, request(false)))
                .isInstanceOf(PlanningTaskException.class)
                .extracting("code").isEqualTo("PLANNING_QUEUE_FULL");
        assertThat(repository.task).isNull();
    }

    @Test
    void realBoundedExecutorSaturationRejectsAndCompensatesWithoutCancellingAcceptedWork() throws Exception {
        ThreadPoolTaskExecutor executor = new PlanningTaskExecutorConfiguration()
                .planningTaskExecutor(1, 1, 5);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch queuedWorkFinished = new CountDownLatch(1);
        FakeRepository repository = new FakeRepository();
        PreferenceMemoryService memory = mock(PreferenceMemoryService.class);
        when(memory.merge(7, request(false))).thenReturn(merged(false));
        try {
            executor.execute(() -> {
                workerStarted.countDown();
                try { releaseWorker.await(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            });
            assertThat(workerStarted.await(2, TimeUnit.SECONDS)).isTrue();
            executor.execute(queuedWorkFinished::countDown);

            assertThatThrownBy(() -> service(repository, memory,
                    mock(TravelPlanningOrchestrator.class), executor).create(7, request(false)))
                    .isInstanceOf(PlanningTaskException.class)
                    .extracting("code").isEqualTo("PLANNING_QUEUE_FULL");
            assertThat(repository.task).isNull();

            releaseWorker.countDown();
            assertThat(queuedWorkFinished.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseWorker.countDown();
            executor.shutdown();
        }
    }

    @Test
    void unexpectedExecutionExceptionMarksTaskFailedWithSanitizedReason() {
        FakeRepository repository = new FakeRepository();
        CapturingExecutor executor = new CapturingExecutor();
        PreferenceMemoryService memory = mock(PreferenceMemoryService.class);
        when(memory.merge(7, request(false))).thenReturn(merged(false));
        TravelPlanningOrchestrator orchestrator = mock(TravelPlanningOrchestrator.class);
        when(orchestrator.plan(any(), any())).thenThrow(new IllegalStateException("SQL C:\\secret JWT"));
        service(repository, memory, orchestrator, executor).create(7, request(false));

        executor.runNext();

        assertThat(repository.task.status()).isEqualTo(PlanningTaskStatus.FAILED);
        assertThat(repository.task.failureCode()).isEqualTo("PLANNING_TASK_EXECUTION_FAILED");
        assertThat(repository.task.failureReason()).isEqualTo("planning task execution failed");
        assertThat(repository.task.failureReason()).doesNotContain("SQL", "secret", "JWT");
    }

    private static PlanningTaskService service(FakeRepository repository,
                                               PreferenceMemoryService memory,
                                               TravelPlanningOrchestrator orchestrator,
                                               Executor executor) {
        PlanningTraceRepository traces = event -> { };
        return new PlanningTaskService(repository, memory, new RequirementValidator(), orchestrator,
                executor, new PlanningTraceService(traces, fixedClock()), fixedClock());
    }

    private static PreferenceMergeResult merged(boolean requested) {
        TravelRequirementInput input = request(false);
        return new PreferenceMergeResult(input, snapshot(requested));
    }

    private static AppliedPreferenceSnapshot snapshot(boolean requested) {
        return new AppliedPreferenceSnapshot(Set.of(), null, null, null,
                PreferenceSource.NOT_PROVIDED, PreferenceSource.NOT_PROVIDED,
                PreferenceSource.NOT_PROVIDED, PreferenceSource.NOT_PROVIDED,
                requested, requested ? PreferenceReadStatus.SUCCEEDED : PreferenceReadStatus.NOT_REQUESTED);
    }

    private static ResolvedTravelRequirement resolved(boolean requested) {
        return new ResolvedTravelRequirement(new RequirementValidator().validate(request(false)), snapshot(requested));
    }

    private static ResolvedTravelRequirement mergedRequirement(PreferenceMergeResult result) {
        return new ResolvedTravelRequirement(new RequirementValidator().validate(result.mergedInput()), result.snapshot());
    }

    private static TravelRequirementInput request(Boolean useSavedPreferences) {
        LocalDate date = LocalDate.of(2026, 10, 1);
        return new TravelRequirementInput("杭州", date, date, 1, BigDecimal.valueOf(1000),
                CompanionType.SOLO.name(), PreferenceValue.notProvided(), PreferenceValue.notProvided(),
                PreferenceValue.notProvided(), PreferenceValue.notProvided(), null, useSavedPreferences);
    }

    private static PlanningOutcome outcome(PlanningOutcomeStatus status, Long planId, String code) {
        if (planId == null) return new PlanningOutcome(status, null, 0, null, null, null, code);
        LocalDate date = LocalDate.of(2026, 10, 1);
        ItineraryPlan plan = new ItineraryPlan("杭州", date, date,
                List.of(new ItineraryDay(date, List.of())), BigDecimal.ZERO);
        PlanMetrics metrics = new PlanMetrics(new MetricRatio(0, 0), new MetricRatio(0, 0),
                new MetricRatio(0, 0), new MetricRatio(8, 8));
        return new PlanningOutcome(status, planId, 1, plan, new ValidationReport(List.of()), metrics, code);
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static final class CapturingExecutor implements Executor {
        private final List<Runnable> commands = new ArrayList<>();
        @Override public synchronized void execute(Runnable command) { commands.add(command); }
        void runNext() { commands.removeFirst().run(); }
    }

    private static final class FakeRepository implements PlanningTaskRepository {
        private PlanningTask task;
        private int createCount;

        @Override
        public synchronized Optional<PlanningTask> createPendingIfNoActive(PendingTask draft) {
            if (task != null && task.status().isActive()) return Optional.empty();
            createCount++;
            task = draft.toTask(createCount);
            return Optional.of(task);
        }

        @Override public synchronized Optional<PlanningTask> findOwned(long taskId, int userId) {
            return task != null && task.id() == taskId && task.userId() == userId
                    ? Optional.of(task) : Optional.empty();
        }
        @Override public synchronized Optional<PlanningTask> findLatestByPlanAndUser(long planId, int userId) {
            return task != null && java.util.Objects.equals(task.planId(), planId) && task.userId() == userId
                    ? Optional.of(task) : Optional.empty();
        }
        @Override public synchronized boolean markRunning(long taskId, Instant updatedAt) {
            if (task == null || task.status() != PlanningTaskStatus.PENDING) return false;
            task = task.withState(PlanningTaskStatus.RUNNING, PlanningStep.REQUIREMENT.name(), null, null, null, updatedAt);
            return true;
        }
        @Override public synchronized boolean updateStep(long taskId, String step, Instant updatedAt) {
            if (task == null || task.status() != PlanningTaskStatus.RUNNING) return false;
            task = task.withState(task.status(), step, task.planId(), task.failureCode(), task.failureReason(), updatedAt);
            return true;
        }
        @Override public synchronized boolean finish(long taskId, PlanningTaskStatus status, Long planId,
                                                      String code, String reason, Instant updatedAt) {
            if (task == null || task.status() != PlanningTaskStatus.RUNNING) return false;
            task = task.withState(status, PlanningStep.COMPLETION.name(), planId, code, reason, updatedAt);
            return true;
        }
        @Override public synchronized boolean deletePending(long taskId) {
            if (task != null && task.status() == PlanningTaskStatus.PENDING) { task = null; return true; }
            return false;
        }
        @Override public synchronized int failRunningTasks(Instant updatedAt, String code, String reason) { return 0; }
    }
}
