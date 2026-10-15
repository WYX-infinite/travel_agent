package com.wyx.travelagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalService;
import com.wyx.travelagent.planning.PlanningOutcome;
import com.wyx.travelagent.planning.TravelPlanningCommand;
import com.wyx.travelagent.planning.TravelPlanningOrchestrator;
import com.wyx.travelagent.planning.api.TravelPlanningRequest;
import com.wyx.travelagent.planning.memory.PreferenceMemoryService;
import com.wyx.travelagent.planning.memory.PreferenceMergeResult;
import com.wyx.travelagent.planning.model.CostEvidenceType;
import com.wyx.travelagent.planning.model.ResolvedTravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirementInput;
import com.wyx.travelagent.planning.task.PendingTask;
import com.wyx.travelagent.planning.task.PlanningTask;
import com.wyx.travelagent.planning.task.PlanningTaskRepository;
import com.wyx.travelagent.planning.task.PlanningTaskStatus;
import com.wyx.travelagent.planning.validation.RequirementValidator;
import com.wyx.travelagent.tools.amap.AmapProperties;
import com.wyx.travelagent.tools.amap.AmapRouteProvider;
import com.wyx.travelagent.tools.dispatch.ToolAction;
import com.wyx.travelagent.tools.dispatch.ToolExecutionPolicy;
import com.wyx.travelagent.tools.dispatch.ToolPlan;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.TravelMode;
import com.wyx.travelagent.tools.port.PlaceProvider;
import com.wyx.travelagent.tools.port.RouteProvider;
import com.wyx.travelagent.tools.port.WeatherProvider;
import com.wyx.travelagent.trace.PersistedPlanningTrace;
import com.wyx.travelagent.trace.PlanningStep;
import com.wyx.travelagent.trace.PlanningTraceService;
import com.wyx.travelagent.trace.TraceStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicit real-service evidence test. Ordinary Surefire discovery never selects this IT class.
 * The generated report contains only counts, stable codes, model names and timing summaries.
 * Run only after approving real DashScope, Amap and Open-Meteo calls with:
 * TRAVEL_REAL_SMOKE_ENABLED=true .\mvnw.cmd -Preal-smoke verify
 */
@Tag("real-smoke")
@EnabledIfEnvironmentVariable(named = "TRAVEL_REAL_SMOKE_ENABLED", matches = "(?i)true")
@ActiveProfiles({"local", "real-smoke"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TravelAgentRealSmokeIT {
    private static final ZoneId CLIENT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String CITY = "杭州";
    private static final String CITATION_QUERY = "浙江省博物馆 免费参观 开放时间 官方";
    private static final String REPAIR_QUERY = "杭州两日文化行程 博物馆 开放时间 官方指南";

    @Autowired private PlaceProvider placeProvider;
    @Autowired private RouteProvider routeProvider;
    @Autowired private WeatherProvider weatherProvider;
    @Autowired private KnowledgeRetrievalService knowledgeRetrievalService;
    @Autowired private PreferenceMemoryService preferenceMemoryService;
    @Autowired private RequirementValidator requirementValidator;
    @Autowired private TravelPlanningOrchestrator orchestrator;
    @Autowired private PlanningTaskRepository taskRepository;
    @Autowired private PlanningTraceService traceService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RedisConnectionFactory redisConnectionFactory;
    @Autowired private Environment environment;
    @Autowired private Clock clock;

    @Test
    void verifiesRealPlanningEvidenceAndWritesOnlySanitizedEvidence() throws Exception {
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        boolean redisAvailable = redisAvailable();

        Integer userId = jdbcTemplate.queryForObject(
                "SELECT id FROM user ORDER BY id LIMIT 1", Integer.class);
        assertThat(userId).as("real smoke requires one existing local user row").isPositive();

        long connectivityStarted = System.nanoTime();
        var placeResult = placeProvider.search(CITY, "博物馆", 3);
        assertThat(placeResult.success()).isTrue();
        List<PlaceCandidate> places = Objects.requireNonNull(placeResult.data());
        assertThat(places).hasSizeGreaterThanOrEqualTo(2);
        var routeResult = routeProvider.route(CITY, places.get(0), places.get(1), TravelMode.TRANSIT);
        assertThat(routeResult.success()).isTrue();
        LocalDate startDate = LocalDate.now(CLIENT_ZONE).plusDays(7);
        var weatherResult = weatherProvider.forecast(
                places.getFirst().location(), startDate, startDate.plusDays(1));
        assertThat(weatherResult.success()).isTrue();
        var retrieval = knowledgeRetrievalService.retrieve(CITATION_QUERY, CITY);
        assertThat(retrieval.allowedReferences())
                .as("preload at least one ACTIVE Hangzhou knowledge document before real smoke")
                .isNotEmpty();
        assertThat(retrieval.allowedReferences())
                .as("the citation scenario requires an ACTIVE Zhejiang Museum knowledge chunk")
                .anyMatch(reference -> reference.title().contains("浙江省博物馆"));
        long connectivityLatencyMs = elapsedMillis(connectivityStarted);

        var injectedRouteFailure = new AmapRouteProvider(
                RestClient.create("http://127.0.0.1:1"),
                new AmapProperties("http://127.0.0.1:1", "smoke-test-redacted",
                        Duration.ofMillis(250), Duration.ofMillis(250)),
                Clock.systemUTC())
                .route(CITY, places.get(0), places.get(1), TravelMode.TRANSIT);
        assertThat(injectedRouteFailure.success()).isFalse();
        assertThat(injectedRouteFailure.errorCode())
                .isIn("PROVIDER_TIMEOUT", "PROVIDER_UNAVAILABLE", "INVALID_PROVIDER_RESPONSE");

        List<ScenarioEvidence> scenarios = new ArrayList<>();
        scenarios.add(runScenario(userId, "HANGZHOU_TWO_DAY_WITH_DOCUMENT",
                normalRequest(startDate), CITATION_QUERY, "浙江省博物馆"));
        if (scenarios.stream().noneMatch(evidence -> evidence.repairAttempts() > 0)) {
            scenarios.add(runScenario(userId, "AUTO_REPAIR_TRIGGER",
                    repairRequest(startDate.plusDays(14)), REPAIR_QUERY, "景点 博物馆"));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("evaluationMode", "REAL_SMOKE");
        report.put("executedAt", Instant.now(clock).toString());
        report.put("services", Map.of(
                "chatModel", property("langchain4j.open-ai.chat-model.model-name", "unknown"),
                "embeddingModel", property("langchain4j.open-ai.embedding-model.model-name", "unknown"),
                "mapProvider", "Amap Web Service v3",
                "weatherProvider", "Open-Meteo forecast API"));
        report.put("connectivity", Map.of(
                "mysql", true,
                "redis", redisAvailable,
                "placeCount", places.size(),
                "routeVerified", routeResult.data() != null && routeResult.data().verified(),
                "weatherDayCount", weatherResult.data() == null
                        ? 0 : weatherResult.data().dailyForecasts().size(),
                "knowledgeReferenceCount", retrieval.allowedReferences().size(),
                "latencyMs", connectivityLatencyMs));
        report.put("routeFailureEvidence", Map.of(
                "injection", "unreachable loopback endpoint",
                "externalProviderCalled", false,
                "success", false,
                "errorCode", injectedRouteFailure.errorCode()));
        report.put("scenarios", scenarios);
        report.put("totals", totals(scenarios));
        report.put("limitations", List.of(
                "This is a small approved smoke run, not a statistical sample or SLA measurement.",
                "Redis availability is recorded but does not block the smoke run because cache failures degrade to direct provider calls.",
                "The route failure case is a controlled unreachable-endpoint injection, not a claim about Amap availability.",
                "The report omits prompts, model responses, credentials, tokens, usernames, task ids and plan ids."));

        Path output = Path.of("target", "evaluation", "travel-agent-real-smoke.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new ObjectMapper().findAndRegisterModules()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(report) + System.lineSeparator(),
                StandardCharsets.UTF_8);

        assertThat(scenarios.getFirst().taskStatus()).isEqualTo(PlanningTaskStatus.COMPLETED.name());
        assertThat(scenarios.getFirst().documentCitationCount())
                .as("the completed itinerary must persist at least one allowed DOCUMENT chunk reference")
                .isPositive();
        assertThat(scenarios).anyMatch(evidence -> evidence.repairAttempts() > 0);
    }

    private ScenarioEvidence runScenario(
            int userId,
            String scenario,
            TravelPlanningRequest request,
            String knowledgeQuery,
            String poiKeyword) {
        TravelRequirementInput input = request.toInput();
        PreferenceMergeResult merged = preferenceMemoryService.merge(userId, input);
        TravelRequirement requirement = requirementValidator.validate(merged.mergedInput());
        ResolvedTravelRequirement resolved = new ResolvedTravelRequirement(requirement, merged.snapshot());
        PlanningTask task = taskRepository.createPendingIfNoActive(new PendingTask(
                        userId, input, resolved, merged.snapshot(), Instant.now(clock)))
                .orElseThrow(() -> new IllegalStateException("REAL_SMOKE_ACTIVE_TASK_EXISTS"));
        assertThat(taskRepository.markRunning(task.id(), Instant.now(clock))).isTrue();

        ToolPlan toolPlan = new ToolPlan(CITY, List.of(new ToolAction("SEARCH_POI",
                Map.of("city", CITY, "keyword", poiKeyword, "limit", 10))));
        long started = System.nanoTime();
        PlanningOutcome outcome;
        try {
            outcome = orchestrator.plan(new TravelPlanningCommand(
                    task.id(), userId, input, knowledgeQuery,
                    new ToolExecutionPolicy(5), toolPlan, resolved));
        } catch (RuntimeException exception) {
            taskRepository.finish(task.id(), PlanningTaskStatus.FAILED, null,
                    "REAL_SMOKE_EXECUTION_FAILED", "real smoke planning failed", Instant.now(clock));
            throw exception;
        }
        long latencyMs = elapsedMillis(started);

        PlanningTaskStatus taskStatus = PlanningTaskStatus.fromOutcome(outcome);
        String failureCode = taskStatus == PlanningTaskStatus.FAILED
                ? Objects.requireNonNullElse(outcome.failureCode(), "REAL_SMOKE_EXECUTION_FAILED") : null;
        String failureReason = taskStatus == PlanningTaskStatus.FAILED
                ? "real smoke planning failed" : null;
        assertThat(taskRepository.finish(task.id(), taskStatus, outcome.planId(),
                failureCode, failureReason, Instant.now(clock))).isTrue();
        PlanningTask stored = taskRepository.findOwned(task.id(), userId).orElseThrow();

        List<PersistedPlanningTrace> traces = traceService.findAfter(task.id(), 0, 200);
        TraceAggregate aggregate = aggregate(traces);
        int documentCitations = outcome.plan() == null ? 0 : outcome.plan().days().stream()
                .flatMap(day -> day.items().stream())
                .map(item -> item.cost())
                .filter(cost -> cost.evidenceType() == CostEvidenceType.DOCUMENT
                        && cost.knowledgeChunkId() != null)
                .mapToInt(ignored -> 1)
                .sum();
        List<String> resultCodes = outcome.validationReport() == null ? List.of()
                : outcome.validationReport().issues().stream()
                .map(issue -> issue.code()).distinct().sorted().toList();

        return new ScenarioEvidence(
                scenario,
                stored.status().name(),
                outcome.version(),
                latencyMs,
                aggregate.modelCallCount(),
                aggregate.actualInputTokens(),
                aggregate.actualOutputTokens(),
                aggregate.providerCallCounts(),
                aggregate.cacheHitCount(),
                aggregate.repairAttempts(),
                documentCitations,
                resultCodes,
                outcome.failureCode());
    }

    private TravelPlanningRequest normalRequest(LocalDate startDate) {
        return new TravelPlanningRequest(CITY, startDate, startDate.plusDays(1),
                2, new java.math.BigDecimal("3000"), "COUPLE",
                List.of("HISTORY", "FOOD"), "MODERATE", "西湖附近", "TRANSIT",
                "第一天必须安排浙江省博物馆；该活动的免费参观费用事实必须使用本次"
                        + " KNOWLEDGE_REFERENCES 中浙江省博物馆条目的 DOCUMENT evidenceType"
                        + " 和对应 chunkId，不得改用 AI_ESTIMATE", false);
    }

    private TravelPlanningRequest repairRequest(LocalDate startDate) {
        return new TravelPlanningRequest(CITY, startDate, startDate.plusDays(1),
                1, new java.math.BigDecimal("500"), "SOLO",
                List.of("HISTORY"), "RELAXED", "西湖附近", "WALKING",
                "每天安排五个不同景点，每个活动至少持续两小时", false);
    }

    private TraceAggregate aggregate(List<PersistedPlanningTrace> traces) {
        int cacheHits = 0;
        int repairAttempts = 0;
        int structureRetries = 0;
        int modelEvents = 0;
        int actualInputTokens = 0;
        int actualOutputTokens = 0;
        Map<String, Integer> providerCalls = new LinkedHashMap<>();
        providerCalls.put("place", 0);
        providerCalls.put("route", 0);
        providerCalls.put("weather", 0);
        for (PersistedPlanningTrace persisted : traces) {
            var event = persisted.event();
            var details = event.details();
            cacheHits += details.cacheHitCount();
            repairAttempts = Math.max(repairAttempts, details.repairAttempt());
            structureRetries = Math.max(structureRetries, details.structureRetryCount());
            if ((event.step() == PlanningStep.GENERATION || event.step() == PlanningStep.REPAIR)
                    && event.status() != TraceStatus.FAILED) {
                modelEvents++;
            }
            actualInputTokens += Objects.requireNonNullElse(details.actualInputTokens(), 0);
            actualOutputTokens += Objects.requireNonNullElse(details.actualOutputTokens(), 0);
            details.providerCallCounts().forEach((key, value) -> providerCalls.merge(key, value, Integer::sum));
        }
        return new TraceAggregate(modelEvents + structureRetries, actualInputTokens,
                actualOutputTokens, Map.copyOf(providerCalls), cacheHits, repairAttempts);
    }

    private Map<String, Object> totals(List<ScenarioEvidence> scenarios) {
        Map<String, Integer> providerCalls = new LinkedHashMap<>();
        providerCalls.put("place", 1);
        providerCalls.put("route", 1);
        providerCalls.put("weather", 1);
        scenarios.forEach(scenario -> scenario.providerCallCounts()
                .forEach((key, value) -> providerCalls.merge(key, value, Integer::sum)));
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("scenarioCount", scenarios.size());
        totals.put("modelCallCount", scenarios.stream().mapToInt(ScenarioEvidence::modelCallCount).sum());
        totals.put("providerCallCounts", providerCalls);
        totals.put("latencyMs", scenarios.stream().mapToLong(ScenarioEvidence::latencyMs).sum());
        totals.put("cacheHitCount", scenarios.stream().mapToInt(ScenarioEvidence::cacheHitCount).sum());
        totals.put("repairAttempts", scenarios.stream().mapToInt(ScenarioEvidence::repairAttempts).sum());
        totals.put("documentCitationCount", scenarios.stream()
                .mapToInt(ScenarioEvidence::documentCitationCount).sum());
        return totals;
    }

    private String property(String name, String fallback) {
        return Objects.requireNonNullElse(environment.getProperty(name), fallback);
    }

    private boolean redisAvailable() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static long elapsedMillis(long started) {
        return Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
    }

    private record TraceAggregate(
            int modelCallCount,
            int actualInputTokens,
            int actualOutputTokens,
            Map<String, Integer> providerCallCounts,
            int cacheHitCount,
            int repairAttempts) {
    }

    private record ScenarioEvidence(
            String scenario,
            String taskStatus,
            int versionCount,
            long latencyMs,
            int modelCallCount,
            int actualInputTokens,
            int actualOutputTokens,
            Map<String, Integer> providerCallCounts,
            int cacheHitCount,
            int repairAttempts,
            int documentCitationCount,
            List<String> resultCodes,
            String failureCode) {
    }
}
