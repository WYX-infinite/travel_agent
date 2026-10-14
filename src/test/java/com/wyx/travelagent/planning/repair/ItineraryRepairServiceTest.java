package com.wyx.travelagent.planning.repair;

import com.wyx.travelagent.planning.context.ConservativeTokenEstimator;
import com.wyx.travelagent.planning.context.ContextBudgetProperties;
import com.wyx.travelagent.planning.context.PromptContext;
import com.wyx.travelagent.planning.context.PromptContextAssembler;
import com.wyx.travelagent.planning.generation.ItineraryGenerationResult;
import com.wyx.travelagent.planning.generation.ItineraryGenerator;
import com.wyx.travelagent.planning.generation.ModelTokenUsage;
import com.wyx.travelagent.planning.memory.AppliedPreferenceSnapshot;
import com.wyx.travelagent.planning.memory.PreferenceReadStatus;
import com.wyx.travelagent.planning.memory.PreferenceSource;
import com.wyx.travelagent.planning.model.CompanionType;
import com.wyx.travelagent.planning.model.CostEstimate;
import com.wyx.travelagent.planning.model.CostEvidenceType;
import com.wyx.travelagent.planning.model.CostFact;
import com.wyx.travelagent.planning.model.ItineraryDay;
import com.wyx.travelagent.planning.model.ItineraryItem;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.ItineraryValidationFacts;
import com.wyx.travelagent.planning.model.OpeningHoursFact;
import com.wyx.travelagent.planning.model.ResolvedTravelRequirement;
import com.wyx.travelagent.planning.model.RouteFact;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItineraryRepairServiceTest {

    @Test
    void repairTemplateExplicitlyForbidsInventedFactsAndRequiresFullReplacementJson() {
        String rendered = com.wyx.travelagent.planning.context.ItineraryPromptRenderer.repair()
                .render("SAFE_CONTEXT");

        assertThat(rendered).contains("禁止创建新的 POI", "路线", "营业时间", "票价",
                "评分", "排名", "安全结论", "FACT", "DOCUMENT", "AI_ESTIMATE",
                "MISSING", "完整替换计划", "只输出单个 JSON 对象")
                .doesNotContain("{{PROMPT_CONTEXT}}");
    }

    @Test
    void budgetOverflowStopsBeforeRepairGeneratorCall() {
        CapturingGenerator generator = new CapturingGenerator();
        ItineraryRepairService service = new ItineraryRepairService(
                new PromptContextAssembler(new ContextBudgetProperties(
                        3, 1, 1, 1, 1, 1, 1, 1, 1), new ConservativeTokenEstimator()),
                generator);

        assertThatThrownBy(() -> service.repair(request(plan(), issue("DATE_MISSING")), context()))
                .isInstanceOf(com.wyx.travelagent.planning.context.ContextBudgetExceededException.class);
        assertThat(generator.contexts).isEmpty();
    }

    @Test
    void anyRequestedPoiOutsideOriginalFactsIsRejectedEvenWhenNotAffected() {
        ItineraryRepairService service = new ItineraryRepairService(assembler(), new CapturingGenerator());
        RepairRequest expanded = new RepairRequest(requirement(), plan(), List.of(issue("DATE_MISSING")),
                List.of(place(), new PlaceCandidate("outside", "伪造", "杭州",
                        new GeoPoint(121, 31, CoordinateSystem.GCJ02))), List.of());

        assertThatThrownBy(() -> service.repair(expanded, context()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("REPAIR_POI_OUTSIDE_ORIGINAL_CONTEXT");
    }

    @Test
    void everyRepairUsesAssemblerAndOnlyTheImmediatelyPreviousStructuredPlanAndCurrentIssues() {
        CapturingGenerator generator = new CapturingGenerator();
        ItineraryRepairService service = new ItineraryRepairService(assembler(), generator);
        TravelPlanningContext original = context();
        ItineraryPlan versionOne = plan();

        ItineraryGenerationResult versionTwo = service.repair(
                request(versionOne, issue("FIRST_ROUND_ONLY")), original).result();
        service.repair(request(versionTwo.plan(), issue("SECOND_ROUND_ONLY")), original);

        assertThat(generator.contexts).hasSize(2);
        PromptContext second = generator.contexts.get(1);
        assertThat(second.previousPlanEntries()).hasSize(1);
        assertThat(second.render()).contains("[PREVIOUS_PLAN]", "SECOND_ROUND_ONLY")
                .doesNotContain("FIRST_ROUND_ONLY", "RAW_MODEL_RESPONSE", "OLD_PROMPT", "TRACE_HISTORY");
        assertThat(count(second.render(), "[PREVIOUS_PLAN]")).isEqualTo(1);
        assertThat(second.allowedPoiIds()).containsExactly("poi-1");
    }

    @Test
    void repairKeepsUnchangedPlanPoisAllowedButFiltersUnrelatedDetailedFacts() {
        CapturingGenerator generator = new CapturingGenerator();
        ItineraryRepairService service = new ItineraryRepairService(assembler(), generator);

        service.repair(request(multiDayPlan(), issue("DAILY_INTENSITY_EXCEEDED")),
                contextWithDetailedFacts());

        PromptContext repairContext = generator.contexts.getFirst();
        assertThat(repairContext.allowedPoiIds()).containsExactly("poi-1", "poi-2");
        String toolFacts = String.join("\n", repairContext.toolFactEntries());
        assertThat(toolFacts)
                .contains("\"poiId\":\"poi-1\"", "\"poiId\":\"poi-2\"",
                        "\"distanceMeters\":111", "\"minimum\":\"11\"",
                        "\"date\":\"2026-10-01\"")
                .doesNotContain("\"distanceMeters\":222", "\"minimum\":\"22\"",
                        "\"date\":\"2026-10-02\"");
    }

    private static RepairRequest request(ItineraryPlan previous, ValidationIssue issue) {
        return new RepairRequest(requirement(), previous, List.of(issue),
                List.of(place(), otherPlace()), List.of());
    }

    private static ValidationIssue issue(String code) {
        return new ValidationIssue(code, IssueSeverity.ERROR, date(), null, "poi-1",
                "需要修正", null, null);
    }

    private static TravelPlanningContext context() {
        AppliedPreferenceSnapshot snapshot = new AppliedPreferenceSnapshot(Set.of(), null, null, null,
                PreferenceSource.NOT_PROVIDED, PreferenceSource.NOT_PROVIDED,
                PreferenceSource.NOT_PROVIDED, PreferenceSource.NOT_PROVIDED,
                false, PreferenceReadStatus.NOT_REQUESTED);
        ItineraryValidationFacts facts = new ItineraryValidationFacts(List.of(
                new ValidationPoiFact(place(), 0, true, false, OpeningHoursFact.unknown()),
                new ValidationPoiFact(otherPlace(), 1, false, false, OpeningHoursFact.unknown())),
                List.of(), List.of(), null, 0, List.of());
        return new TravelPlanningContext(new ResolvedTravelRequirement(requirement(), snapshot), facts);
    }

    private static TravelPlanningContext contextWithDetailedFacts() {
        AppliedPreferenceSnapshot snapshot = new AppliedPreferenceSnapshot(Set.of(), null, null, null,
                PreferenceSource.NOT_PROVIDED, PreferenceSource.NOT_PROVIDED,
                PreferenceSource.NOT_PROVIDED, PreferenceSource.NOT_PROVIDED,
                false, PreferenceReadStatus.NOT_REQUESTED);
        ItineraryValidationFacts facts = new ItineraryValidationFacts(List.of(
                new ValidationPoiFact(place(), 0, true, false, OpeningHoursFact.unknown()),
                new ValidationPoiFact(otherPlace(), 1, false, false, OpeningHoursFact.unknown())),
                List.of(
                        new RouteFact("poi-1", "poi-1",
                                new TravelSegment(TravelMode.WALKING, 111L, 11L, true)),
                        new RouteFact("poi-2", "poi-2",
                                new TravelSegment(TravelMode.WALKING, 222L, 22L, true))),
                List.of(
                        new CostFact("poi-1", CostEstimate.known(
                                BigDecimal.valueOf(11), BigDecimal.valueOf(11),
                                CostEvidenceType.FACT, null)),
                        new CostFact("poi-2", CostEstimate.known(
                                BigDecimal.valueOf(22), BigDecimal.valueOf(22),
                                CostEvidenceType.FACT, null))),
                new WeatherResult(List.of(
                        new WeatherResult.DailyForecast(date(), 10, 20, 10, 1),
                        new WeatherResult.DailyForecast(date().plusDays(1), 11, 21, 20, 2))),
                0, List.of());
        return new TravelPlanningContext(new ResolvedTravelRequirement(requirement(), snapshot), facts);
    }

    private static PromptContextAssembler assembler() {
        return new PromptContextAssembler(new ContextBudgetProperties(
                12000, 9000, 2000, 1000, 3000, 4000, 1000, 10, 5),
                new ConservativeTokenEstimator());
    }

    private static TravelRequirement requirement() {
        return new TravelRequirement("杭州", date(), date().plusDays(1), 1, BigDecimal.valueOf(1000),
                CompanionType.SOLO, Set.of(), null, null, null, null);
    }

    private static ItineraryPlan plan() {
        return new ItineraryPlan("杭州", date(), date(), List.of(new ItineraryDay(date(), List.of())),
                BigDecimal.ZERO);
    }

    private static ItineraryPlan multiDayPlan() {
        return new ItineraryPlan("杭州", date(), date().plusDays(1), List.of(
                new ItineraryDay(date(), List.of(item("poi-1", "西湖"))),
                new ItineraryDay(date().plusDays(1), List.of(item("poi-2", "未受影响景点")))),
                BigDecimal.ZERO);
    }

    private static ItineraryItem item(String poiId, String name) {
        return new ItineraryItem(LocalTime.of(9, 0), LocalTime.of(10, 0), poiId, name,
                null, CostEstimate.missing(), null);
    }

    private static PlaceCandidate place() {
        return new PlaceCandidate("poi-1", "西湖", "杭州", new GeoPoint(
                120.1, 30.2, CoordinateSystem.GCJ02));
    }

    private static PlaceCandidate otherPlace() {
        return new PlaceCandidate("poi-2", "未受影响景点", "杭州", new GeoPoint(
                120.2, 30.3, CoordinateSystem.GCJ02));
    }

    private static LocalDate date() {
        return LocalDate.of(2026, 10, 1);
    }

    private static int count(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }

    private static final class CapturingGenerator implements ItineraryGenerator {
        private final List<PromptContext> contexts = new ArrayList<>();

        @Override
        public ItineraryPlan generate(PromptContext context) {
            return generateResult(context).plan();
        }

        @Override
        public ItineraryGenerationResult generateResult(PromptContext context) {
            contexts.add(context);
            return new ItineraryGenerationResult(plan(), new ModelTokenUsage(30, 10), 0);
        }
    }
}
