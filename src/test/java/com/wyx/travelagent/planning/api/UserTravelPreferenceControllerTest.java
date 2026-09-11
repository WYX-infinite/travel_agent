package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.auth.*;
import com.wyx.travelagent.common.api.GlobalExceptionHandler;
import com.wyx.travelagent.planning.memory.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserTravelPreferenceControllerTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    @AfterEach void clear() { CurrentUserContext.clear(); }

    @Test void absentGetReturnsEmptyObjectAndUsesAuthenticatedUser() throws Exception {
        PreferenceMemoryService service = mock(PreferenceMemoryService.class);
        when(service.find(7)).thenReturn(Optional.empty());
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));
        mvc(service).perform(get("/api/user/travel-preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interests").isArray())
                .andExpect(jsonPath("$.data.interests").isEmpty())
                .andExpect(jsonPath("$.data.pace").value(nullValue()))
                .andExpect(jsonPath("$.data.travelMode").value(nullValue()))
                .andExpect(jsonPath("$.data.accommodationPreference").value(nullValue()));
        verify(service).find(7);
    }

    @Test void putWholeReplacesWithServerTimeAndRejectsUnknownEnumAndBlankAccommodation() throws Exception {
        PreferenceMemoryService service = mock(PreferenceMemoryService.class);
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));
        mvc(service).perform(put("/api/user/travel-preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\":[\"FOOD\"],\"pace\":\"RELAXED\",\"travelMode\":\"TRANSIT\",\"accommodationPreference\":\"湖边\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedAt").exists());
        verify(service).replace(eq(7), argThat(value -> value.userId() == 7 && value.updatedAt().equals(NOW)));

        mvc(service).perform(put("/api/user/travel-preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pace\":\"FASTEST\",\"accommodationPreference\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.pace").exists())
                .andExpect(jsonPath("$.error.fields.accommodationPreference").exists());
    }

    @Test void deleteIsIdempotentAndStorageFailureIsSanitized() throws Exception {
        PreferenceMemoryService service = mock(PreferenceMemoryService.class);
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));
        MockMvc mvc = mvc(service);
        mvc.perform(delete("/api/user/travel-preferences")).andExpect(status().isOk());
        mvc.perform(delete("/api/user/travel-preferences")).andExpect(status().isOk());
        verify(service, times(2)).clear(7);
        when(service.find(7)).thenThrow(new PreferenceMemoryException("PREFERENCE_STORAGE_FAILED", new RuntimeException("SQL secret")));
        mvc.perform(get("/api/user/travel-preferences")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("PREFERENCE_STORAGE_FAILED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test void getExistingPreferenceDoesNotLeakUserIdOrAnotherUsersData() throws Exception {
        PreferenceMemoryService service = mock(PreferenceMemoryService.class);
        when(service.find(7)).thenReturn(Optional.of(new UserTravelPreference(7,
                Set.of(com.wyx.travelagent.planning.model.TravelInterest.HISTORY),
                com.wyx.travelagent.planning.model.TravelPace.MODERATE,
                com.wyx.travelagent.tools.model.TravelMode.TRANSIT, "虚构住宿偏好", NOW)));
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));

        mvc(service).perform(get("/api/user/travel-preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interests[0]").value("HISTORY"))
                .andExpect(jsonPath("$.data.pace").value("MODERATE"))
                .andExpect(jsonPath("$.data.userId").doesNotExist());
        verify(service).find(7);
        verify(service, never()).find(8);
    }

    @Test void requestUserIdCannotOverrideCurrentUserAndAllValidationBoundariesAreFieldLevel() throws Exception {
        PreferenceMemoryService service = mock(PreferenceMemoryService.class);
        CurrentUserContext.set(new CurrentUser(7, "user", Role.USER));
        MockMvc mvc = mvc(service);
        mvc.perform(put("/api/user/travel-preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":999,\"interests\":[\"FOOD\"],\"pace\":\"RELAXED\"}"))
                .andExpect(status().isOk());
        verify(service).replace(eq(7), argThat(value -> value.userId() == 7));

        String tooLong = "a".repeat(256);
        mvc.perform(put("/api/user/travel-preferences").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"interests\":[\"UNKNOWN\"],\"travelMode\":\"FLYING\",\"accommodationPreference\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.interests").exists())
                .andExpect(jsonPath("$.error.fields.travelMode").exists())
                .andExpect(jsonPath("$.error.fields.accommodationPreference").exists());
    }

    private static MockMvc mvc(PreferenceMemoryService service) {
        return MockMvcBuilders.standaloneSetup(new UserTravelPreferenceController(service, Clock.fixed(NOW, ZoneOffset.UTC)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
