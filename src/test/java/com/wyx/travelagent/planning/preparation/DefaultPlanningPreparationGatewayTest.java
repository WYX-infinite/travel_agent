package com.wyx.travelagent.planning.preparation;

import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalResult;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalService;
import com.wyx.travelagent.planning.model.CompanionType;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.tools.dispatch.ToolAction;
import com.wyx.travelagent.tools.dispatch.ToolExecutionBundle;
import com.wyx.travelagent.tools.dispatch.ToolExecutionPolicy;
import com.wyx.travelagent.tools.dispatch.ToolPlan;
import com.wyx.travelagent.tools.dispatch.TravelToolDispatcher;
import com.wyx.travelagent.tools.dispatch.TravelToolType;
import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.ToolResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultPlanningPreparationGatewayTest {

    @Test
    void weatherFailureKeepsPoiFactsAndRecordsOnlyCoordinateSystems() {
        TravelToolDispatcher dispatcher = mock(TravelToolDispatcher.class);
        KnowledgeRetrievalService knowledge = mock(KnowledgeRetrievalService.class);
        ToolExecutionPolicy policy = mock(ToolExecutionPolicy.class);
        ToolPlan plan = new ToolPlan("杭州", List.of(
                new ToolAction("searchPoi", Map.of()),
                new ToolAction("weatherForecast", Map.of())));
        ToolExecutionBundle.Execution weatherFailure = new ToolExecutionBundle.Execution(
                TravelToolType.WEATHER_FORECAST,
                ToolResult.failure("weather", now(), "WEATHER_DOWN", "safe"),
                false, CoordinateSystem.GCJ02, CoordinateSystem.WGS84);
        when(knowledge.retrieve("杭州旅行", "杭州"))
                .thenReturn(new KnowledgeRetrievalResult(List.of(), List.of()));
        when(dispatcher.execute(policy, plan)).thenReturn(new ToolExecutionBundle(List.of(
                new ToolExecutionBundle.Execution(TravelToolType.SEARCH_POI,
                        ToolResult.success("place", now(), List.of(place())), false),
                weatherFailure), Map.of("place", 1, "weather", 1)));

        PlanningPreparationResult result = new DefaultPlanningPreparationGateway(
                knowledge, dispatcher).prepare(requirement(), "杭州旅行", policy, plan);

        assertThat(result.facts().places()).hasSize(1);
        assertThat(result.facts().weather()).isNull();
        assertThat(result.toolSummary().weatherAvailable()).isFalse();
        assertThat(result.toolSummary().weatherCoordinates()).containsExactly(
                new PlanningPreparationResult.WeatherCoordinateSummary(
                        CoordinateSystem.GCJ02, CoordinateSystem.WGS84));
        assertThat(result.toolSummary().toString()).doesNotContain("120.1", "30.2");
    }

    @Test
    void poiFailureUsesStableErrorAndDoesNotInventPlaces() {
        TravelToolDispatcher dispatcher = mock(TravelToolDispatcher.class);
        KnowledgeRetrievalService knowledge = mock(KnowledgeRetrievalService.class);
        ToolExecutionPolicy policy = mock(ToolExecutionPolicy.class);
        ToolPlan plan = new ToolPlan("杭州", List.of(new ToolAction("searchPoi", Map.of())));
        when(knowledge.retrieve("杭州旅行", "杭州"))
                .thenReturn(new KnowledgeRetrievalResult(List.of(), List.of()));
        when(dispatcher.execute(policy, plan)).thenReturn(new ToolExecutionBundle(List.of(
                new ToolExecutionBundle.Execution(TravelToolType.SEARCH_POI,
                        ToolResult.failure("place", now(), "PLACE_DOWN", "safe"), false)),
                Map.of("place", 1)));

        assertThatThrownBy(() -> new DefaultPlanningPreparationGateway(
                knowledge, dispatcher).prepare(requirement(), "杭州旅行", policy, plan))
                .isInstanceOf(PlanningPreparationException.class)
                .hasMessage("POI_DATA_UNAVAILABLE");
    }

    private static TravelRequirement requirement() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        return new TravelRequirement("杭州", date, date, 1, BigDecimal.valueOf(1000),
                CompanionType.SOLO, Set.of(), null, null, null, null);
    }

    private static PlaceCandidate place() {
        return new PlaceCandidate("poi-1", "西湖", "杭州",
                new GeoPoint(120.1, 30.2, CoordinateSystem.GCJ02));
    }

    private static LocalDateTime now() {
        return LocalDateTime.of(2026, 9, 11, 12, 0);
    }
}
