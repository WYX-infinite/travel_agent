package com.wyx.travelagent.trace;

import com.wyx.travelagent.planning.context.ContextBudgetReport;
import com.wyx.travelagent.planning.generation.ModelTokenUsage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanningTraceServiceTest {

    @Test
    void unknownProviderUsageStaysNullAndIsNotReplacedByJavaEstimate() {
        ContextBudgetReport report = new ContextBudgetReport(
                321, 100, 80, 40, 60, 20, 10, 11,
                3, 2, 2, 1, List.of("pace"), List.of("OPTIONAL_TRIMMED"), true);

        PlanningTraceDetails details = PlanningTraceDetails.model(
                report, ModelTokenUsage.unknown(), List.of("interests"), 1, 2, 3);

        assertThat(details.estimatedInputTokens()).isEqualTo(321);
        assertThat(details.actualInputTokens()).isNull();
        assertThat(details.actualOutputTokens()).isNull();
        assertThat(details.contextSectionTokens()).containsEntry("issuesAndPreviousPlan", 40);
    }

    @Test
    void failedTraceAcceptsOnlyStableCodeAndFixedSafeMessage() {
        List<PlanningTraceEvent> events = new ArrayList<>();
        PlanningTraceService service = new PlanningTraceService(events::add,
                Clock.fixed(Instant.parse("2026-09-11T03:00:00Z"), ZoneOffset.UTC));

        service.failed(77L, null, PlanningStep.GENERATION, 12L,
                "MODEL_UNAVAILABLE");

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.errorCode()).isEqualTo("MODEL_UNAVAILABLE");
            assertThat(event.errorMessage()).isEqualTo("itinerary generation failed");
            assertThat(event.toString()).doesNotContain("apiKey", "RAW_MODEL_RESPONSE", "[SYSTEM]");
        });
        assertThatThrownBy(() -> service.failed(77L, null, PlanningStep.GENERATION, 1L,
                "user message with spaces"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_TRACE_ERROR_CODE");
        assertThatThrownBy(() -> new PlanningTraceDetails(null, null, null,
                java.util.Map.of("prompt", 1), List.of(), List.of(), 0, 0, 0,
                java.util.Map.of(), List.of(), 0, 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_TRACE_MAP");
    }
}
