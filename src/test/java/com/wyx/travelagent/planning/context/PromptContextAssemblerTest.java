package com.wyx.travelagent.planning.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeReference;
import com.wyx.travelagent.planning.memory.AppliedPreferenceSnapshot;
import com.wyx.travelagent.planning.memory.PreferenceReadStatus;
import com.wyx.travelagent.planning.memory.PreferenceSource;
import com.wyx.travelagent.planning.model.CompanionType;
import com.wyx.travelagent.planning.model.ItineraryValidationFacts;
import com.wyx.travelagent.planning.model.OpeningHoursFact;
import com.wyx.travelagent.planning.model.ResolvedTravelRequirement;
import com.wyx.travelagent.planning.model.RouteFact;
import com.wyx.travelagent.planning.model.TravelInterest;
import com.wyx.travelagent.planning.model.TravelPace;
import com.wyx.travelagent.planning.model.TravelPlanningContext;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.model.ValidationPoiFact;
import com.wyx.travelagent.planning.validation.IssueSeverity;
import com.wyx.travelagent.planning.validation.ValidationIssue;
import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.TravelMode;
import com.wyx.travelagent.tools.model.TravelSegment;
import com.wyx.travelagent.tools.model.WeatherResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptContextAssemblerTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final ContextBudgetProperties DEFAULTS =
            new ContextBudgetProperties(32768, 16000, 4000, 2000, 4000, 6000, 500, 20, 5);

    @Test
    void acceptsDocumentedDefaultsAndRejectsInvalidWindowOrPartitionBudgets() {
        assertThat(DEFAULTS.maxEstimatedInputTokens()).isEqualTo(16000);
        assertThatThrownBy(() -> new ContextBudgetProperties(
                20000, 16000, 4000, 1, 4000, 6000, 500, 20, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_CONTEXT_BUDGET");
        assertThatThrownBy(() -> new ContextBudgetProperties(
                32768, 16000, 4000, 2000, 16001, 6000, 500, 20, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_CONTEXT_BUDGET");
        assertThatThrownBy(() -> new ContextBudgetProperties(
                32768, 16000, 4000, 2000, 4000, 6000, 0, 20, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("INVALID_CONTEXT_BUDGET");
    }

    @Test
    void estimatorUsesTheDocumentedDeterministicUtf8Formula() {
        ConservativeTokenEstimator estimator = new ConservativeTokenEstimator();
        for (String value : List.of("ASCII json: {\"a\":1}\n", "中文换行\n第二行", "emoji 😀")) {
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            int newlines = (int) value.chars().filter(character -> character == '\n').count();
            int expected = Math.toIntExact((bytes + 2L) / 3L) + newlines + 8;
            assertThat(estimator.estimate(value)).isEqualTo(expected);
            assertThat(estimator.estimate(value)).isEqualTo(estimator.estimate(value));
        }
    }

    @Test
    void assemblesFiveDayFiftyPoiContextWithinEveryConfiguredLimit() {
        PromptContext result = new PromptContextAssembler(DEFAULTS, new ConservativeTokenEstimator())
                .assemble(largeInput());

        assertThat(result.budgetReport().withinBudget()).isTrue();
        assertThat(result.budgetReport().totalEstimatedTokens()).isLessThanOrEqualTo(16000);
        assertThat(result.budgetReport().retainedPoiCount()).isLessThanOrEqualTo(20);
        assertThat(result.budgetReport().retainedKnowledgeChunkCount()).isLessThanOrEqualTo(5);
        assertThat(result.budgetReport().memoryTokens()).isLessThanOrEqualTo(500);
        assertThat(result.budgetReport().toolFactTokens()).isLessThanOrEqualTo(6000);
        assertThat(result.budgetReport().knowledgeTokens()).isLessThanOrEqualTo(4000);
        assertThat(result.systemSection()).contains("JSON", "poiId", "FACT", "DOCUMENT", "AI_ESTIMATE");
        assertThat(result.hardConstraintSection()).contains("杭州", "2026-10-01", "2026-10-05", "3000");
        assertThat(result.allowedPoiIds()).contains("poi-000", "poi-001");
        assertThat(result.allowedPoiIds()).hasSize(result.budgetReport().retainedPoiCount());
    }

    @Test
    void reportsAndLimitsTheActualOutboundPromptIncludingTemplateEnvelope() throws Exception {
        ConservativeTokenEstimator estimator = new ConservativeTokenEstimator();
        PromptContext result = new PromptContextAssembler(DEFAULTS, estimator).assemble(largeInput());
        String template = new ClassPathResource("prompts/itinerary-generate.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        String outboundPrompt = template.replace("{{PROMPT_CONTEXT}}", result.render());

        assertThat(result.budgetReport().totalEstimatedTokens())
                .isEqualTo(estimator.estimate(outboundPrompt))
                .isLessThanOrEqualTo(DEFAULTS.maxEstimatedInputTokens());
    }

    @Test
    void preservesExplicitRequestValuesOutsideTheTrimmableMemorySection() {
        PromptContext result = new PromptContextAssembler(tightMemoryBudget(), new ConservativeTokenEstimator())
                .assemble(largeInput());

        assertThat(result.hardConstraintSection()).contains("MODERATE", "TRANSIT", "显式特殊要求");
        assertThat(result.memoryEntries()).allMatch(entry -> entry.contains("SAVED_PREFERENCE"));
        assertThat(result.budgetReport().trimmedMemoryFields())
                .containsExactlyElementsOf(result.budgetReport().trimmedMemoryFields().stream().sorted().toList());
    }

    @Test
    void sortsKnowledgeByScoreThenChunkIdAndOnlyRemovesWholeJsonObjects() throws Exception {
        PromptContext result = new PromptContextAssembler(DEFAULTS, new ConservativeTokenEstimator())
                .assemble(largeInput());

        assertThat(result.allowedKnowledgeChunkIds()).containsExactly(101L);
        ObjectMapper mapper = new ObjectMapper();
        for (String entry : result.knowledgeEntries()) {
            assertThat(mapper.readTree(entry).isObject()).isTrue();
        }
        for (String entry : result.toolFactEntries()) {
            assertThat(mapper.readTree(entry).isObject()).isTrue();
        }
    }

    @Test
    void trimsInStablePriorityOrderAndReportsCountsAndReasons() {
        ContextBudgetProperties tight = new ContextBudgetProperties(
                12000, 6000, 3000, 1000, 1200, 3000, 80, 8, 3);
        PromptContextAssembler assembler = new PromptContextAssembler(tight, new ConservativeTokenEstimator());

        PromptContext first = assembler.assemble(largeInput());
        PromptContext second = assembler.assemble(largeInput());

        assertThat(first).isEqualTo(second);
        assertThat(first.budgetReport().originalPoiCount()).isEqualTo(50);
        assertThat(first.budgetReport().retainedPoiCount()).isLessThanOrEqualTo(8);
        assertThat(first.budgetReport().originalKnowledgeChunkCount()).isEqualTo(5);
        assertThat(first.budgetReport().retainedKnowledgeChunkCount()).isLessThanOrEqualTo(3);
        assertThat(first.budgetReport().trimReasons())
                .contains("OPTIONAL_DESCRIPTION_REMOVED", "LOW_PRIORITY_POI_REMOVED");
    }

    @Test
    void canonicalizesWeatherOrderAndRejectsConflictingDuplicateDates() {
        PromptContextInput original = largeInput();
        ItineraryValidationFacts originalFacts = original.planningContext().validationFacts();
        List<WeatherResult.DailyForecast> reversed = new ArrayList<>(
                originalFacts.weather().dailyForecasts());
        Collections.reverse(reversed);
        ItineraryValidationFacts reorderedFacts = new ItineraryValidationFacts(
                originalFacts.places(), originalFacts.routes(), originalFacts.costs(),
                new WeatherResult(reversed), originalFacts.routeBufferMinutes(),
                originalFacts.knowledgeReferences());
        PromptContextInput reordered = new PromptContextInput(
                new TravelPlanningContext(original.planningContext().resolvedRequirement(), reorderedFacts),
                original.validationIssues(), original.optionalDescription());
        PromptContextAssembler assembler = new PromptContextAssembler(DEFAULTS, new ConservativeTokenEstimator());

        assertThat(assembler.assemble(reordered)).isEqualTo(assembler.assemble(original));

        WeatherResult.DailyForecast duplicate = reversed.getFirst();
        assertThatThrownBy(() -> new ItineraryValidationFacts(
                originalFacts.places(), originalFacts.routes(), originalFacts.costs(),
                new WeatherResult(List.of(duplicate, duplicate)), originalFacts.routeBufferMinutes(),
                originalFacts.knowledgeReferences()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate weather date");
    }

    @Test
    void apiCannotAcceptRawThirdPartyChatTraceOrPreviousPromptFields() {
        assertThat(PromptContextInput.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("planningContext", "validationIssues", "optionalDescription", "previousPlan")
                .doesNotContain("rawJson", "chatHistory", "trace", "previousPrompt", "modelResponse");
        assertThat(PromptContextInput.class.getRecordComponents()[3].getType())
                .isEqualTo(com.wyx.travelagent.planning.model.ItineraryPlan.class);
    }

    @Test
    void failsWithStableCodeWhenMandatorySectionsAloneExceedTheBudget() {
        ContextBudgetProperties tiny = new ContextBudgetProperties(
                5000, 300, 100, 100, 100, 100, 50, 20, 5);
        PromptContextAssembler assembler = new PromptContextAssembler(tiny, new ConservativeTokenEstimator());

        assertThatThrownBy(() -> assembler.assemble(largeInput()))
                .isInstanceOf(ContextBudgetExceededException.class)
                .hasMessage("CONTEXT_BUDGET_EXCEEDED")
                .satisfies(exception -> assertThat(
                        ((ContextBudgetExceededException) exception).code())
                        .isEqualTo("CONTEXT_BUDGET_EXCEEDED"));
    }

    private static ContextBudgetProperties tightMemoryBudget() {
        return new ContextBudgetProperties(32768, 16000, 4000, 2000, 4000, 6000, 40, 20, 5);
    }

    private static PromptContextInput largeInput() {
        List<ValidationPoiFact> places = new ArrayList<>();
        List<RouteFact> routes = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            String id = "poi-" + String.format("%03d", index);
            PlaceCandidate candidate = new PlaceCandidate(id, "虚构地点" + index,
                    "虚构地址" + index + "号", new GeoPoint(120.0 + index / 1000.0, 30.0, CoordinateSystem.GCJ02));
            places.add(new ValidationPoiFact(candidate, index, index < 2, index % 2 == 0,
                    OpeningHoursFact.reliable(LocalTime.of(8, 0), LocalTime.of(20, 0))));
            if (index > 0) {
                routes.add(new RouteFact("poi-" + String.format("%03d", index - 1), id,
                        new TravelSegment(TravelMode.TRANSIT, 1000L + index, 20L, true)));
            }
        }
        List<WeatherResult.DailyForecast> forecasts = new ArrayList<>();
        for (int day = 0; day < 5; day++) {
            forecasts.add(new WeatherResult.DailyForecast(START.plusDays(day), 15, 25, 20, 0));
        }
        List<KnowledgeReference> references = new ArrayList<>();
        for (int index = 5; index >= 1; index--) {
            references.add(new KnowledgeReference(1L, 100L + index, index,
                    "虚构资料" + index, "杭州", "虚构机构", URI.create("https://example.test/" + index),
                    LocalDate.of(2026, 1, index), KnowledgeDocumentType.ATTRACTION_GUIDE,
                    ("完整知识对象" + index + "。").repeat(300), 0.9 - index / 100.0));
        }
        ItineraryValidationFacts facts = new ItineraryValidationFacts(
                places, routes, List.of(), new WeatherResult(forecasts), 15, references);
        TravelRequirement requirement = new TravelRequirement("杭州", START, START.plusDays(4), 2,
                new BigDecimal("3000"), CompanionType.FRIENDS,
                EnumSet.of(TravelInterest.FOOD, TravelInterest.HISTORY), TravelPace.MODERATE,
                "显式住宿位置", TravelMode.TRANSIT, "显式特殊要求");
        AppliedPreferenceSnapshot snapshot = new AppliedPreferenceSnapshot(
                EnumSet.of(TravelInterest.FOOD, TravelInterest.HISTORY), TravelPace.MODERATE,
                TravelMode.TRANSIT, "记忆住宿偏好".repeat(80),
                PreferenceSource.SAVED_PREFERENCE, PreferenceSource.EXPLICIT_REQUEST,
                PreferenceSource.EXPLICIT_REQUEST, PreferenceSource.SAVED_PREFERENCE,
                true, PreferenceReadStatus.SUCCEEDED);
        TravelPlanningContext context = new TravelPlanningContext(
                new ResolvedTravelRequirement(requirement, snapshot), facts);
        ValidationIssue correction = new ValidationIssue("ROUTE_TIME_INSUFFICIENT", IssueSeverity.ERROR,
                START, 1, "poi-001", "fixed safe message", "20 minutes", "5 minutes");
        return new PromptContextInput(context, List.of(correction), "可选说明".repeat(5000));
    }
}
