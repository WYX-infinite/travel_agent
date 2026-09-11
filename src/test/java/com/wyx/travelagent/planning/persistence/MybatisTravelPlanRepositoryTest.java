package com.wyx.travelagent.planning.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.planning.metrics.MetricRatio;
import com.wyx.travelagent.planning.metrics.PlanMetrics;
import com.wyx.travelagent.planning.model.ItineraryDay;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.validation.ValidationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisTravelPlanRepositoryTest {
    private TravelPlanMapper plans;
    private TravelPlanVersionMapper versions;
    private PlatformTransactionManager transactions;
    private TransactionStatus transactionStatus;
    private MybatisTravelPlanRepository repository;

    @BeforeEach
    void setUp() {
        plans = mock(TravelPlanMapper.class);
        versions = mock(TravelPlanVersionMapper.class);
        transactions = mock(PlatformTransactionManager.class);
        transactionStatus = mock(TransactionStatus.class);
        when(transactions.getTransaction(any())).thenReturn(transactionStatus);
        repository = new MybatisTravelPlanRepository(plans, versions,
                new TransactionTemplate(transactions), new ObjectMapper());
    }

    @Test
    void savingVersionInsertsSnapshotAndAdvancesParentBeforeTransactionCommit() {
        doAnswer(invocation -> {
            invocation.<TravelPlanVersionRow>getArgument(0).setId(501L);
            return 1;
        }).when(versions).insert(any());
        when(plans.advanceVersion(42L, 1, TravelPlanStatus.COMPLETED.name())).thenReturn(1);

        TravelPlanRepository.SavedVersion saved = repository.saveVersion(42L, draft());

        assertThat(saved).isEqualTo(new TravelPlanRepository.SavedVersion(501L, 1));
        var order = inOrder(versions, plans, transactions);
        order.verify(versions).insert(any());
        order.verify(plans).advanceVersion(42L, 1, TravelPlanStatus.COMPLETED.name());
        order.verify(transactions).commit(transactionStatus);
    }

    @Test
    void parentAdvanceFailureRollsBackVersionInsertAndReturnsStableError() {
        doAnswer(invocation -> {
            invocation.<TravelPlanVersionRow>getArgument(0).setId(501L);
            return 1;
        }).when(versions).insert(any());
        when(plans.advanceVersion(42L, 1, TravelPlanStatus.COMPLETED.name())).thenReturn(0);

        assertThatThrownBy(() -> repository.saveVersion(42L, draft()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PLAN_PERSISTENCE_FAILED");

        verify(transactions).rollback(transactionStatus);
    }

    private static TravelPlanRepository.VersionDraft draft() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        ItineraryPlan plan = new ItineraryPlan("杭州", date, date,
                List.of(new ItineraryDay(date, List.of())), BigDecimal.ZERO);
        PlanMetrics metrics = new PlanMetrics(new MetricRatio(0, 0),
                new MetricRatio(0, 0), new MetricRatio(0, 0), new MetricRatio(8, 8));
        return new TravelPlanRepository.VersionDraft(1, plan,
                new ValidationReport(List.of()), metrics, TravelPlanStatus.COMPLETED);
    }
}
