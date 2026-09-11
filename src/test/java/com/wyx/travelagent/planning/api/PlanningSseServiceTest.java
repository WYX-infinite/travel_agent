package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.task.*;
import com.wyx.travelagent.trace.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PlanningSseServiceTest {
    @Test void replaysOnlyAfterLastEventIdInOrderAndDoesNotRepeatOnPoll() {
        PlanningTaskService tasks = mock(PlanningTaskService.class);
        PlanningTask running = task(PlanningTaskStatus.RUNNING);
        when(tasks.getOwned(4, 7)).thenReturn(running);
        RecordingTraceRepository repository = new RecordingTraceRepository(List.of(trace(1), trace(2), trace(3)));
        RecordingEmitter emitter = new RecordingEmitter();
        PlanningSseService service = new PlanningSseService(tasks,
                new PlanningTraceService(repository, Clock.systemUTC()), 1000, 5, ignored -> emitter);

        service.subscribe(4, 7, 1);
        service.poll();

        assertThat(emitter.sendCount).isEqualTo(2);
        assertThat(repository.cursors).containsExactly(1L, 3L);
        verify(tasks, atLeastOnce()).getOwned(4, 7);
    }

    @Test void terminalTaskCompletesEmitterAndNoPerConnectionExecutorExists() {
        PlanningTaskService tasks = mock(PlanningTaskService.class);
        PlanningTask completed = task(PlanningTaskStatus.COMPLETED);
        when(tasks.getOwned(4, 7)).thenReturn(completed);
        RecordingEmitter emitter = new RecordingEmitter();
        PlanningSseService service = new PlanningSseService(tasks,
                new PlanningTraceService(new RecordingTraceRepository(List.of(trace(1))), Clock.systemUTC()),
                1000, 5, ignored -> emitter);

        service.subscribe(4, 7, 0);

        assertThat(emitter.sendCount).isEqualTo(2);
        assertThat(emitter.completed).isTrue();
        assertThat(Arrays.stream(PlanningSseService.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getType)
                .noneMatch(java.util.concurrent.Executor.class::isAssignableFrom)).isTrue();
    }

    @Test void projectsTraceIntoFrozenPublicStatusesMessagesAndOccurredAtField() {
        PlanningTaskService tasks = mock(PlanningTaskService.class);
        PlanningTask running = task(PlanningTaskStatus.RUNNING);
        when(tasks.getOwned(4, 7)).thenReturn(running);
        RecordingEmitter emitter = new RecordingEmitter();
        List<PersistedPlanningTrace> traces = List.of(
                trace(1, PlanningStep.REQUIREMENT, TraceStatus.SUCCEEDED, null, null),
                trace(2, PlanningStep.COMPLETION, TraceStatus.SUCCEEDED, null, null),
                trace(3, PlanningStep.COMPLETION, TraceStatus.DEGRADED, null, null),
                trace(4, PlanningStep.COMPLETION, TraceStatus.FAILED, "SAFE_CODE", "SQL JWT C:/private"));
        PlanningSseService service = new PlanningSseService(tasks,
                new PlanningTraceService(new RecordingTraceRepository(traces), Clock.systemUTC()),
                1000, 5, ignored -> emitter);

        service.subscribe(4, 7, 0);

        assertThat(emitter.events).extracting(PlanningSseEvent::status)
                .containsExactly("RUNNING", "COMPLETED", "NEEDS_ADJUSTMENT", "FAILED");
        assertThat(emitter.events.get(3).message()).isEqualTo("planning failed");
        assertThat(emitter.events.get(3).message()).doesNotContain("SQL", "JWT", "private");
        assertThat(emitter.events.get(3).errorCode()).isEqualTo("SAFE_CODE");
        assertThat(Arrays.stream(PlanningSseEvent.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .contains("occurredAt").doesNotContain("createdAt");
    }

    @Test void serializesImmediateReplayAndScheduledPollForOneSubscription() throws Exception {
        PlanningTaskService tasks = mock(PlanningTaskService.class);
        PlanningTask running = task(PlanningTaskStatus.RUNNING);
        when(tasks.getOwned(4, 7)).thenReturn(running);
        BlockingFirstEmitter emitter = new BlockingFirstEmitter();
        PlanningSseService service = new PlanningSseService(tasks,
                new PlanningTraceService(new RecordingTraceRepository(List.of(trace(1))), Clock.systemUTC()),
                1000, 5, ignored -> emitter);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<SseEmitter> subscription = caller.submit(() -> service.subscribe(4, 7, 0));
            assertThat(emitter.firstSendEntered.await(2, TimeUnit.SECONDS)).isTrue();

            service.poll();
            emitter.releaseFirstSend.countDown();
            subscription.get(2, TimeUnit.SECONDS);

            assertThat(emitter.sendCount.get()).isEqualTo(1);
        } finally {
            emitter.releaseFirstSend.countDown();
            caller.shutdownNow();
        }
    }

    @Test void terminalTaskWithoutTraceSendsPersistedTaskStatusBeforeCompleting() {
        PlanningTaskService tasks = mock(PlanningTaskService.class);
        PlanningTask failed = task(PlanningTaskStatus.FAILED);
        when(failed.currentStep()).thenReturn("COMPLETION");
        when(failed.failureCode()).thenReturn("SERVER_RESTARTED");
        when(failed.failureReason()).thenReturn("planning stopped because the server restarted");
        when(failed.updatedAt()).thenReturn(Instant.parse("2026-10-01T02:00:00Z"));
        when(tasks.getOwned(4, 7)).thenReturn(failed);
        RecordingEmitter emitter = new RecordingEmitter();
        PlanningSseService service = new PlanningSseService(tasks,
                new PlanningTraceService(new RecordingTraceRepository(List.of()), Clock.systemUTC()),
                1000, 5, ignored -> emitter);

        service.subscribe(4, 7, 0);

        assertThat(emitter.taskEvents).singleElement().satisfies(event -> {
            assertThat(event.status()).isEqualTo("FAILED");
            assertThat(event.errorCode()).isEqualTo("SERVER_RESTARTED");
            assertThat(event.errorMessage()).isEqualTo("planning stopped because the server restarted");
        });
        assertThat(emitter.completed).isTrue();
    }

    @Test void ownershipFailureDoesNotAllocateAnEmitter() {
        PlanningTaskService tasks = mock(PlanningTaskService.class);
        when(tasks.getOwned(4, 8)).thenThrow(new PlanningTaskException(
                "PLANNING_TASK_NOT_FOUND", "planning task not found", org.springframework.http.HttpStatus.NOT_FOUND));
        AtomicInteger allocations = new AtomicInteger();
        PlanningSseService service = new PlanningSseService(tasks,
                new PlanningTraceService(new RecordingTraceRepository(List.of()), Clock.systemUTC()),
                1000, 5, ignored -> { allocations.incrementAndGet(); return new RecordingEmitter(); });

        assertThatThrownBy(() -> service.subscribe(4, 8, 0))
                .isInstanceOf(PlanningTaskException.class);
        assertThat(allocations).hasValue(0);
    }

    @Test void reconnectUsesPersistedCursorAndDoesNotReplayAcknowledgedEvents() {
        PlanningTaskService tasks = mock(PlanningTaskService.class);
        PlanningTask running = task(PlanningTaskStatus.RUNNING);
        when(tasks.getOwned(4, 7)).thenReturn(running);
        RecordingTraceRepository repository = new RecordingTraceRepository(List.of(trace(1), trace(2), trace(3)));
        RecordingEmitter first = new RecordingEmitter();
        RecordingEmitter second = new RecordingEmitter();
        AtomicInteger allocation = new AtomicInteger();
        PlanningSseService service = new PlanningSseService(tasks,
                new PlanningTraceService(repository, Clock.systemUTC()), 1000, 5,
                ignored -> allocation.getAndIncrement() == 0 ? first : second);

        service.subscribe(4, 7, 1);
        service.subscribe(4, 7, 3);

        assertThat(first.events).extracting(PlanningSseEvent::eventId).containsExactly(2L, 3L);
        assertThat(second.events).isEmpty();
        assertThat(repository.cursors).containsExactly(1L, 3L);
    }

    @Test void timeoutCleansConnectionSlotAndDoesNotChangeTaskState() {
        PlanningTaskService tasks = mock(PlanningTaskService.class);
        PlanningTask running = task(PlanningTaskStatus.RUNNING);
        when(tasks.getOwned(4, 7)).thenReturn(running);
        LifecycleEmitter first = new LifecycleEmitter();
        RecordingEmitter second = new RecordingEmitter();
        AtomicInteger allocation = new AtomicInteger();
        PlanningSseService service = new PlanningSseService(tasks,
                new PlanningTraceService(new RecordingTraceRepository(List.of()), Clock.systemUTC()),
                1000, 1, ignored -> allocation.getAndIncrement() == 0 ? first : second);

        service.subscribe(4, 7, 0);
        first.fireTimeout();
        service.subscribe(4, 7, 0);

        assertThat(allocation).hasValue(2);
        assertThat(running.status()).isEqualTo(PlanningTaskStatus.RUNNING);
    }

    @Test void sendFailureCleansConnectionSlotForNextSubscriber() {
        PlanningTaskService tasks = mock(PlanningTaskService.class);
        PlanningTask running = task(PlanningTaskStatus.RUNNING);
        when(tasks.getOwned(4, 7)).thenReturn(running);
        FailingEmitter first = new FailingEmitter();
        RecordingEmitter second = new RecordingEmitter();
        AtomicInteger allocation = new AtomicInteger();
        PlanningSseService service = new PlanningSseService(tasks,
                new PlanningTraceService(new RecordingTraceRepository(List.of(trace(1))), Clock.systemUTC()),
                1000, 1, ignored -> allocation.getAndIncrement() == 0 ? first : second);

        service.subscribe(4, 7, 0);
        service.subscribe(4, 7, 1);

        assertThat(allocation).hasValue(2);
    }

    private static PlanningTask task(PlanningTaskStatus status) {
        PlanningTask task = mock(PlanningTask.class); when(task.status()).thenReturn(status); return task;
    }

    private static PersistedPlanningTrace trace(long id) {
        return trace(id, PlanningStep.REQUIREMENT, TraceStatus.SUCCEEDED, null, null);
    }

    private static PersistedPlanningTrace trace(long id, PlanningStep step, TraceStatus status,
                                                 String errorCode, String errorMessage) {
        return new PersistedPlanningTrace(id, new PlanningTraceEvent(4, null, step,
                status, 1, PlanningTraceDetails.empty(), errorCode, errorMessage, Instant.EPOCH));
    }

    private static final class RecordingTraceRepository implements PlanningTraceRepository {
        private final List<PersistedPlanningTrace> events; private final List<Long> cursors = new ArrayList<>();
        private RecordingTraceRepository(List<PersistedPlanningTrace> events) { this.events = events; }
        @Override public void append(PlanningTraceEvent event) { }
        @Override public List<PersistedPlanningTrace> findAfter(long taskId, long afterEventId, int limit) {
            cursors.add(afterEventId); return events.stream().filter(event -> event.eventId() > afterEventId).limit(limit).toList();
        }
    }

    private static class RecordingEmitter extends SseEmitter {
        private int sendCount; private boolean completed;
        private final List<PlanningSseEvent> events = new ArrayList<>();
        private final List<PlanningTaskSseEvent> taskEvents = new ArrayList<>();
        private RecordingEmitter() { super(1000L); }
        @Override public synchronized void send(SseEventBuilder builder) throws IOException {
            sendCount++;
            builder.build().stream().map(org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType::getData)
                    .filter(PlanningSseEvent.class::isInstance).map(PlanningSseEvent.class::cast).forEach(events::add);
            builder.build().stream().map(org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType::getData)
                    .filter(PlanningTaskSseEvent.class::isInstance).map(PlanningTaskSseEvent.class::cast).forEach(taskEvents::add);
        }
        @Override public synchronized void complete() { completed = true; }
    }

    private static final class BlockingFirstEmitter extends SseEmitter {
        private final AtomicInteger sendCount = new AtomicInteger();
        private final CountDownLatch firstSendEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFirstSend = new CountDownLatch(1);
        private BlockingFirstEmitter() { super(1000L); }
        @Override public void send(SseEventBuilder builder) throws IOException {
            if (sendCount.incrementAndGet() == 1) {
                firstSendEntered.countDown();
                try {
                    if (!releaseFirstSend.await(2, TimeUnit.SECONDS)) throw new IOException("test timeout");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("test interrupted", exception);
                }
            }
        }
    }

    private static final class LifecycleEmitter extends RecordingEmitter {
        private Runnable timeout;
        @Override public void onTimeout(Runnable callback) { timeout = callback; }
        private void fireTimeout() { timeout.run(); }
    }

    private static final class FailingEmitter extends SseEmitter {
        private FailingEmitter() { super(1000L); }
        @Override public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("client path C:/private");
        }
    }
}
