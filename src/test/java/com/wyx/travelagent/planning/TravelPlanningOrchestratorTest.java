package com.wyx.travelagent.planning;

import com.wyx.travelagent.planning.context.ConservativeTokenEstimator;
import com.wyx.travelagent.planning.context.ContextBudgetProperties;
import com.wyx.travelagent.planning.context.PromptContextAssembler;
import com.wyx.travelagent.planning.generation.ItineraryGenerationException;
import com.wyx.travelagent.planning.generation.ItineraryGenerationResult;
import com.wyx.travelagent.planning.generation.ItineraryGenerator;
import com.wyx.travelagent.planning.generation.ModelTokenUsage;
import com.wyx.travelagent.planning.memory.PreferenceMemoryService;
import com.wyx.travelagent.planning.memory.UserTravelPreferenceRepository;
import com.wyx.travelagent.planning.metrics.PlanMetricsCalculator;
import com.wyx.travelagent.planning.model.CompanionType;
import com.wyx.travelagent.planning.model.CostEstimate;
import com.wyx.travelagent.planning.model.CostStatus;
import com.wyx.travelagent.planning.model.ItineraryDay;
import com.wyx.travelagent.planning.model.ItineraryItem;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.ItineraryValidationFacts;
import com.wyx.travelagent.planning.model.OpeningHoursFact;
import com.wyx.travelagent.planning.model.PreferenceValue;
import com.wyx.travelagent.planning.model.TravelRequirementInput;
import com.wyx.travelagent.planning.model.ValidationPoiFact;
import com.wyx.travelagent.planning.persistence.TravelPlanRepository;
import com.wyx.travelagent.planning.persistence.TravelPlanStatus;
import com.wyx.travelagent.planning.preparation.PlanningPreparationException;
import com.wyx.travelagent.planning.preparation.PlanningPreparationGateway;
import com.wyx.travelagent.planning.preparation.PlanningPreparationResult;
import com.wyx.travelagent.planning.repair.ItineraryRepairer;
import com.wyx.travelagent.planning.repair.RepairExecution;
import com.wyx.travelagent.planning.validation.ItineraryValidator;
import com.wyx.travelagent.planning.validation.RequirementValidator;
import com.wyx.travelagent.tools.dispatch.ToolExecutionPolicy;
import com.wyx.travelagent.tools.dispatch.ToolPlan;
import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.trace.PlanningTraceEvent;
import com.wyx.travelagent.trace.PlanningTraceRepository;
import com.wyx.travelagent.trace.PlanningTraceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TravelPlanningOrchestratorTest {
    private QueueGenerator generator;
    private QueueRepairer repairer;
    private RecordingPlanRepository plans;
    private RecordingTraceRepository traces;
    private PlanningPreparationGateway preparation;

    @BeforeEach
    void setUp() {
        generator = new QueueGenerator();
        repairer = new QueueRepairer();
        plans = new RecordingPlanRepository();
        traces = new RecordingTraceRepository();
        preparation = (requirement, query, policy, plan) ->
                new PlanningPreparationResult(facts(), PlanningPreparationResult.ToolSummary.empty());
    }

    @Test
    void initialDraftCanCompleteAndWarningsDoNotBlockCompletion() {
        generator.results.add(result(validPlan(), 101, 31, 0));

        PlanningOutcome outcome = orchestrator(preparation).plan(command());

        assertThat(outcome.status()).isEqualTo(PlanningOutcomeStatus.COMPLETED);
        assertThat(plans.versions).hasSize(1);
        assertThat(plans.versions.getFirst().status()).isEqualTo(TravelPlanStatus.COMPLETED);
        assertThat(repairer.calls).isZero();
    }

    @Test
    void firstRepairCanPassAndEveryParseableVersionIsSavedAndFullyValidated() {
        generator.results.add(result(invalidPlan(), 100, 30, 0));
        repairer.results.add(result(validPlan(), 80, 25, 0));

        PlanningOutcome outcome = orchestrator(preparation).plan(command());

        assertThat(outcome.status()).isEqualTo(PlanningOutcomeStatus.COMPLETED);
        assertThat(repairer.calls).isEqualTo(1);
        assertThat(plans.versions).extracting(TravelPlanRepository.VersionDraft::versionNumber)
                .containsExactly(1, 2);
        assertThat(plans.versions.getFirst().validationReport().canProceed()).isFalse();
        assertThat(plans.versions.get(1).validationReport().canProceed()).isTrue();
        assertThat(repairer.requests.getFirst().issues())
                .allMatch(issue -> issue.severity() == com.wyx.travelagent.planning.validation.IssueSeverity.ERROR);
        List<PlanningTraceEvent> modelEvents = traces.events.stream()
                .filter(event -> event.step() == com.wyx.travelagent.trace.PlanningStep.GENERATION
                        || event.step() == com.wyx.travelagent.trace.PlanningStep.REPAIR)
                .toList();
        assertThat(modelEvents).hasSize(2);
        assertThat(modelEvents.get(1).details().estimatedInputTokens())
                .isGreaterThan(modelEvents.getFirst().details().estimatedInputTokens());
        assertThat(modelEvents).allSatisfy(event -> assertThat(event.durationMs()).isPositive());
    }

    @Test
    void exactlyTwoFailedRepairsEndAsNeedsAdjustmentWithoutAThirdRepair() {
        generator.results.add(result(invalidPlan(), 100, 30, 0));
        repairer.results.add(result(invalidPlan(), 80, 25, 0));
        repairer.results.add(result(invalidPlan(), 75, 20, 0));

        PlanningOutcome outcome = orchestrator(preparation).plan(command());

        assertThat(outcome.status()).isEqualTo(PlanningOutcomeStatus.NEEDS_ADJUSTMENT);
        assertThat(repairer.calls).isEqualTo(2);
        assertThat(plans.versions).hasSize(3);
        assertThat(plans.versions.getLast().status()).isEqualTo(TravelPlanStatus.NEEDS_ADJUSTMENT);
    }

    @Test
    void malformedInitialResponseGetsOnlyOneStructureRetryAndSavesNoVersion() {
        generator.failures.add(new ItineraryGenerationException(
                ItineraryGenerationException.INVALID_RESPONSE));
        generator.failures.add(new ItineraryGenerationException(
                ItineraryGenerationException.INVALID_RESPONSE));

        PlanningOutcome outcome = orchestrator(preparation).plan(command());

        assertThat(outcome.status()).isEqualTo(PlanningOutcomeStatus.FAILED);
        assertThat(outcome.failureCode()).isEqualTo(ItineraryGenerationException.INVALID_RESPONSE);
        assertThat(generator.calls).isEqualTo(2);
        assertThat(plans.versions).isEmpty();
    }

    @Test
    void poiPreparationFailureStopsBeforeAnyModelCall() {
        PlanningPreparationGateway unavailable = (requirement, query, policy, plan) -> {
            throw new PlanningPreparationException("POI_DATA_UNAVAILABLE");
        };

        PlanningOutcome outcome = orchestrator(unavailable).plan(command());

        assertThat(outcome.status()).isEqualTo(PlanningOutcomeStatus.FAILED);
        assertThat(outcome.failureCode()).isEqualTo("POI_DATA_UNAVAILABLE");
        assertThat(generator.calls).isZero();
        assertThat(repairer.calls).isZero();
    }

    @Test
    void invalidReferencesAreSanitizedAndCountedInTraceWithoutBlockingCompletion() {
        generator.results.add(result(validPlan(), 90, 20, 2));

        PlanningOutcome outcome = orchestrator(preparation).plan(command());

        assertThat(outcome.status()).isEqualTo(PlanningOutcomeStatus.COMPLETED);
        assertThat(traces.events).anySatisfy(event ->
                assertThat(event.details().invalidReferenceCount()).isEqualTo(2));
    }

    @Test
    void requiredContextOverBudgetStopsBeforeAnyModelCall() {
        PromptContextAssembler tinyBudget = new PromptContextAssembler(new ContextBudgetProperties(
                3, 1, 1, 1, 1, 1, 1, 1, 1), new ConservativeTokenEstimator());

        PlanningOutcome outcome = orchestrator(preparation, tinyBudget).plan(command());

        assertThat(outcome.status()).isEqualTo(PlanningOutcomeStatus.CONTEXT_BUDGET_EXCEEDED);
        assertThat(outcome.failureCode()).isEqualTo("CONTEXT_BUDGET_EXCEEDED");
        assertThat(generator.calls).isZero();
        assertThat(repairer.calls).isZero();
        assertThat(plans.versions).isEmpty();
    }

    @Test
    void traceFailureAfterVersionSaveCompensatesParentStatusAndReturnsStableFailure() {
        generator.results.add(result(validPlan(), 90, 20, 0));
        traces.failAfterSuccessfulWrites = 2;

        PlanningOutcome outcome = orchestrator(preparation).plan(command());

        assertThat(outcome.status()).isEqualTo(PlanningOutcomeStatus.FAILED);
        assertThat(outcome.failureCode()).isEqualTo("TRACE_PERSISTENCE_FAILED");
        assertThat(plans.versions).hasSize(1);
        assertThat(plans.updatedStatus).isEqualTo(TravelPlanStatus.FAILED);
    }

    @Test
    void versionPersistenceFailureCompensatesCreatedParentAndReturnsStableFailure() {
        generator.results.add(result(validPlan(), 90, 20, 0));
        plans.failSave = true;

        PlanningOutcome outcome = orchestrator(preparation).plan(command());

        assertThat(outcome.status()).isEqualTo(PlanningOutcomeStatus.FAILED);
        assertThat(outcome.failureCode()).isEqualTo("PLAN_PERSISTENCE_FAILED");
        assertThat(plans.updatedStatus).isEqualTo(TravelPlanStatus.FAILED);
    }

    private TravelPlanningOrchestrator orchestrator(PlanningPreparationGateway gateway) {
        return orchestrator(gateway, assembler());
    }

    private TravelPlanningOrchestrator orchestrator(
            PlanningPreparationGateway gateway,
            PromptContextAssembler promptAssembler) {
        UserTravelPreferenceRepository preferenceRepository = mock(UserTravelPreferenceRepository.class);
        when(preferenceRepository.findByUserId(9)).thenReturn(Optional.empty());
        return new TravelPlanningOrchestrator(
                new PreferenceMemoryService(preferenceRepository),
                new RequirementValidator(), gateway, promptAssembler, generator, repairer,
                new ItineraryValidator(), plans,
                new PlanningTraceService(traces, fixedClock()), new PlanMetricsCalculator());
    }

    private static TravelPlanningCommand command() {
        TravelRequirementInput input = new TravelRequirementInput(
                "杭州", date(), date(), 1, BigDecimal.valueOf(1000), CompanionType.SOLO.name(),
                PreferenceValue.notProvided(), PreferenceValue.notProvided(),
                PreferenceValue.notProvided(), PreferenceValue.notProvided(), null, false);
        return new TravelPlanningCommand(77L, 9, input, "杭州旅行",
                mock(ToolExecutionPolicy.class), new ToolPlan("杭州", List.of()));
    }

    private static PromptContextAssembler assembler() {
        return new PromptContextAssembler(new ContextBudgetProperties(
                12000, 9000, 2000, 1000, 3000, 4000, 1000, 10, 5),
                new ConservativeTokenEstimator());
    }

    private static ItineraryValidationFacts facts() {
        return new ItineraryValidationFacts(List.of(new ValidationPoiFact(
                place(), 0, true, false, OpeningHoursFact.unknown())),
                List.of(), List.of(), null, 0, List.of());
    }

    private static ItineraryGenerationResult result(
            ItineraryPlan plan, int inputTokens, int outputTokens, int invalidReferences) {
        return new ItineraryGenerationResult(plan,
                new ModelTokenUsage(inputTokens, outputTokens), invalidReferences);
    }

    private static ItineraryPlan validPlan() {
        ItineraryItem item = new ItineraryItem(LocalTime.of(9, 0), LocalTime.of(10, 0),
                "poi-1", "西湖", null,
                new CostEstimate(CostStatus.MISSING, null, null, null, null), null);
        return new ItineraryPlan("杭州", date(), date(),
                List.of(new ItineraryDay(date(), List.of(item))), BigDecimal.ZERO);
    }

    private static ItineraryPlan invalidPlan() {
        LocalDate wrong = date().plusDays(1);
        return new ItineraryPlan("杭州", wrong, wrong,
                List.of(new ItineraryDay(wrong, validPlan().days().getFirst().items())), BigDecimal.ZERO);
    }

    private static PlaceCandidate place() {
        return new PlaceCandidate("poi-1", "西湖", "杭州",
                new GeoPoint(120.1, 30.2, CoordinateSystem.GCJ02));
    }

    private static LocalDate date() {
        return LocalDate.of(2026, 10, 1);
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-09-11T03:00:00Z"), ZoneOffset.UTC);
    }

    private static final class QueueGenerator implements ItineraryGenerator {
        private final Queue<ItineraryGenerationResult> results = new ArrayDeque<>();
        private final Queue<ItineraryGenerationException> failures = new ArrayDeque<>();
        private int calls;

        @Override
        public ItineraryPlan generate(com.wyx.travelagent.planning.context.PromptContext context) {
            return generateResult(context).plan();
        }

        @Override
        public ItineraryGenerationResult generateResult(
                com.wyx.travelagent.planning.context.PromptContext context) {
            calls++;
            if (!failures.isEmpty()) throw failures.remove();
            return results.remove();
        }
    }

    private static final class QueueRepairer implements ItineraryRepairer {
        private final Queue<ItineraryGenerationResult> results = new ArrayDeque<>();
        private final List<com.wyx.travelagent.planning.repair.RepairRequest> requests = new ArrayList<>();
        private int calls;

        @Override
        public RepairExecution repair(
                com.wyx.travelagent.planning.repair.RepairRequest request,
                com.wyx.travelagent.planning.model.TravelPlanningContext context) {
            calls++;
            requests.add(request);
            var promptContext = assembler().assemble(new com.wyx.travelagent.planning.context.PromptContextInput(
                    context, request.issues(), null, request.previousPlan()));
            return new RepairExecution(results.remove(), promptContext);
        }
    }

    private static final class RecordingPlanRepository implements TravelPlanRepository {
        private final List<VersionDraft> versions = new ArrayList<>();
        private boolean failSave;
        private TravelPlanStatus updatedStatus;

        @Override
        public long create(int userId, com.wyx.travelagent.planning.model.TravelRequirement requirement) {
            return 42L;
        }

        @Override
        public SavedVersion saveVersion(long planId, VersionDraft draft) {
            if (failSave) throw new IllegalStateException("database detail");
            versions.add(draft);
            return new SavedVersion(100L + draft.versionNumber(), draft.versionNumber());
        }

        @Override
        public void updateStatus(long planId, TravelPlanStatus status) {
            updatedStatus = status;
        }
    }

    private static final class RecordingTraceRepository implements PlanningTraceRepository {
        private final List<PlanningTraceEvent> events = new ArrayList<>();
        private int failAfterSuccessfulWrites = Integer.MAX_VALUE;

        @Override
        public void append(PlanningTraceEvent event) {
            if (events.size() >= failAfterSuccessfulWrites) {
                throw new IllegalStateException("trace database detail");
            }
            events.add(event);
        }
    }
}
