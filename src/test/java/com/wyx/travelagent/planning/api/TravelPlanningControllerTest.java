package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.auth.*;
import com.wyx.travelagent.common.api.GlobalExceptionHandler;
import com.wyx.travelagent.planning.model.TravelRequirementInput;
import com.wyx.travelagent.planning.task.*;
import com.wyx.travelagent.planning.validation.RequirementValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TravelPlanningControllerTest {
    @AfterEach void clear() { CurrentUserContext.clear(); }

    @Test void postReturns202AndPreservesDefaultTrueAndExplicitFalse() throws Exception {
        PlanningTaskService service = mock(PlanningTaskService.class);
        PlanningTask firstTask = task(1, PlanningTaskStatus.PENDING);
        when(service.create(eq(7), any())).thenReturn(firstTask);
        MockMvc mvc = mvc(service);
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));

        mvc.perform(post("/api/planning-tasks").contentType(MediaType.APPLICATION_JSON).content("""
                {"destinationCity":"杭州","startDate":"2026-10-01","endDate":"2026-10-02",
                 "travelers":1,"totalBudget":1000,"companionType":"SOLO","interests":[]}
                """)).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PENDING"));
        ArgumentCaptor<TravelRequirementInput> input = ArgumentCaptor.forClass(TravelRequirementInput.class);
        verify(service).create(eq(7), input.capture());
        assertThat(input.getValue().useSavedPreferences()).isNull();
        assertThat(input.getValue().shouldUseSavedPreferences()).isTrue();
        assertThat(input.getValue().interests().state()).isEqualTo(com.wyx.travelagent.planning.model.PreferenceValue.State.EXPLICIT_EMPTY);

        reset(service); PlanningTask secondTask = task(2, PlanningTaskStatus.PENDING);
        when(service.create(eq(7), any())).thenReturn(secondTask);
        mvc.perform(post("/api/planning-tasks").contentType(MediaType.APPLICATION_JSON).content("""
                {"destinationCity":"杭州","startDate":"2026-10-01","endDate":"2026-10-02",
                 "travelers":1,"totalBudget":1000,"companionType":"SOLO","useSavedPreferences":false}
                """)).andExpect(status().isAccepted());
        verify(service).create(eq(7), input.capture());
        assertThat(input.getValue().useSavedPreferences()).isFalse();
    }

    @Test void getUsesCurrentUserAndCrossUserIsIndistinguishable404() throws Exception {
        PlanningTaskService service = mock(PlanningTaskService.class);
        when(service.getOwned(9, 7)).thenThrow(new PlanningTaskException(
                "PLANNING_TASK_NOT_FOUND", "planning task not found", org.springframework.http.HttpStatus.NOT_FOUND));
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));
        mvc(service).perform(get("/api/planning-tasks/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PLANNING_TASK_NOT_FOUND"));
        verify(service).getOwned(9, 7);
    }

    @Test void getReturnsFrozenStatusFieldNamesIncludingCompletedPlanAndSafeFailure() throws Exception {
        PlanningTaskService service = mock(PlanningTaskService.class);
        PlanningTask completed = task(10, PlanningTaskStatus.COMPLETED);
        when(completed.currentStep()).thenReturn("COMPLETION");
        when(completed.planId()).thenReturn(88L);
        PlanningTask failed = task(11, PlanningTaskStatus.FAILED);
        when(failed.currentStep()).thenReturn("VALIDATION");
        when(failed.failureCode()).thenReturn("PLANNING_TASK_EXECUTION_FAILED");
        when(failed.failureReason()).thenReturn("planning task execution failed");
        when(service.getOwned(10, 7)).thenReturn(completed);
        when(service.getOwned(11, 7)).thenReturn(failed);
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));
        MockMvc mvc = mvc(service);

        mvc.perform(get("/api/planning-tasks/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercent").value(100))
                .andExpect(jsonPath("$.data.planId").value(88))
                .andExpect(jsonPath("$.data.progress").doesNotExist())
                .andExpect(jsonPath("$.data.failureCode").doesNotExist());
        mvc.perform(get("/api/planning-tasks/11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorCode").value("PLANNING_TASK_EXECUTION_FAILED"))
                .andExpect(jsonPath("$.data.errorMessage").value("planning task execution failed"));
    }

    @Test void invalidLastEventIdReturnsStableFieldError() throws Exception {
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));
        mvc(mock(PlanningTaskService.class)).perform(get("/api/planning-tasks/1/events")
                        .header("Last-Event-ID", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields['Last-Event-ID']").exists());
    }

    @Test void invalidCreationReturnsFieldErrorsAndDoesNotStartASecondCall() throws Exception {
        PlanningTaskService service = mock(PlanningTaskService.class);
        RequirementValidator.ValidationException invalid;
        try {
            new RequirementValidator().validate(new TravelPlanningRequest("杭州", null, null,
                    0, java.math.BigDecimal.ZERO, "UNKNOWN", null, null,
                    null, null, null, true).toInput());
            throw new AssertionError("expected validation failure");
        } catch (RequirementValidator.ValidationException exception) {
            invalid = exception;
        }
        when(service.create(eq(7), any())).thenThrow(invalid);
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));

        mvc(service).perform(post("/api/planning-tasks").contentType(MediaType.APPLICATION_JSON).content("""
                {"destinationCity":"杭州","travelers":0,"totalBudget":0,"companionType":"UNKNOWN"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.travelers").exists())
                .andExpect(jsonPath("$.error.fields.startDate").exists());
        verify(service, times(1)).create(eq(7), any());
    }

    @Test void activeTaskAndFullQueueUseStableHttpErrors() throws Exception {
        PlanningTaskService service = mock(PlanningTaskService.class);
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));
        MockMvc mvc = mvc(service);
        String valid = """
                {"destinationCity":"杭州","startDate":"2026-10-01","endDate":"2026-10-01",
                 "travelers":1,"totalBudget":1000,"companionType":"SOLO"}
                """;
        when(service.create(eq(7), any())).thenThrow(new PlanningTaskException(
                "PLANNING_TASK_ALREADY_ACTIVE", "an active planning task already exists",
                org.springframework.http.HttpStatus.CONFLICT));
        mvc.perform(post("/api/planning-tasks").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PLANNING_TASK_ALREADY_ACTIVE"));

        reset(service);
        when(service.create(eq(7), any())).thenThrow(new PlanningTaskException(
                "PLANNING_QUEUE_FULL", "planning queue is full",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        mvc.perform(post("/api/planning-tasks").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("PLANNING_QUEUE_FULL"));
    }

    private static MockMvc mvc(PlanningTaskService service) {
        return MockMvcBuilders.standaloneSetup(new TravelPlanningController(service, mock(PlanningSseService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static PlanningTask task(long id, PlanningTaskStatus status) {
        PlanningTask task = mock(PlanningTask.class);
        when(task.id()).thenReturn(id); when(task.status()).thenReturn(status);
        when(task.currentStep()).thenReturn(status.name()); when(task.createdAt()).thenReturn(Instant.EPOCH);
        when(task.updatedAt()).thenReturn(Instant.EPOCH); return task;
    }
}
