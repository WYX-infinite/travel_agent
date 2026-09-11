package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.persistence.TravelPlanRepository;
import com.wyx.travelagent.planning.task.PlanningTaskException;
import com.wyx.travelagent.planning.task.PlanningTaskService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TravelPlanQueryServiceTest {
    @Test
    void allRepositoryReadFailuresBecomeOneSafePlanStorageError() {
        TravelPlanRepository plans = mock(TravelPlanRepository.class);
        PlanningTaskService tasks = mock(PlanningTaskService.class);
        TravelPlanRepository.PlanRecord owned = mock(TravelPlanRepository.PlanRecord.class);
        when(plans.findAllByUserId(7)).thenThrow(new IllegalStateException("SQL password=/secret/path"));
        when(plans.findByIdAndUserId(3, 7)).thenReturn(Optional.of(owned));
        when(plans.findVersionsByPlanIdAndUserId(3, 7))
                .thenThrow(new IllegalStateException("corrupt JSON at C:/private"));
        TravelPlanQueryService service = new TravelPlanQueryService(plans, tasks);

        assertSafeStorageFailure(() -> service.list(7));
        assertSafeStorageFailure(() -> service.detail(3, 7));
        assertSafeStorageFailure(() -> service.versions(3, 7));
    }

    private static void assertSafeStorageFailure(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(PlanningTaskException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("PLAN_STORAGE_FAILED");
                    assertThat(exception.getMessage()).isEqualTo("plan storage failed");
                    assertThat(exception.getMessage()).doesNotContain("SQL", "password", "private");
                });
    }
}
