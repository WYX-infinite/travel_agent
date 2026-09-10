package com.wyx.travelagent.planning.validation;

import com.wyx.travelagent.knowledge.retrieval.KnowledgeReference;
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
import com.wyx.travelagent.planning.model.TravelPace;
import com.wyx.travelagent.planning.model.TravelPlanningContext;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.model.ValidationPoiFact;
import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.TravelMode;
import com.wyx.travelagent.tools.model.TravelSegment;
import com.wyx.travelagent.tools.model.WeatherResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItineraryValidatorTest {
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 1);
    private static final String POI_A = "poi-a";
    private static final String POI_B = "poi-b";
    private static final String POI_C = "poi-c";
    private final ItineraryValidator validator = new ItineraryValidator();

    @Test
    void rejectsPoiOutsideTheIdWhitelistWithoutFallingBackToItsName() {
        TravelPlanningContext context = context(TravelPace.MODERATE, "1000", clearWeather(),
                poi(POI_A, false, hours(8, 18)), List.of(), 15);
        ItineraryPlan plan = plan(FIRST_DAY, item(9, 10, "unknown", known("10")));

        assertThat(validator.validate(context, plan).errors())
                .extracting(ValidationIssue::code)
                .contains("UNKNOWN_POI");
    }

    @Test
    void reportsMissingExtraAndDuplicateDatesAsErrors() {
        TravelPlanningContext context = context(FIRST_DAY, FIRST_DAY.plusDays(1), TravelPace.MODERATE,
                "1000", clearWeather(), List.of(poi(POI_A, false, hours(8, 18))), List.of(), 15);
        ItineraryDay duplicate = new ItineraryDay(FIRST_DAY, List.of(item(9, 10, POI_A, known("10"))));
        ItineraryPlan plan = new ItineraryPlan("杭州", FIRST_DAY, FIRST_DAY.plusDays(2),
                List.of(duplicate, duplicate,
                        new ItineraryDay(FIRST_DAY.plusDays(2), List.of(item(9, 10, POI_A, known("10"))))),
                new BigDecimal("30"));

        assertThat(validator.validate(context, plan).errors())
                .extracting(ValidationIssue::code)
                .contains("DATE_DUPLICATE", "DATE_MISSING", "DATE_EXTRA", "PLAN_DATE_RANGE_MISMATCH");
    }

    @Test
    void rejectsEndTimeThatIsNotLaterThanStartTime() {
        ValidationReport report = validator.validate(validContext(),
                plan(FIRST_DAY, item(10, 10, POI_A, known("10"))));

        assertThat(report.errors()).extracting(ValidationIssue::code)
                .containsExactly("INVALID_ACTIVITY_TIME");
    }

    @Test
    void detectsOverlapIndependentlyOfTheOriginalCollectionOrder() {
        ItineraryItem early = item(9, 11, POI_A, known("10"));
        ItineraryItem late = item(10, 12, POI_B, known("10"));
        TravelPlanningContext context = context(TravelPace.MODERATE, "1000", clearWeather(),
                poi(POI_A, false, hours(8, 18)), poi(POI_B, false, hours(8, 18)), route(POI_A, POI_B, 15));

        assertThat(validator.validate(context, plan(FIRST_DAY, late, early)).errors())
                .extracting(ValidationIssue::code)
                .contains("ACTIVITY_OVERLAP");
    }

    @Test
    void requiresGapToCoverVerifiedTravelTimeAndConfiguredBuffer() {
        TravelPlanningContext context = context(TravelPace.MODERATE, "1000", clearWeather(),
                poi(POI_A, false, hours(8, 18)), poi(POI_B, false, hours(8, 18)), route(POI_A, POI_B, 30));
        ItineraryPlan plan = plan(FIRST_DAY,
                item(9, 10, POI_A, known("10")), item(10, 44, 11, 44, POI_B, known("10")));

        ValidationIssue issue = validator.validate(context, plan).errors().getFirst();
        assertThat(issue.code()).isEqualTo("ROUTE_TIME_INSUFFICIENT");
        assertThat(issue.knownValue()).isEqualTo("30 minutes travel + 15 minutes buffer");
        assertThat(issue.requiredAdjustment()).isEqualTo("1 minutes");
    }

    @Test
    void selectsRouteByDeclaredTravelModeAndWarnsWhenThatModeHasNoFact() {
        TravelPlanningContext context = context(TravelPace.MODERATE, "1000", clearWeather(),
                poi(POI_A, false, hours(8, 18)), poi(POI_B, false, hours(8, 18)),
                route(POI_A, POI_B, TravelMode.WALKING, 5),
                route(POI_A, POI_B, TravelMode.TRANSIT, 60));
        ItineraryItem origin = item(9, 10, POI_A, known("10"));
        ItineraryItem transitDestination = item(
                10, 30, 11, 30, POI_B, known("10"), TravelMode.TRANSIT);
        ItineraryItem taxiDestination = item(
                10, 30, 11, 30, POI_B, known("10"), TravelMode.TAXI);

        ValidationIssue transitIssue = validator.validate(
                context, plan(FIRST_DAY, origin, transitDestination)).errors().getFirst();
        assertThat(transitIssue.code()).isEqualTo("ROUTE_TIME_INSUFFICIENT");
        assertThat(transitIssue.knownValue()).startsWith("60 minutes travel");
        assertThat(validator.validate(context, plan(FIRST_DAY, origin, taxiDestination)).warnings())
                .extracting(ValidationIssue::code)
                .contains("ROUTE_NOT_VERIFIED");
    }

    @Test
    void warnsWhenAdjacentRouteIsMissingOrUnverifiedInsteadOfInventingDuration() {
        TravelPlanningContext context = context(TravelPace.MODERATE, "1000", clearWeather(),
                poi(POI_A, false, hours(8, 18)), poi(POI_B, false, hours(8, 18)));
        ItineraryPlan plan = plan(FIRST_DAY,
                item(9, 10, POI_A, known("10")), item(11, 12, POI_B, known("10")));

        ValidationReport report = validator.validate(context, plan);
        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).extracting(ValidationIssue::code)
                .containsExactly("ROUTE_NOT_VERIFIED");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("paceBoundaries")
    void enforcesCentralizedPaceActivityBoundaries(String name, TravelPace pace, int allowed, int rejected) {
        List<ValidationPoiFact> places = new ArrayList<>();
        for (int index = 0; index < rejected; index++) {
            places.add(poi("poi-" + index, false, hours(0, 23)));
        }
        TravelPlanningContext context = context(FIRST_DAY, FIRST_DAY, pace, "10000", clearWeather(), places, List.of(), 0);

        assertThat(validator.validate(context, spacedPlan(allowed)).errors())
                .extracting(ValidationIssue::code).doesNotContain("DAILY_INTENSITY_EXCEEDED");
        assertThat(validator.validate(context, spacedPlan(rejected)).errors())
                .extracting(ValidationIssue::code).contains("DAILY_INTENSITY_EXCEEDED");
    }

    static Stream<Arguments> paceBoundaries() {
        return Stream.of(
                Arguments.of("轻松四项", TravelPace.RELAXED, 4, 5),
                Arguments.of("适中六项", TravelPace.MODERATE, 6, 7),
                Arguments.of("紧凑八项", TravelPace.COMPACT, 8, 9));
    }

    @Test
    void acceptsBudgetEqualToConservativeUpperBoundsAndRejectsAnyExcess() {
        TravelPlanningContext context = context(TravelPace.MODERATE, "100", clearWeather(),
                poi(POI_A, false, hours(8, 18)), poi(POI_B, false, hours(8, 18)), route(POI_A, POI_B, 0));

        assertThat(validator.validate(context, plan(FIRST_DAY,
                item(9, 10, POI_A, range("20", "40")), item(11, 12, POI_B, range("30", "60")))).errors())
                .extracting(ValidationIssue::code).doesNotContain("BUDGET_EXCEEDED");
        assertThat(validator.validate(context, plan(FIRST_DAY,
                item(9, 10, POI_A, range("20", "40")), item(11, 12, POI_B, range("30", "60.01")))).errors())
                .extracting(ValidationIssue::code).contains("BUDGET_EXCEEDED");
    }

    @Test
    void warnsWhenAnyCostIsMissing() {
        ValidationReport report = validator.validate(validContext(),
                plan(FIRST_DAY, item(9, 10, POI_A, CostEstimate.missing())));

        assertThat(report.warnings()).extracting(ValidationIssue::code)
                .contains("COST_INFORMATION_INCOMPLETE");
    }

    @Test
    void matchesTrustedCostEvidenceByNumericValueRegardlessOfDecimalScale() {
        CostEstimate trusted = CostEstimate.known(
                new BigDecimal("10.00"), new BigDecimal("20.00"), CostEvidenceType.FACT, null);
        CostEstimate generated = CostEstimate.known(
                new BigDecimal("10.0"), new BigDecimal("20.0"), CostEvidenceType.FACT, null);
        TravelPlanningContext context = context(TravelPace.MODERATE, "1000", clearWeather(),
                poi(POI_A, false, hours(8, 18)), new CostFact(POI_A, trusted));

        assertThat(validator.validate(context, plan(FIRST_DAY, item(9, 10, POI_A, generated))).warnings())
                .extracting(ValidationIssue::code)
                .doesNotContain("COST_EVIDENCE_UNVERIFIED");
    }

    @Test
    void validatesOnlyReliableOpeningHoursAndWarnsWhenUnknown() {
        TravelPlanningContext inside = context(TravelPace.MODERATE, "1000", clearWeather(),
                poi(POI_A, false, hours(8, 18)));
        TravelPlanningContext outside = context(TravelPace.MODERATE, "1000", clearWeather(),
                poi(POI_A, false, hours(10, 18)));
        TravelPlanningContext unknown = context(TravelPace.MODERATE, "1000", clearWeather(),
                poi(POI_A, false, OpeningHoursFact.unknown()));

        assertThat(validator.validate(inside, plan(FIRST_DAY, item(9, 10, POI_A, known("10")))).issues()).isEmpty();
        assertThat(validator.validate(outside, plan(FIRST_DAY, item(9, 10, POI_A, known("10")))).errors())
                .extracting(ValidationIssue::code).containsExactly("OUTSIDE_OPENING_HOURS");
        assertThat(validator.validate(unknown, plan(FIRST_DAY, item(9, 10, POI_A, known("10")))).warnings())
                .extracting(ValidationIssue::code).containsExactly("OPENING_HOURS_UNKNOWN");
    }

    @Test
    void warnsForOutdoorActivityDuringDeterministicallyAdverseWeather() {
        WeatherResult adverse = weather(80, 12.0);
        TravelPlanningContext context = context(TravelPace.MODERATE, "1000", adverse,
                poi(POI_A, true, hours(8, 18)));

        assertThat(validator.validate(context, plan(FIRST_DAY, item(9, 10, POI_A, known("10")))).warnings())
                .extracting(ValidationIssue::code).containsExactly("WEATHER_OUTDOOR_RISK");
    }

    @Test
    void missingWeatherNeverProducesADefiniteWeatherConclusion() {
        TravelPlanningContext context = context(TravelPace.MODERATE, "1000", null,
                poi(POI_A, true, hours(8, 18)));

        assertThat(validator.validate(context, plan(FIRST_DAY, item(9, 10, POI_A, known("10")))).warnings())
                .extracting(ValidationIssue::code)
                .containsExactly("WEATHER_DATA_MISSING")
                .doesNotContain("WEATHER_OUTDOOR_RISK");
    }

    @Test
    void reportSeparatesSeveritySortsDeterministicallyAndDefensivelyCopies() {
        List<ValidationIssue> mutable = new ArrayList<>(List.of(
                issue("Z_WARNING", IssueSeverity.WARNING, FIRST_DAY.plusDays(1), 2, POI_B),
                issue("B_ERROR", IssueSeverity.ERROR, FIRST_DAY, 2, POI_B),
                issue("A_ERROR", IssueSeverity.ERROR, FIRST_DAY, 1, POI_A)));
        ValidationReport report = new ValidationReport(mutable);
        mutable.clear();

        assertThat(report.issues()).extracting(ValidationIssue::code)
                .containsExactly("A_ERROR", "B_ERROR", "Z_WARNING");
        assertThat(report.errors()).hasSize(2);
        assertThat(report.warnings()).hasSize(1);
        assertThat(report.canProceed()).isFalse();
        assertThatThrownBy(() -> report.issues().add(issue("X", IssueSeverity.WARNING, null, null, null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validatorHasNoModelOrInfrastructureDependencies() {
        assertThat(ItineraryValidator.class.getDeclaredFields())
                .allSatisfy(field -> assertThat(field.getType().getName())
                        .doesNotContain("langchain4j", "Repository", "Redis", "RestClient"));
    }

    private static ValidationIssue issue(String code, IssueSeverity severity, LocalDate date,
                                         Integer itemIndex, String poiId) {
        return new ValidationIssue(code, severity, date, itemIndex, poiId,
                "fixed safe message", null, null);
    }

    private static ItineraryPlan spacedPlan(int count) {
        List<ItineraryItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int startMinutes = 8 * 60 + index * 60;
            items.add(item(startMinutes / 60, startMinutes % 60,
                    startMinutes / 60, startMinutes % 60 + 30, "poi-" + index, known("1")));
        }
        return new ItineraryPlan("杭州", FIRST_DAY, FIRST_DAY,
                List.of(new ItineraryDay(FIRST_DAY, items)), new BigDecimal(count));
    }

    private static TravelPlanningContext validContext() {
        return context(TravelPace.MODERATE, "1000", clearWeather(), poi(POI_A, false, hours(8, 18)));
    }

    private static TravelPlanningContext context(TravelPace pace, String budget, WeatherResult weather,
                                                 Object... facts) {
        List<ValidationPoiFact> places = new ArrayList<>();
        List<RouteFact> routes = new ArrayList<>();
        List<CostFact> costs = new ArrayList<>();
        for (Object fact : facts) {
            if (fact instanceof ValidationPoiFact place) places.add(place);
            if (fact instanceof RouteFact route) routes.add(route);
            if (fact instanceof CostFact cost) costs.add(cost);
        }
        return context(FIRST_DAY, FIRST_DAY, pace, budget, weather, places, routes, costs, 15);
    }

    private static TravelPlanningContext context(LocalDate start, LocalDate end, TravelPace pace, String budget,
                                                 WeatherResult weather, List<ValidationPoiFact> places,
                                                 List<RouteFact> routes, int bufferMinutes) {
        return context(start, end, pace, budget, weather, places, routes, List.of(), bufferMinutes);
    }

    private static TravelPlanningContext context(
            LocalDate start, LocalDate end, TravelPace pace, String budget,
            WeatherResult weather, List<ValidationPoiFact> places,
            List<RouteFact> routes, List<CostFact> costs, int bufferMinutes) {
        TravelRequirement requirement = new TravelRequirement("杭州", start, end, 2, new BigDecimal(budget),
                CompanionType.FRIENDS, Set.of(), pace, null, TravelMode.TRANSIT, null);
        AppliedPreferenceSnapshot snapshot = new AppliedPreferenceSnapshot(Set.of(), pace, TravelMode.TRANSIT, null,
                PreferenceSource.NOT_PROVIDED, PreferenceSource.EXPLICIT_REQUEST,
                PreferenceSource.EXPLICIT_REQUEST, PreferenceSource.NOT_PROVIDED,
                false, PreferenceReadStatus.NOT_REQUESTED);
        ItineraryValidationFacts facts = new ItineraryValidationFacts(
                places, routes, costs, weather, bufferMinutes, List.<KnowledgeReference>of());
        return new TravelPlanningContext(new ResolvedTravelRequirement(requirement, snapshot), facts);
    }

    private static ValidationPoiFact poi(String id, boolean outdoor, OpeningHoursFact openingHours) {
        PlaceCandidate place = new PlaceCandidate(id, "虚构地点" + id, "虚构地址",
                new GeoPoint(120.1, 30.2, CoordinateSystem.GCJ02));
        return new ValidationPoiFact(place, 1, true, outdoor, openingHours);
    }

    private static OpeningHoursFact hours(int openHour, int closeHour) {
        return OpeningHoursFact.reliable(LocalTime.of(openHour, 0), LocalTime.of(closeHour, 0));
    }

    private static RouteFact route(String from, String to, long minutes) {
        return route(from, to, TravelMode.TRANSIT, minutes);
    }

    private static RouteFact route(String from, String to, TravelMode mode, long minutes) {
        return new RouteFact(from, to, new TravelSegment(mode, 1000L, minutes, true));
    }

    private static WeatherResult clearWeather() {
        return weather(10, 0.0);
    }

    private static WeatherResult weather(int probability, double millimeters) {
        return new WeatherResult(List.of(new WeatherResult.DailyForecast(
                FIRST_DAY, 16, 24, probability, millimeters)));
    }

    private static CostEstimate known(String amount) {
        return CostEstimate.known(new BigDecimal(amount), new BigDecimal(amount),
                CostEvidenceType.AI_ESTIMATE, null);
    }

    private static CostEstimate range(String minimum, String maximum) {
        return CostEstimate.known(new BigDecimal(minimum), new BigDecimal(maximum),
                CostEvidenceType.AI_ESTIMATE, null);
    }

    private static ItineraryItem item(int startHour, int endHour, String poiId, CostEstimate cost) {
        return item(startHour, 0, endHour, 0, poiId, cost);
    }

    private static ItineraryItem item(int startHour, int startMinute, int endHour, int endMinute,
                                      String poiId, CostEstimate cost) {
        return item(startHour, startMinute, endHour, endMinute, poiId, cost, TravelMode.TRANSIT);
    }

    private static ItineraryItem item(int startHour, int startMinute, int endHour, int endMinute,
                                      String poiId, CostEstimate cost, TravelMode travelMode) {
        return new ItineraryItem(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute),
                poiId, "虚构活动", null, cost, travelMode);
    }

    private static ItineraryPlan plan(LocalDate date, ItineraryItem... items) {
        BigDecimal total = Stream.of(items)
                .filter(item -> item.cost().isKnown())
                .map(item -> item.cost().maximum())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ItineraryPlan("杭州", date, date, List.of(new ItineraryDay(date, List.of(items))), total);
    }
}
