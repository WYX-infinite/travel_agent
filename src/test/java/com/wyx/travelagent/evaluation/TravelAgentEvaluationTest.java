package com.wyx.travelagent.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.evaluation.EvaluationReportWriter.CaseResult;
import com.wyx.travelagent.evaluation.EvaluationReportWriter.MetricResult;
import com.wyx.travelagent.evaluation.EvaluationReportWriter.Report;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import com.wyx.travelagent.knowledge.retrieval.CitationValidationResult;
import com.wyx.travelagent.knowledge.retrieval.CitationValidator;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeReference;
import com.wyx.travelagent.planning.context.ConservativeTokenEstimator;
import com.wyx.travelagent.planning.context.ContextBudgetExceededException;
import com.wyx.travelagent.planning.context.ContextBudgetProperties;
import com.wyx.travelagent.planning.context.ContextBudgetReport;
import com.wyx.travelagent.planning.context.PromptContext;
import com.wyx.travelagent.planning.context.PromptContextAssembler;
import com.wyx.travelagent.planning.context.PromptContextInput;
import com.wyx.travelagent.planning.generation.ItineraryGenerationException;
import com.wyx.travelagent.planning.generation.LangChain4jItineraryGenerator;
import com.wyx.travelagent.planning.memory.AppliedPreferenceSnapshot;
import com.wyx.travelagent.planning.memory.PreferenceMemoryService;
import com.wyx.travelagent.planning.memory.PreferenceReadStatus;
import com.wyx.travelagent.planning.memory.PreferenceSource;
import com.wyx.travelagent.planning.memory.UserTravelPreference;
import com.wyx.travelagent.planning.memory.UserTravelPreferenceRepository;
import com.wyx.travelagent.planning.metrics.PlanMetrics;
import com.wyx.travelagent.planning.metrics.PlanMetricsCalculator;
import com.wyx.travelagent.planning.model.CompanionType;
import com.wyx.travelagent.planning.model.CostEstimate;
import com.wyx.travelagent.planning.model.CostEvidenceType;
import com.wyx.travelagent.planning.model.CostFact;
import com.wyx.travelagent.planning.model.ItineraryDay;
import com.wyx.travelagent.planning.model.ItineraryItem;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.ItineraryValidationFacts;
import com.wyx.travelagent.planning.model.OpeningHoursFact;
import com.wyx.travelagent.planning.model.PreferenceValue;
import com.wyx.travelagent.planning.model.ResolvedTravelRequirement;
import com.wyx.travelagent.planning.model.RouteFact;
import com.wyx.travelagent.planning.model.TravelInterest;
import com.wyx.travelagent.planning.model.TravelPace;
import com.wyx.travelagent.planning.model.TravelPlanningContext;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirementInput;
import com.wyx.travelagent.planning.model.ValidationPoiFact;
import com.wyx.travelagent.planning.validation.ItineraryValidator;
import com.wyx.travelagent.planning.validation.RequirementValidator;
import com.wyx.travelagent.planning.validation.ValidationReport;
import com.wyx.travelagent.tools.dispatch.ToolAction;
import com.wyx.travelagent.tools.dispatch.ToolExecutionBundle;
import com.wyx.travelagent.tools.dispatch.ToolExecutionPolicy;
import com.wyx.travelagent.tools.dispatch.ToolPlan;
import com.wyx.travelagent.tools.dispatch.ToolPlanValidator;
import com.wyx.travelagent.tools.dispatch.TravelToolDispatcher;
import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.TravelMode;
import com.wyx.travelagent.tools.model.TravelSegment;
import com.wyx.travelagent.tools.model.WeatherResult;
import com.wyx.travelagent.tools.port.PlaceProvider;
import com.wyx.travelagent.tools.port.RouteProvider;
import com.wyx.travelagent.tools.port.WeatherProvider;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Deterministic offline evaluation. Every provider, model response and fact is synthetic. */
class TravelAgentEvaluationTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 20);
    private static final LocalDateTime RETRIEVED_AT = LocalDateTime.of(2026, 10, 14, 9, 0);
    private static final String CITY = "虚构测试城";
    private static final String POI_A = "fixture-poi-a";
    private static final String POI_B = "fixture-poi-b";
    private static final ContextBudgetProperties CONTEXT_LIMITS =
            new ContextBudgetProperties(32768, 16000, 4000, 2000, 4000, 6000, 500, 20, 5);
    private static final String VALID_MODEL_JSON = """
            {"destinationCity":"虚构测试城","startDate":"2026-10-20","endDate":"2026-10-20",
            "days":[{"date":"2026-10-20","items":[{"startTime":"09:00","endTime":"10:00",
            "poiId":"fixture-poi-a","name":"虚构地点A","description":"fixture",
            "cost":{"status":"KNOWN","minimum":0,"maximum":0,"evidenceType":"FACT","knowledgeChunkId":null},
            "travelModeFromPrevious":null}]}],"estimatedTotalCost":0}
            """;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ItineraryValidator itineraryValidator = new ItineraryValidator();
    private final PlanMetricsCalculator metricsCalculator = new PlanMetricsCalculator();

    @Test
    void datasetContainsFortyUniqueSyntheticCasesWithCompleteExpectations() throws Exception {
        Dataset dataset = loadDataset();

        assertThat(dataset.datasetId()).isEqualTo("travel-agent-offline-v1");
        assertThat(dataset.fixtureDataClass()).isEqualTo("SYNTHETIC_FICTIONAL_ONLY");
        assertThat(dataset.cases()).hasSizeBetween(30, 50);
        assertThat(dataset.cases()).extracting(EvaluationCase::id).doesNotHaveDuplicates();
        assertThat(dataset.cases()).allSatisfy(testCase -> {
            assertThat(testCase.id()).matches("EVAL-[0-9]{3}");
            assertThat(testCase.scenarioType()).isNotBlank();
            assertThat(testCase.fixtureRef()).isNotBlank();
            assertThat(testCase.expectedTaskStatus()).isNotBlank();
            assertThat(testCase.rulesOrMetrics()).isNotEmpty().doesNotContainNull();
        });
    }

    @Test
    void evaluatesAllCasesAndWritesStableSanitizedReports() throws Exception {
        Dataset dataset = loadDataset();
        EvaluationRun first = evaluate(dataset.cases());
        Report report = new Report(dataset.datasetId(), first.metrics(), first.results(), List.of(
                "Offline fixtures and fake providers do not measure real model or provider success rates.",
                "The conservative token estimator is not a provider tokenizer.",
                "Warnings can reduce rule pass rate without changing a task from COMPLETED.",
                "Zero denominators are reported as N/A, never as 100%."));
        EvaluationReportWriter writer = new EvaluationReportWriter();
        writer.write(report);
        String firstJson = Files.readString(EvaluationReportWriter.JSON_REPORT, StandardCharsets.UTF_8);
        String firstMarkdown = Files.readString(EvaluationReportWriter.MARKDOWN_REPORT, StandardCharsets.UTF_8);
        writer.write(report);

        assertThat(first.results()).allMatch(CaseResult::passed);
        assertThat(Files.readString(EvaluationReportWriter.JSON_REPORT, StandardCharsets.UTF_8)).isEqualTo(firstJson);
        assertThat(Files.readString(EvaluationReportWriter.MARKDOWN_REPORT, StandardCharsets.UTF_8)).isEqualTo(firstMarkdown);
        assertThat(firstJson).contains("DETERMINISTIC_OFFLINE", "\"numerator\"", "\"denominator\"", "N/A")
                .doesNotContain("api-key", "Authorization", "Bearer ", "sk-", "PROMPT_CONTEXT", VALID_MODEL_JSON);
        assertThat(firstMarkdown).doesNotContain("api-key", "Authorization", "Bearer ", "sk-", VALID_MODEL_JSON);
    }

    @Test
    void realSmokeTestRequiresTheDedicatedMavenProfileAndIsNotASurefireTest() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        Path smokeTest = Path.of("src", "test", "java", "com", "wyx", "travelagent", "evaluation",
                "TravelAgentRealSmokeIT.java");

        assertThat(pom).contains("<id>real-smoke</id>", "maven-failsafe-plugin", "**/*RealSmokeIT.java");
        assertThat(smokeTest).exists();
        assertThat(smokeTest.getFileName().toString()).endsWith("IT.java").doesNotEndWith("Test.java");
        assertThat(Files.readString(smokeTest, StandardCharsets.UTF_8))
                .contains("@Tag(\"real-smoke\")", "TRAVEL_REAL_SMOKE_ENABLED")
                .doesNotContain("sk-", "api-key:");
    }

    private EvaluationRun evaluate(List<EvaluationCase> cases) {
        List<CaseResult> results = new ArrayList<>();
        Counts counts = new Counts();
        cases.stream().sorted(java.util.Comparator.comparing(EvaluationCase::id)).forEach(testCase -> {
            Outcome outcome = execute(testCase.fixtureRef());
            boolean statusMatches = testCase.expectedTaskStatus().equals(outcome.status());
            boolean errorMatches = Objects.equals(testCase.expectedErrorCode(), outcome.errorCode());
            String failureReason = !statusMatches ? "task status mismatch"
                    : !errorMatches ? "error code mismatch" : null;
            boolean passed = statusMatches && errorMatches;
            results.add(new CaseResult(testCase.id(), testCase.scenarioType(), testCase.expectedTaskStatus(),
                    outcome.status(), testCase.expectedErrorCode(), outcome.errorCode(), passed,
                    failureReason, outcome.repairAttempts()));
            counts.add(testCase, outcome, passed);
        });
        return new EvaluationRun(List.of(
                metric("fabricatedPoiBlockingRate", counts.fabricatedPoiBlocked, counts.fabricatedPoiAttempts,
                        "blocked fabricated POI attempts / fabricated POI attempts"),
                metric("citationCoverage", counts.citationNumerator, counts.citationDenominator,
                        "items with FACT or valid DOCUMENT evidence / itinerary items"),
                metric("routeCoverage", counts.routeNumerator, counts.routeDenominator,
                        "verified adjacent routes / adjacent itinerary movements"),
                metric("rulePassRate", counts.ruleNumerator, counts.ruleDenominator,
                        "rules without issues / executed deterministic rules"),
                metric("repairSuccessRate", counts.repairSucceeded, counts.repairRequired,
                        "repair-required cases completed / repair-required cases"),
                metric("averageRepairAttempts", counts.repairAttempts, counts.repairRequired,
                        "sum of repair attempts / repair-required cases"),
                metric("preferenceMergeAccuracy", counts.preferenceCorrect, counts.preferenceCases,
                        "correct preference outcomes / preference cases"),
                metric("userIsolation", counts.isolationCorrect, counts.isolationCases,
                        "isolated user reads / user isolation cases"),
                metric("cacheBehavior", counts.cacheCorrect, counts.cacheCases,
                        "correct cache outcomes / cache cases"),
                metric("taskStatusAccuracy", counts.statusCorrect, counts.totalCases,
                        "cases with expected task status and error code / all cases"),
                metric("contextTrimmingDeterminism", counts.contextCorrect, counts.contextCases,
                        "repeatable context boundary outcomes / context boundary cases"),
                metric("emptyPlanCitationCoverage", counts.emptyCitationNumerator, counts.emptyCitationDenominator,
                        "sourced items / items in the empty-plan zero-denominator fixture")
        ), List.copyOf(results));
    }

    private Outcome execute(String fixture) {
        return switch (fixture) {
            case "valid-basic" -> validatePlan(CompanionType.FRIENDS, TravelPace.MODERATE,
                    new BigDecimal("1000"), clearWeather(), List.of(poi(POI_A, false), poi(POI_B, false)),
                    List.of(route(POI_A, POI_B, 20)), List.of(costFact(POI_A, "20", CostEvidenceType.FACT, null)),
                    List.of(), plan(item(9, 10, POI_A, cost("20", CostEvidenceType.FACT, null), null),
                            item(11, 12, POI_B, cost("10", CostEvidenceType.AI_ESTIMATE, null), TravelMode.TRANSIT)));
            case "valid-family" -> validatePlan(CompanionType.FAMILY, TravelPace.RELAXED,
                    new BigDecimal("1000"), clearWeather(), List.of(poi(POI_A, false)), List.of(),
                    List.of(costFact(POI_A, "20", CostEvidenceType.DOCUMENT, 101L)), List.of(reference(101L)),
                    plan(item(9, 10, POI_A, cost("20", CostEvidenceType.DOCUMENT, 101L), null)));
            case "valid-seniors" -> validatePlan(CompanionType.SENIORS, TravelPace.RELAXED,
                    new BigDecimal("1000"), clearWeather(), List.of(poi(POI_A, false)), List.of(), List.of(), List.of(),
                    plan(item(9, 10, POI_A, cost("20", CostEvidenceType.AI_ESTIMATE, null), null)));
            case "budget-exceeded", "repair-twice-fails" -> repairOutcome(List.of(expensivePlan(), expensivePlan(), expensivePlan()));
            case "missing-start-date" -> requirementOutcome(null, START);
            case "missing-end-date" -> requirementOutcome(START, null);
            case "zero-budget" -> requirementOutcome(START, START, BigDecimal.ZERO);
            case "rain-outdoor-warning" -> validatePlan(CompanionType.FRIENDS, TravelPace.MODERATE,
                    new BigDecimal("1000"), rainyWeather(), List.of(poi(POI_A, true)), List.of(),
                    List.of(costFact(POI_A, "10", CostEvidenceType.FACT, null)), List.of(),
                    plan(item(9, 10, POI_A, cost("10", CostEvidenceType.FACT, null), null)));
            case "route-time-insufficient" -> validatePlan(CompanionType.FRIENDS, TravelPace.MODERATE,
                    new BigDecimal("1000"), clearWeather(), List.of(poi(POI_A, false), poi(POI_B, false)),
                    List.of(route(POI_A, POI_B, 180)), List.of(), List.of(),
                    plan(item(9, 10, POI_A, aiCost("10"), null),
                            item(10, 30, 11, 30, POI_B, aiCost("10"), TravelMode.TRANSIT)));
            case "knowledge-empty" -> citationOutcome(List.of(), List.of());
            case "place-provider-failure" -> providerFailure("place");
            case "route-provider-failure" -> providerFailure("route");
            case "weather-provider-failure" -> providerFailure("weather");
            case "forged-poi-id", "forged-poi-name-collision" -> validatePlan(CompanionType.FRIENDS,
                    TravelPace.MODERATE, new BigDecimal("1000"), clearWeather(), List.of(poi(POI_A, false)),
                    List.of(), List.of(), List.of(), plan(item(9, 10, "forged-poi", aiCost("10"), null)));
            case "forged-chunk-id" -> citationOutcome(List.of(999L), List.of(reference(101L)));
            case "invalid-chunk-value" -> citationOutcome(List.of(-1L), List.of(reference(101L)));
            case "invalid-json-then-valid" -> generationOutcome(List.of("not-json", VALID_MODEL_JSON));
            case "invalid-json-twice" -> generationOutcome(List.of("not-json", "[]"));
            case "repair-first-success" -> repairOutcome(List.of(expensivePlan(), inexpensivePlan()));
            case "repair-second-success" -> repairOutcome(List.of(expensivePlan(), expensivePlan(), inexpensivePlan()));
            case "preference-hit", "preference-explicit-override", "preference-disabled", "preference-cleared",
                    "preference-user-a", "preference-user-b", "preference-storage-unavailable" -> preferenceOutcome(fixture);
            case "five-day-boundary", "fifty-poi-boundary", "five-chunk-boundary",
                    "five-hundred-token-memory-boundary", "required-context-over-budget" -> contextOutcome(fixture);
            case "cache-hit", "cache-miss", "cache-failure-falls-open" -> cacheOutcome(fixture);
            case "empty-plan-metrics" -> validatePlan(CompanionType.FRIENDS, TravelPace.MODERATE,
                    new BigDecimal("1000"), clearWeather(), List.of(), List.of(), List.of(), List.of(),
                    new ItineraryPlan(CITY, START, START, List.of(new ItineraryDay(START, List.of())), BigDecimal.ZERO));
            default -> throw new IllegalArgumentException("unknown fixture: " + fixture);
        };
    }

    private Outcome validatePlan(CompanionType companion, TravelPace pace, BigDecimal budget,
                                 WeatherResult weather, List<ValidationPoiFact> places, List<RouteFact> routes,
                                 List<CostFact> costs, List<KnowledgeReference> references, ItineraryPlan plan) {
        TravelPlanningContext context = context(companion, pace, budget, weather, places, routes, costs, references);
        ValidationReport report = itineraryValidator.validate(context, plan);
        PlanMetrics metrics = metricsCalculator.calculate(plan, report, context.validationFacts(), 8);
        String error = report.errors().isEmpty()
                ? report.warnings().stream().findFirst().map(issue -> issue.code()).orElse(null)
                : report.errors().getFirst().code();
        return new Outcome(report.canProceed() ? "COMPLETED" : "NEEDS_ADJUSTMENT", error, 0, metrics,
                null, null, null, null);
    }

    private Outcome requirementOutcome(LocalDate start, LocalDate end) {
        return requirementOutcome(start, end, new BigDecimal("1000"));
    }

    private Outcome requirementOutcome(LocalDate start, LocalDate end, BigDecimal budget) {
        try {
            new RequirementValidator().validate(input(start, end, budget, true,
                    PreferenceValue.notProvided(), PreferenceValue.notProvided()));
            return Outcome.completed();
        } catch (RequirementValidator.ValidationException exception) {
            return new Outcome("FAILED", exception.code(), 0, null, null, null, null, null);
        }
    }

    private Outcome citationOutcome(List<Long> requested, List<KnowledgeReference> allowed) {
        CitationValidationResult result = new CitationValidator().validate(requested, allowed);
        return result.issues().isEmpty() ? Outcome.completed()
                : new Outcome("FAILED", result.issues().getFirst().code(), 0, null, null, null, null, null);
    }

    private Outcome generationOutcome(List<String> responses) {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn(responses.getFirst(), responses.size() > 1 ? responses.get(1) : responses.getFirst());
        LangChain4jItineraryGenerator generator = new LangChain4jItineraryGenerator(model);
        int attempts = 0;
        while (attempts < 2) {
            try {
                generator.generate(generationContext());
                return Outcome.completed();
            } catch (ItineraryGenerationException exception) {
                attempts++;
                if (attempts == 2) {
                    return new Outcome("FAILED", exception.code(), 0, null, null, null, null, null);
                }
            }
        }
        throw new IllegalStateException("bounded generation loop");
    }

    private Outcome repairOutcome(List<ItineraryPlan> versions) {
        TravelPlanningContext context = context(CompanionType.FRIENDS, TravelPace.MODERATE,
                new BigDecimal("50"), clearWeather(), List.of(poi(POI_A, false)), List.of(), List.of(), List.of());
        ValidationReport last = null;
        PlanMetrics metrics = null;
        for (int index = 0; index < versions.size(); index++) {
            ItineraryPlan version = versions.get(index);
            last = itineraryValidator.validate(context, version);
            metrics = metricsCalculator.calculate(version, last, context.validationFacts(), 8);
            if (last.canProceed()) {
                return new Outcome("COMPLETED", null, index, metrics, null, null, null, null);
            }
            if (index == 2 || index == versions.size() - 1) {
                return new Outcome("NEEDS_ADJUSTMENT", last.errors().getFirst().code(), index, metrics,
                        null, null, null, null);
            }
        }
        throw new IllegalStateException("empty repair fixture");
    }

    private Outcome preferenceOutcome(String fixture) {
        FakePreferenceRepository repository = new FakePreferenceRepository();
        repository.values.put(11, preference(11, TravelInterest.FOOD, TravelPace.RELAXED));
        repository.values.put(12, preference(12, TravelInterest.HISTORY, TravelPace.COMPACT));
        PreferenceMemoryService service = new PreferenceMemoryService(repository);
        boolean correct;
        String diagnostic = null;
        switch (fixture) {
            case "preference-hit" -> correct = service.merge(11, input()).snapshot().interests().contains(TravelInterest.FOOD);
            case "preference-explicit-override" -> {
                var merged = service.merge(11, input(START, START, new BigDecimal("1000"), true,
                        PreferenceValue.explicitValue(List.of("HISTORY")), PreferenceValue.explicitValue("COMPACT")));
                correct = merged.snapshot().interests().equals(Set.of(TravelInterest.HISTORY))
                        && merged.snapshot().pace() == TravelPace.COMPACT
                        && merged.snapshot().interestsSource() == PreferenceSource.EXPLICIT_REQUEST;
            }
            case "preference-disabled" -> {
                var merged = service.merge(11, input(START, START, new BigDecimal("1000"), false,
                        PreferenceValue.notProvided(), PreferenceValue.notProvided()));
                correct = repository.findCount == 0 && merged.snapshot().readStatus() == PreferenceReadStatus.NOT_REQUESTED;
            }
            case "preference-cleared" -> {
                service.clear(11);
                service.clear(11);
                correct = service.merge(11, input()).snapshot().interests().isEmpty();
            }
            case "preference-user-a" -> correct = service.merge(11, input()).snapshot().interests()
                    .equals(Set.of(TravelInterest.FOOD));
            case "preference-user-b" -> correct = service.merge(12, input()).snapshot().interests()
                    .equals(Set.of(TravelInterest.HISTORY));
            case "preference-storage-unavailable" -> {
                repository.failRead = true;
                var merged = service.merge(11, input());
                correct = merged.snapshot().readStatus() == PreferenceReadStatus.FAILED
                        && merged.snapshot().interests().isEmpty();
                diagnostic = "PREFERENCE_STORAGE_FAILED";
            }
            default -> throw new IllegalArgumentException(fixture);
        }
        return new Outcome(correct ? "COMPLETED" : "FAILED", correct ? diagnostic : "PREFERENCE_EVALUATION_FAILED",
                0, null, correct, fixture.startsWith("preference-user-") ? correct : null, null, null);
    }

    private Outcome contextOutcome(String fixture) {
        PromptContextInput input = largeContextInput();
        ContextBudgetProperties limits = "required-context-over-budget".equals(fixture)
                ? new ContextBudgetProperties(5000, 300, 100, 100, 100, 100, 50, 20, 5)
                : CONTEXT_LIMITS;
        PromptContextAssembler assembler = new PromptContextAssembler(limits, new ConservativeTokenEstimator());
        try {
            PromptContext first = assembler.assemble(input);
            PromptContext second = assembler.assemble(input);
            boolean correct = first.equals(second) && first.budgetReport().withinBudget()
                    && first.budgetReport().originalPoiCount() == 50
                    && first.budgetReport().retainedPoiCount() <= 20
                    && first.budgetReport().originalKnowledgeChunkCount() == 5
                    && first.budgetReport().retainedKnowledgeChunkCount() <= 5
                    && first.budgetReport().memoryTokens() <= 500
                    && input.planningContext().resolvedRequirement().requirement().durationDays() == 5;
            return new Outcome(correct ? "COMPLETED" : "FAILED",
                    correct ? null : "CONTEXT_BOUNDARY_FAILED", 0, null, null, null, null, correct);
        } catch (ContextBudgetExceededException exception) {
            return new Outcome("FAILED", exception.code(), 0, null, null, null, null, true);
        }
    }

    private Outcome cacheOutcome(String fixture) {
        ProviderFixture providers = new ProviderFixture();
        CacheManager caches = "cache-failure-falls-open".equals(fixture)
                ? new ThrowingCacheManager() : new ConcurrentMapCacheManager("poi", "geocode", "route", "weather");
        TravelToolDispatcher dispatcher = new TravelToolDispatcher(
                new ToolPlanValidator(), providers, providers, providers, caches);
        ToolPlan plan = new ToolPlan(CITY, List.of(new ToolAction("SEARCH_POI",
                Map.of("city", CITY, "keyword", "虚构关键字", "limit", 2))));
        ToolExecutionBundle first = dispatcher.execute(new ToolExecutionPolicy(5), plan);
        boolean correct;
        String diagnostic = null;
        if ("cache-hit".equals(fixture)) {
            ToolExecutionBundle second = dispatcher.execute(new ToolExecutionPolicy(5), plan);
            correct = !first.executions().getFirst().cacheHit() && second.executions().getFirst().cacheHit()
                    && providers.placeCalls == 1;
        } else if ("cache-miss".equals(fixture)) {
            correct = !first.executions().getFirst().cacheHit() && providers.placeCalls == 1;
        } else {
            correct = first.executions().getFirst().result().success() && providers.placeCalls == 1;
            diagnostic = "CACHE_UNAVAILABLE";
        }
        return new Outcome(correct ? "COMPLETED" : "FAILED", correct ? diagnostic : "CACHE_BEHAVIOR_FAILED",
                0, null, null, null, correct, null);
    }

    private Outcome providerFailure(String kind) {
        ProviderFixture providers = new ProviderFixture();
        providers.failureKind = kind;
        TravelToolDispatcher dispatcher = new TravelToolDispatcher(new ToolPlanValidator(), providers, providers,
                providers, new ConcurrentMapCacheManager("poi", "geocode", "route", "weather"));
        ToolAction action = switch (kind) {
            case "place" -> new ToolAction("SEARCH_POI", Map.of("city", CITY, "keyword", "虚构", "limit", 2));
            case "route" -> new ToolAction("TRANSIT_ROUTE", Map.of("city", CITY, "origin", placeCandidate(POI_A),
                    "destination", placeCandidate(POI_B), "mode", "TRANSIT"));
            case "weather" -> new ToolAction("WEATHER_FORECAST", Map.of("city", CITY,
                    "location", new GeoPoint(120.1, 30.1, CoordinateSystem.WGS84), "date", START));
            default -> throw new IllegalArgumentException(kind);
        };
        ToolExecutionBundle bundle = dispatcher.execute(new ToolExecutionPolicy(5), new ToolPlan(CITY, List.of(action)));
        String providerCode = bundle.executions().getFirst().result().errorCode();
        if ("weather".equals(kind)) {
            return new Outcome("COMPLETED", "WEATHER_DATA_MISSING", 0, null, null, null, null, null);
        }
        return new Outcome("FAILED", providerCode, 0, null, null, null, null, null);
    }

    private TravelPlanningContext context(CompanionType companion, TravelPace pace, BigDecimal budget,
                                          WeatherResult weather, List<ValidationPoiFact> places,
                                          List<RouteFact> routes, List<CostFact> costs,
                                          List<KnowledgeReference> references) {
        TravelRequirement requirement = new TravelRequirement(CITY, START, START, 2, budget, companion,
                Set.of(), pace, null, TravelMode.TRANSIT, null);
        AppliedPreferenceSnapshot snapshot = new AppliedPreferenceSnapshot(Set.of(), pace, TravelMode.TRANSIT, null,
                PreferenceSource.NOT_PROVIDED, PreferenceSource.EXPLICIT_REQUEST,
                PreferenceSource.EXPLICIT_REQUEST, PreferenceSource.NOT_PROVIDED,
                false, PreferenceReadStatus.NOT_REQUESTED);
        return new TravelPlanningContext(new ResolvedTravelRequirement(requirement, snapshot),
                new ItineraryValidationFacts(places, routes, costs, weather, 15, references));
    }

    private PromptContextInput largeContextInput() {
        List<ValidationPoiFact> places = new ArrayList<>();
        List<RouteFact> routes = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            String id = "fixture-poi-" + String.format(java.util.Locale.ROOT, "%03d", index);
            PlaceCandidate candidate = new PlaceCandidate(id, "虚构地点" + index, "虚构地址" + index,
                    new GeoPoint(120 + index / 1000.0, 30, CoordinateSystem.GCJ02));
            places.add(new ValidationPoiFact(candidate, index, index < 2, index % 2 == 0,
                    OpeningHoursFact.reliable(LocalTime.of(8, 0), LocalTime.of(20, 0))));
            if (index > 0) routes.add(new RouteFact("fixture-poi-" + String.format(java.util.Locale.ROOT, "%03d", index - 1),
                    id, new TravelSegment(TravelMode.TRANSIT, 1000L + index, 20L, true)));
        }
        List<KnowledgeReference> references = new ArrayList<>();
        for (int index = 1; index <= 5; index++) references.add(reference(100L + index));
        List<WeatherResult.DailyForecast> weather = new ArrayList<>();
        for (int day = 0; day < 5; day++) weather.add(new WeatherResult.DailyForecast(
                START.plusDays(day), 15, 25, 10, 0));
        TravelRequirement requirement = new TravelRequirement(CITY, START, START.plusDays(4), 2,
                new BigDecimal("3000"), CompanionType.FAMILY,
                EnumSet.of(TravelInterest.FOOD, TravelInterest.HISTORY), TravelPace.MODERATE,
                "显式虚构住宿", TravelMode.TRANSIT, "显式需求");
        AppliedPreferenceSnapshot snapshot = new AppliedPreferenceSnapshot(
                EnumSet.of(TravelInterest.FOOD, TravelInterest.HISTORY), TravelPace.MODERATE,
                TravelMode.TRANSIT, "虚构记忆住宿偏好".repeat(120),
                PreferenceSource.SAVED_PREFERENCE, PreferenceSource.EXPLICIT_REQUEST,
                PreferenceSource.EXPLICIT_REQUEST, PreferenceSource.SAVED_PREFERENCE,
                true, PreferenceReadStatus.SUCCEEDED);
        ItineraryValidationFacts facts = new ItineraryValidationFacts(places, routes, List.of(),
                new WeatherResult(weather), 15, references);
        return new PromptContextInput(new TravelPlanningContext(new ResolvedTravelRequirement(requirement, snapshot), facts),
                List.of(), "虚构可选说明".repeat(2000));
    }

    private PromptContext generationContext() {
        ContextBudgetReport report = new ContextBudgetReport(100, 20, 20, 0, 20, 0, 0, 0,
                1, 1, 0, 0, List.of(), List.of(), true);
        return new PromptContext("system schema", "hard constraints", List.of(),
                List.of("{\"type\":\"POI\",\"poiId\":\"fixture-poi-a\"}"), List.of(), List.of(), List.of(), report,
                Set.of(POI_A), Set.of());
    }

    private static TravelRequirementInput input() {
        return input(START, START, new BigDecimal("1000"), true,
                PreferenceValue.notProvided(), PreferenceValue.notProvided());
    }

    private static TravelRequirementInput input(LocalDate start, LocalDate end, BigDecimal budget,
                                                boolean useSaved, PreferenceValue<List<String>> interests,
                                                PreferenceValue<String> pace) {
        return new TravelRequirementInput(CITY, start, end, 2, budget, "FRIENDS", interests, pace,
                PreferenceValue.notProvided(), PreferenceValue.notProvided(), null, useSaved);
    }

    private static UserTravelPreference preference(int userId, TravelInterest interest, TravelPace pace) {
        return new UserTravelPreference(userId, EnumSet.of(interest), pace, TravelMode.TRANSIT,
                "虚构住宿偏好", Instant.parse("2026-10-14T00:00:00Z"));
    }

    private static ValidationPoiFact poi(String id, boolean outdoor) {
        return new ValidationPoiFact(placeCandidate(id), 1, true, outdoor,
                OpeningHoursFact.reliable(LocalTime.of(8, 0), LocalTime.of(20, 0)));
    }

    private static PlaceCandidate placeCandidate(String id) {
        return new PlaceCandidate(id, "虚构地点" + id, "虚构地址",
                new GeoPoint(120.1, 30.1, CoordinateSystem.GCJ02));
    }

    private static RouteFact route(String from, String to, long minutes) {
        return new RouteFact(from, to, new TravelSegment(TravelMode.TRANSIT, 1000L, minutes, true));
    }

    private static CostFact costFact(String poiId, String amount, CostEvidenceType type, Long chunkId) {
        return new CostFact(poiId, cost(amount, type, chunkId));
    }

    private static CostEstimate cost(String amount, CostEvidenceType type, Long chunkId) {
        BigDecimal value = new BigDecimal(amount);
        return CostEstimate.known(value, value, type, chunkId);
    }

    private static CostEstimate aiCost(String amount) {
        return cost(amount, CostEvidenceType.AI_ESTIMATE, null);
    }

    private static ItineraryItem item(int startHour, int endHour, String poiId,
                                      CostEstimate cost, TravelMode mode) {
        return item(startHour, 0, endHour, 0, poiId, cost, mode);
    }

    private static ItineraryItem item(int startHour, int startMinute, int endHour, int endMinute,
                                      String poiId, CostEstimate cost, TravelMode mode) {
        return new ItineraryItem(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute),
                poiId, "虚构活动", "fixture", cost, mode);
    }

    private static ItineraryPlan plan(ItineraryItem... items) {
        BigDecimal total = java.util.Arrays.stream(items).filter(item -> item.cost().isKnown())
                .map(item -> item.cost().maximum()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ItineraryPlan(CITY, START, START, List.of(new ItineraryDay(START, List.of(items))), total);
    }

    private static ItineraryPlan expensivePlan() {
        return plan(item(9, 10, POI_A, aiCost("100"), null));
    }

    private static ItineraryPlan inexpensivePlan() {
        return plan(item(9, 10, POI_A, aiCost("20"), null));
    }

    private static WeatherResult clearWeather() {
        return new WeatherResult(List.of(new WeatherResult.DailyForecast(START, 15, 25, 10, 0)));
    }

    private static WeatherResult rainyWeather() {
        return new WeatherResult(List.of(new WeatherResult.DailyForecast(START, 15, 20, 90, 20)));
    }

    private static KnowledgeReference reference(long chunkId) {
        return new KnowledgeReference(1L, chunkId, Math.toIntExact(chunkId), "虚构资料", CITY,
                "虚构发布机构", URI.create("https://example.test/fixture/" + chunkId),
                LocalDate.of(2026, 1, 1), KnowledgeDocumentType.ATTRACTION_GUIDE,
                "虚构知识切片，仅用于离线评测。", 0.9);
    }

    private Dataset loadDataset() throws Exception {
        JsonNode root = mapper.readTree(new ClassPathResource("evaluation/travel-cases.json").getInputStream());
        List<EvaluationCase> cases = new ArrayList<>();
        for (JsonNode node : root.path("cases")) cases.add(mapper.treeToValue(node, EvaluationCase.class));
        return new Dataset(root.path("datasetId").asText(), root.path("fixtureDataClass").asText(), List.copyOf(cases));
    }

    private static MetricResult metric(String name, int numerator, int denominator, String formula) {
        return new MetricResult(name, numerator, denominator, formula);
    }

    private record Dataset(String datasetId, String fixtureDataClass, List<EvaluationCase> cases) { }

    private record EvaluationCase(String id, String scenarioType, String fixtureRef,
                                  String expectedTaskStatus, String expectedErrorCode,
                                  List<String> rulesOrMetrics) { }

    private record EvaluationRun(List<MetricResult> metrics, List<CaseResult> results) { }

    private record Outcome(String status, String errorCode, int repairAttempts, PlanMetrics metrics,
                           Boolean preferenceCorrect, Boolean isolationCorrect, Boolean cacheCorrect,
                           Boolean contextCorrect) {
        private static Outcome completed() {
            return new Outcome("COMPLETED", null, 0, null, null, null, null, null);
        }
    }

    private static final class Counts {
        int fabricatedPoiBlocked, fabricatedPoiAttempts;
        int citationNumerator, citationDenominator, routeNumerator, routeDenominator, ruleNumerator, ruleDenominator;
        int repairSucceeded, repairRequired, repairAttempts;
        int preferenceCorrect, preferenceCases, isolationCorrect, isolationCases;
        int cacheCorrect, cacheCases, statusCorrect, totalCases, contextCorrect, contextCases;
        int emptyCitationNumerator, emptyCitationDenominator;

        void add(EvaluationCase testCase, Outcome outcome, boolean passed) {
            totalCases++;
            if (passed) statusCorrect++;
            if ("FABRICATED_POI_ID".equals(testCase.scenarioType())) {
                fabricatedPoiAttempts++;
                if ("UNKNOWN_POI".equals(outcome.errorCode())) fabricatedPoiBlocked++;
            }
            if (outcome.metrics() != null) {
                citationNumerator += outcome.metrics().sourceCoverage().numerator();
                citationDenominator += outcome.metrics().sourceCoverage().denominator();
                routeNumerator += outcome.metrics().routeCoverage().numerator();
                routeDenominator += outcome.metrics().routeCoverage().denominator();
                ruleNumerator += outcome.metrics().rulePassRate().numerator();
                ruleDenominator += outcome.metrics().rulePassRate().denominator();
                if ("ZERO_DENOMINATOR_METRICS".equals(testCase.scenarioType())) {
                    emptyCitationNumerator += outcome.metrics().sourceCoverage().numerator();
                    emptyCitationDenominator += outcome.metrics().sourceCoverage().denominator();
                }
            }
            if (testCase.scenarioType().startsWith("REPAIR_")) {
                repairRequired++;
                repairAttempts += outcome.repairAttempts();
                if ("COMPLETED".equals(outcome.status())) repairSucceeded++;
            }
            if (outcome.preferenceCorrect() != null) {
                preferenceCases++;
                if (outcome.preferenceCorrect()) preferenceCorrect++;
            }
            if (outcome.isolationCorrect() != null) {
                isolationCases++;
                if (outcome.isolationCorrect()) isolationCorrect++;
            }
            if (outcome.cacheCorrect() != null) {
                cacheCases++;
                if (outcome.cacheCorrect()) cacheCorrect++;
            }
            if (outcome.contextCorrect() != null) {
                contextCases++;
                if (outcome.contextCorrect()) contextCorrect++;
            }
        }
    }

    private static final class FakePreferenceRepository implements UserTravelPreferenceRepository {
        private final Map<Integer, UserTravelPreference> values = new HashMap<>();
        private int findCount;
        private boolean failRead;

        @Override public Optional<UserTravelPreference> findByUserId(Integer userId) {
            findCount++;
            if (failRead) throw new IllegalStateException("synthetic storage outage");
            return Optional.ofNullable(values.get(userId));
        }

        @Override public void replace(Integer userId, UserTravelPreference preference) {
            values.put(userId, preference);
        }

        @Override public void deleteByUserId(Integer userId) {
            values.remove(userId);
        }
    }

    private static final class ProviderFixture implements PlaceProvider, RouteProvider, WeatherProvider {
        private int placeCalls;
        private String failureKind;

        @Override public ToolResult<List<PlaceCandidate>> search(String city, String keyword, int limit) {
            placeCalls++;
            return "place".equals(failureKind)
                    ? ToolResult.failure("fake-place", RETRIEVED_AT, "PLACE_PROVIDER_UNAVAILABLE", "synthetic failure")
                    : ToolResult.success("fake-place", RETRIEVED_AT, List.of(placeCandidate(POI_A)));
        }

        @Override public ToolResult<PlaceCandidate> geocode(String city, String address) {
            placeCalls++;
            return ToolResult.success("fake-place", RETRIEVED_AT, placeCandidate(POI_A));
        }

        @Override public ToolResult<TravelSegment> route(String city, PlaceCandidate from,
                                                         PlaceCandidate to, TravelMode mode) {
            return "route".equals(failureKind)
                    ? ToolResult.failure("fake-route", RETRIEVED_AT, "ROUTE_PROVIDER_UNAVAILABLE", "synthetic failure")
                    : ToolResult.success("fake-route", RETRIEVED_AT, new TravelSegment(mode, 1000L, 20L, true));
        }

        @Override public ToolResult<WeatherResult> forecast(GeoPoint location, LocalDate startDate, LocalDate endDate) {
            return "weather".equals(failureKind)
                    ? ToolResult.failure("fake-weather", RETRIEVED_AT, "WEATHER_PROVIDER_UNAVAILABLE", "synthetic failure")
                    : ToolResult.success("fake-weather", RETRIEVED_AT, clearWeather());
        }
    }

    private static final class ThrowingCacheManager implements CacheManager {
        @Override public Cache getCache(String name) {
            throw new IllegalStateException("synthetic cache outage");
        }

        @Override public Collection<String> getCacheNames() {
            return List.of();
        }
    }
}
