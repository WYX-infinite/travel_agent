package com.wyx.travelagent.planning.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.planning.model.PreferenceValue;
import com.wyx.travelagent.planning.model.ResolvedTravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirementInput;
import com.wyx.travelagent.planning.memory.AppliedPreferenceSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** 仅在任务仓储边界确定性编解码结构化请求与冻结结果，不输出秘密或模型原文。 */
final class PlanningTaskPayloadCodec {
    private final ObjectMapper mapper;

    PlanningTaskPayloadCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper").copy().findAndRegisterModules();
    }

    String encodeRequest(TravelRequirementInput input) {
        StoredRequest stored = new StoredRequest(input.destinationCity(), input.startDate(), input.endDate(),
                input.travelers(), input.totalBudget(), input.companionType(), field(input.interests()),
                field(input.pace()), field(input.accommodationAddress()), field(input.travelMode()),
                input.specialRequirements(), input.useSavedPreferences());
        return write(stored);
    }

    TravelRequirementInput decodeRequest(String json) {
        StoredRequest stored = read(json, StoredRequest.class);
        return new TravelRequirementInput(stored.destinationCity(), stored.startDate(), stored.endDate(),
                stored.travelers(), stored.totalBudget(), stored.companionType(), listField(stored.interests()),
                textField(stored.pace()), textField(stored.accommodationAddress()), textField(stored.travelMode()),
                stored.specialRequirements(), stored.useSavedPreferences());
    }

    String encodeResolved(ResolvedTravelRequirement value) { return write(value); }
    ResolvedTravelRequirement decodeResolved(String json) { return read(json, ResolvedTravelRequirement.class); }
    String mapperWriteSnapshot(AppliedPreferenceSnapshot value) { return write(value); }
    AppliedPreferenceSnapshot decodeSnapshot(String json) { return read(json, AppliedPreferenceSnapshot.class); }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw PlanningTaskException.storageFailure(); }
    }

    private <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(Objects.requireNonNull(json, "json"), type); }
        catch (RuntimeException | JsonProcessingException exception) { throw PlanningTaskException.storageFailure(); }
    }

    private static StoredField field(PreferenceValue<?> value) {
        return new StoredField(value.state().name(), value.value());
    }

    @SuppressWarnings("unchecked")
    private static PreferenceValue<List<String>> listField(StoredField field) {
        PreferenceValue.State state = state(field);
        if (state == PreferenceValue.State.NOT_PROVIDED) return PreferenceValue.notProvided();
        if (state == PreferenceValue.State.EXPLICIT_EMPTY) return PreferenceValue.explicitEmpty();
        if (!(field.value() instanceof List<?> values)) throw PlanningTaskException.storageFailure();
        return PreferenceValue.explicitValue(values.stream().map(value -> value == null ? null : String.valueOf(value)).toList());
    }

    private static PreferenceValue<String> textField(StoredField field) {
        PreferenceValue.State state = state(field);
        if (state == PreferenceValue.State.NOT_PROVIDED) return PreferenceValue.notProvided();
        if (state == PreferenceValue.State.EXPLICIT_EMPTY) return PreferenceValue.explicitEmpty();
        if (!(field.value() instanceof String value)) throw PlanningTaskException.storageFailure();
        return PreferenceValue.explicitValue(value);
    }

    private static PreferenceValue.State state(StoredField field) {
        try { return PreferenceValue.State.valueOf(Objects.requireNonNull(field, "field").state()); }
        catch (RuntimeException exception) { throw PlanningTaskException.storageFailure(); }
    }

    private record StoredField(String state, Object value) { }
    private record StoredRequest(String destinationCity, LocalDate startDate, LocalDate endDate,
            Integer travelers, BigDecimal totalBudget, String companionType, StoredField interests,
            StoredField pace, StoredField accommodationAddress, StoredField travelMode,
            String specialRequirements, Boolean useSavedPreferences) { }
}
