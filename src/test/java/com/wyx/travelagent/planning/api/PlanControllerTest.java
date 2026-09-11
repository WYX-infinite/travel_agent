package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.auth.*;
import com.wyx.travelagent.common.api.GlobalExceptionHandler;
import com.wyx.travelagent.planning.memory.*;
import com.wyx.travelagent.planning.metrics.*;
import com.wyx.travelagent.planning.model.*;
import com.wyx.travelagent.planning.persistence.*;
import com.wyx.travelagent.planning.task.*;
import com.wyx.travelagent.planning.validation.*;
import com.wyx.travelagent.tools.model.TravelMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.Set;
import static org.hamcrest.Matchers.nullValue;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlanControllerTest {
    @AfterEach void clear() { CurrentUserContext.clear(); }

    @Test void listDetailVersionsAndReplanUseCurrentUser() throws Exception {
        TravelPlanQueryService service = mock(TravelPlanQueryService.class);
        when(service.list(7)).thenReturn(List.of()); when(service.versions(3, 7)).thenReturn(List.of());
        PlanningTask task = mock(PlanningTask.class); when(task.id()).thenReturn(8L);
        when(task.status()).thenReturn(PlanningTaskStatus.PENDING); when(task.currentStep()).thenReturn("PENDING");
        when(task.createdAt()).thenReturn(Instant.EPOCH); when(task.updatedAt()).thenReturn(Instant.EPOCH);
        when(service.replan(3, 7)).thenReturn(task);
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER)); MockMvc mvc = mvc(service);
        mvc.perform(get("/api/plans")).andExpect(status().isOk());
        mvc.perform(get("/api/plans/3/versions")).andExpect(status().isOk());
        mvc.perform(post("/api/plans/3/replan")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").value(8));
        verify(service).list(7); verify(service).versions(3, 7); verify(service).replan(3, 7);
    }

    @Test void crossUserAndMissingPlanShare404Contract() throws Exception {
        TravelPlanQueryService service = mock(TravelPlanQueryService.class);
        when(service.detail(3, 7)).thenThrow(new PlanningTaskException("PLAN_NOT_FOUND", "plan not found",
                org.springframework.http.HttpStatus.NOT_FOUND));
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));
        mvc(service).perform(get("/api/plans/3")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PLAN_NOT_FOUND"));
    }

    @Test void detailAndVersionsExposeOnlyFrozenApiDtos() throws Exception {
        TravelPlanQueryService service = mock(TravelPlanQueryService.class);
        Fixture fixture = fixture();
        PlanDetailResponse detail = PlanDetailResponse.from(fixture.plan(), fixture.version(), fixture.task());
        List<PlanVersionResponse> versions = List.of(PlanVersionResponse.from(fixture.version()));
        when(service.detail(3, 7)).thenReturn(detail);
        when(service.versions(3, 7)).thenReturn(versions);
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));
        MockMvc mvc = mvc(service);

        mvc.perform(get("/api/plans/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").isString())
                .andExpect(jsonPath("$.data.weather").hasJsonPath())
                .andExpect(jsonPath("$.data.weather").value(nullValue()))
                .andExpect(jsonPath("$.data.appliedPreferences.enabled").value(true))
                .andExpect(jsonPath("$.data.appliedPreferences.adoptedFields[0]").value("interests"))
                .andExpect(jsonPath("$.data.days[0].items[0].poiName").value("虚构博物馆"))
                .andExpect(jsonPath("$.data.days[0].items[0].address").hasJsonPath())
                .andExpect(jsonPath("$.data.days[0].items[0].address").value(nullValue()))
                .andExpect(jsonPath("$.data.days[0].items[0].activity").value("虚构说明"))
                .andExpect(jsonPath("$.data.days[0].items[0].cost.min").value(10))
                .andExpect(jsonPath("$.data.days[0].items[0].cost.max").value(20))
                .andExpect(jsonPath("$.data.days[0].items[0].cost.evidenceType").value("API_FACT"))
                .andExpect(jsonPath("$.data.days[0].items[0].cost.estimated").value(false))
                .andExpect(jsonPath("$.data.days[0].items[0].nextSegment").hasJsonPath())
                .andExpect(jsonPath("$.data.days[0].items[0].nextSegment").value(nullValue()))
                .andExpect(jsonPath("$.data.days[0].items[0].evidence").isEmpty())
                .andExpect(jsonPath("$.data.days[0].items[0].name").doesNotExist())
                .andExpect(jsonPath("$.data.days[0].items[0].description").doesNotExist())
                .andExpect(jsonPath("$.data.days[0].items[0].travelModeFromPrevious").doesNotExist())
                .andExpect(jsonPath("$.data.days[0].items[0].cost.minimum").doesNotExist())
                .andExpect(jsonPath("$.data.days[0].items[0].cost.maximum").doesNotExist())
                .andExpect(jsonPath("$.data.warnings[0].code").value("MISSING_ROUTE"))
                .andExpect(jsonPath("$.data.metrics.sourceCoverage.percentage").value(100.0))
                .andExpect(jsonPath("$.data.versions[0].versionNumber").value(1))
                .andExpect(jsonPath("$.data.itinerary").doesNotExist())
                .andExpect(jsonPath("$.data.issues").doesNotExist());
        mvc.perform(get("/api/plans/3/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].days[0].items[0].poiId").value("fake-poi-1"))
                .andExpect(jsonPath("$.data[0].warnings[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.data[0].itinerary").doesNotExist())
                .andExpect(jsonPath("$.data[0].issues").doesNotExist());
    }

    private static Fixture fixture() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        ItineraryItem item = new ItineraryItem(LocalTime.of(9, 0), LocalTime.of(10, 0),
                "fake-poi-1", "虚构博物馆", "虚构说明",
                CostEstimate.known(BigDecimal.TEN, BigDecimal.valueOf(20), CostEvidenceType.FACT, null),
                TravelMode.WALKING);
        ItineraryPlan itinerary = new ItineraryPlan("杭州", date, date,
                List.of(new ItineraryDay(date, List.of(item))), BigDecimal.valueOf(20));
        ValidationIssue warning = new ValidationIssue("MISSING_ROUTE", IssueSeverity.WARNING,
                date, 0, "fake-poi-1", "route fact is unavailable", null, null);
        PlanMetrics metrics = new PlanMetrics(new MetricRatio(1, 1), new MetricRatio(0, 0),
                new MetricRatio(1, 1), new MetricRatio(3, 4));
        TravelPlanRepository.PlanRecord plan = new TravelPlanRepository.PlanRecord(3, 7, "杭州",
                date, date, 1, TravelPlanStatus.COMPLETED, Instant.EPOCH, Instant.EPOCH);
        TravelPlanRepository.PlanVersion version = new TravelPlanRepository.PlanVersion(21, 3, 1,
                itinerary, new ValidationReport(List.of(warning)), metrics, Instant.EPOCH);
        PlanningTask task = mock(PlanningTask.class);
        when(task.appliedPreferences()).thenReturn(new AppliedPreferenceSnapshot(Set.of(TravelInterest.HISTORY),
                TravelPace.MODERATE, TravelMode.TRANSIT, null,
                PreferenceSource.SAVED_PREFERENCE, PreferenceSource.EXPLICIT_REQUEST,
                PreferenceSource.SAVED_PREFERENCE, PreferenceSource.NOT_PROVIDED,
                true, PreferenceReadStatus.SUCCEEDED));
        return new Fixture(plan, version, task);
    }

    private record Fixture(TravelPlanRepository.PlanRecord plan,
                           TravelPlanRepository.PlanVersion version, PlanningTask task) { }

    private static MockMvc mvc(TravelPlanQueryService service) {
        return MockMvcBuilders.standaloneSetup(new PlanController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
