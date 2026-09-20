package com.wyx.travelagent.planning.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.planning.memory.*;
import com.wyx.travelagent.planning.model.*;
import com.wyx.travelagent.planning.validation.RequirementValidator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MybatisPlanningTaskRepositoryTest {
    @Test void productionRepositoryIsTheDefaultPlanningTaskRepository() {
        assertThat(MybatisPlanningTaskRepository.class).hasAnnotation(Primary.class);
    }

    @Test void locksUserChecksActiveThenInsertsInOneTransaction() {
        PlanningTaskMapper mapper = mock(PlanningTaskMapper.class);
        when(mapper.lockUser(7)).thenReturn(7); when(mapper.countActive(7)).thenReturn(0);
        when(mapper.insert(any())).thenAnswer(invocation -> { invocation.<PlanningTaskRow>getArgument(0).setId(9L); return 1; });
        MybatisPlanningTaskRepository repository = new MybatisPlanningTaskRepository(mapper, directTransactions(), new ObjectMapper());

        PlanningTask task = repository.createPendingIfNoActive(draft()).orElseThrow();

        assertThat(task.id()).isEqualTo(9); assertThat(task.status()).isEqualTo(PlanningTaskStatus.PENDING);
        var order = inOrder(mapper); order.verify(mapper).lockUser(7); order.verify(mapper).countActive(7); order.verify(mapper).insert(any());
    }

    @Test void invalidGeneratedIdRollsBackTheUserLockAndInsertTransaction() {
        PlanningTaskMapper mapper = mock(PlanningTaskMapper.class);
        when(mapper.lockUser(7)).thenReturn(7);
        when(mapper.countActive(7)).thenReturn(0);
        when(mapper.insert(any())).thenReturn(1);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        MybatisPlanningTaskRepository repository = new MybatisPlanningTaskRepository(
                mapper, new TransactionTemplate(manager), new ObjectMapper());

        assertThatThrownBy(() -> repository.createPendingIfNoActive(draft()))
                .isInstanceOf(PlanningTaskException.class)
                .extracting("code").isEqualTo("PLANNING_TASK_STORAGE_FAILED");

        verify(mapper).insert(any());
        verify(manager).rollback(status);
        verify(manager, never()).commit(status);
    }

    @Test void activeTaskPreventsInsertAndRequestCodecPreservesExplicitEmptyAndFalse() {
        PlanningTaskMapper mapper = mock(PlanningTaskMapper.class);
        when(mapper.lockUser(7)).thenReturn(7); when(mapper.countActive(7)).thenReturn(1);
        MybatisPlanningTaskRepository repository = new MybatisPlanningTaskRepository(mapper, directTransactions(), new ObjectMapper());
        assertThat(repository.createPendingIfNoActive(draft())).isEmpty(); verify(mapper, never()).insert(any());

        PlanningTaskPayloadCodec codec = new PlanningTaskPayloadCodec(new ObjectMapper());
        TravelRequirementInput restored = codec.decodeRequest(codec.encodeRequest(draft().originalRequest()));
        assertThat(restored.interests().state()).isEqualTo(PreferenceValue.State.EXPLICIT_EMPTY);
        assertThat(restored.useSavedPreferences()).isFalse();
        assertThat(codec.decodeResolved(codec.encodeResolved(draft().resolvedRequirement())))
                .isEqualTo(draft().resolvedRequirement());
        assertThat(codec.decodeSnapshot(codec.mapperWriteSnapshot(draft().appliedPreferences())))
                .isEqualTo(draft().appliedPreferences());
    }

    @Test void unknownStoredStatusFailsClosed() {
        PlanningTaskMapper mapper = mock(PlanningTaskMapper.class); PlanningTaskRow row = new PlanningTaskRow();
        row.setStatus("FUTURE_DONE"); when(mapper.findOwned(1, 7)).thenReturn(row);
        MybatisPlanningTaskRepository repository = new MybatisPlanningTaskRepository(mapper, directTransactions(), new ObjectMapper());
        assertThatThrownBy(() -> repository.findOwned(1, 7)).isInstanceOf(PlanningTaskException.class)
                .extracting("code").isEqualTo("PLANNING_TASK_STORAGE_FAILED");
    }

    @Test void serializedUserLockTransactionAllowsOnlyOneConcurrentInsert() throws Exception {
        PlanningTaskMapper mapper = mock(PlanningTaskMapper.class);
        AtomicBoolean active = new AtomicBoolean();
        AtomicLong ids = new AtomicLong(20);
        when(mapper.lockUser(7)).thenReturn(7);
        when(mapper.countActive(7)).thenAnswer(ignored -> active.get() ? 1 : 0);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            active.set(true);
            invocation.<PlanningTaskRow>getArgument(0).setId(ids.incrementAndGet());
            return 1;
        });
        Object userRowLock = new Object();
        TransactionTemplate serialized = mock(TransactionTemplate.class);
        when(serialized.execute(any())).thenAnswer(invocation -> {
            synchronized (userRowLock) {
                return invocation.<TransactionCallback<?>>getArgument(0)
                        .doInTransaction(mock(TransactionStatus.class));
            }
        });
        MybatisPlanningTaskRepository repository = new MybatisPlanningTaskRepository(
                mapper, serialized, new ObjectMapper());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Callable<Optional<PlanningTask>> create = () -> {
                ready.countDown();
                start.await();
                return repository.createPendingIfNoActive(draft());
            };
            Future<Optional<PlanningTask>> first = callers.submit(create);
            Future<Optional<PlanningTask>> second = callers.submit(create);
            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(2, TimeUnit.SECONDS), second.get(2, TimeUnit.SECONDS)))
                    .filteredOn(Optional::isPresent).hasSize(1);
            verify(mapper, times(1)).insert(any());
        } finally {
            start.countDown();
            callers.shutdownNow();
        }
    }

    @Test void conditionalStateWritesAndStorageFailuresStayClosedAndSanitized() {
        PlanningTaskMapper mapper = mock(PlanningTaskMapper.class);
        when(mapper.markRunning(9, Instant.EPOCH)).thenReturn(1, 0);
        when(mapper.updateStep(9, "GENERATION", Instant.EPOCH)).thenReturn(1, 0);
        MybatisPlanningTaskRepository repository = new MybatisPlanningTaskRepository(
                mapper, directTransactions(), new ObjectMapper());

        assertThat(repository.markRunning(9, Instant.EPOCH)).isTrue();
        assertThat(repository.markRunning(9, Instant.EPOCH)).isFalse();
        assertThat(repository.updateStep(9, "GENERATION", Instant.EPOCH)).isTrue();
        assertThat(repository.updateStep(9, "GENERATION", Instant.EPOCH)).isFalse();

        when(mapper.failRunning(any(), any(), any()))
                .thenThrow(new IllegalStateException("SQL password C:/private"));
        assertThatThrownBy(() -> repository.failRunningTasks(Instant.EPOCH,
                "SERVER_RESTARTED", "planning stopped because the server restarted"))
                .isInstanceOf(PlanningTaskException.class)
                .hasMessage("planning task storage failed")
                .hasMessageNotContaining("SQL")
                .hasMessageNotContaining("password")
                .hasMessageNotContaining("private");
    }

    @SuppressWarnings("unchecked")
    private static TransactionTemplate directTransactions() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        when(template.execute(any())).thenAnswer(invocation -> invocation.<TransactionCallback<?>>getArgument(0)
                .doInTransaction(mock(TransactionStatus.class)));
        return template;
    }

    private static PendingTask draft() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        TravelRequirementInput input = new TravelRequirementInput("杭州", date, date, 1, BigDecimal.TEN,
                "SOLO", PreferenceValue.explicitEmpty(), PreferenceValue.notProvided(),
                PreferenceValue.notProvided(), PreferenceValue.notProvided(), null, false);
        AppliedPreferenceSnapshot snapshot = new AppliedPreferenceSnapshot(Set.of(), null, null, null,
                PreferenceSource.EXPLICIT_REQUEST, PreferenceSource.NOT_PROVIDED, PreferenceSource.NOT_PROVIDED,
                PreferenceSource.NOT_PROVIDED, false, PreferenceReadStatus.NOT_REQUESTED);
        ResolvedTravelRequirement resolved = new ResolvedTravelRequirement(new RequirementValidator().validate(input), snapshot);
        return new PendingTask(7, input, resolved, snapshot, Instant.EPOCH);
    }
}
