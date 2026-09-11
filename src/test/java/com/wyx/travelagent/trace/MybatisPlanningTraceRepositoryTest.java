package com.wyx.travelagent.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MybatisPlanningTraceRepositoryTest {
    @Test void findAfterUsesExclusiveAscendingCursorAndMapsPersistedRows() throws Exception {
        PlanningTraceMapper mapper = mock(PlanningTraceMapper.class);
        PlanningTraceRow row = row(18, 101, 27L, "VALIDATION", "DEGRADED",
                new ObjectMapper().findAndRegisterModules().writeValueAsString(PlanningTraceDetails.empty()));
        when(mapper.findAfter(101, 17, 25)).thenReturn(List.of(row));
        MybatisPlanningTraceRepository repository = new MybatisPlanningTraceRepository(
                mapper, new ObjectMapper());

        List<PersistedPlanningTrace> result = repository.findAfter(101, 17, 25);

        assertThat(result).hasSize(1);
        PersistedPlanningTrace persisted = result.getFirst();
        assertThat(persisted.eventId()).isEqualTo(18);
        assertThat(persisted.event().taskId()).isEqualTo(101);
        assertThat(persisted.event().planVersionId()).isEqualTo(27);
        assertThat(persisted.event().step()).isEqualTo(PlanningStep.VALIDATION);
        assertThat(persisted.event().status()).isEqualTo(TraceStatus.DEGRADED);
        assertThat(persisted.event().details()).isEqualTo(PlanningTraceDetails.empty());
        assertThat(persisted.event().createdAt()).isEqualTo(Instant.parse("2026-10-01T02:00:12Z"));
        verify(mapper).findAfter(101, 17, 25);

        Select select = PlanningTraceMapper.class
                .getDeclaredMethod("findAfter", long.class, long.class, int.class)
                .getAnnotation(Select.class);
        assertThat(String.join(" ", select.value()))
                .contains("task_id=#{taskId}", "id>#{afterId}", "ORDER BY id ASC", "LIMIT #{limit}");
    }

    @Test void findAfterRejectsInvalidBoundsBeforeDatabaseAccess() {
        PlanningTraceMapper mapper = mock(PlanningTraceMapper.class);
        MybatisPlanningTraceRepository repository = new MybatisPlanningTraceRepository(
                mapper, new ObjectMapper());

        assertThatThrownBy(() -> repository.findAfter(0, 0, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PLANNING_TRACE_PERSISTENCE_FAILED");
        assertThatThrownBy(() -> repository.findAfter(1, -1, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PLANNING_TRACE_PERSISTENCE_FAILED");
        assertThatThrownBy(() -> repository.findAfter(1, 0, 201))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PLANNING_TRACE_PERSISTENCE_FAILED");
        verifyNoInteractions(mapper);
    }

    @Test void findAfterFailsClosedWithoutLeakingMalformedStoredContent() {
        PlanningTraceMapper mapper = mock(PlanningTraceMapper.class);
        when(mapper.findAfter(101, 0, 10)).thenReturn(List.of(
                row(18, 101, null, "VALIDATION", "SUCCEEDED", "SQL password C:/private")));
        MybatisPlanningTraceRepository repository = new MybatisPlanningTraceRepository(
                mapper, new ObjectMapper());

        assertThatThrownBy(() -> repository.findAfter(101, 0, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PLANNING_TRACE_PERSISTENCE_FAILED")
                .hasMessageNotContaining("SQL")
                .hasMessageNotContaining("password")
                .hasMessageNotContaining("private");
    }

    private static PlanningTraceRow row(long id, long taskId, Long planVersionId,
                                        String step, String status, String detailsJson) {
        PlanningTraceRow row = new PlanningTraceRow();
        row.setId(id);
        row.setTaskId(taskId);
        row.setPlanVersionId(planVersionId);
        row.setStep(step);
        row.setStatus(status);
        row.setDurationMs(31L);
        row.setInputSummary(detailsJson);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 1, 2, 0, 12));
        return row;
    }
}
