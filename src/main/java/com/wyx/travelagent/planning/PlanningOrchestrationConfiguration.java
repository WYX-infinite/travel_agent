package com.wyx.travelagent.planning;

import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalService;
import com.wyx.travelagent.planning.context.ConservativeTokenEstimator;
import com.wyx.travelagent.planning.context.ContextBudgetProperties;
import com.wyx.travelagent.planning.context.PromptContextAssembler;
import com.wyx.travelagent.planning.generation.ItineraryGenerator;
import com.wyx.travelagent.planning.memory.PreferenceMemoryService;
import com.wyx.travelagent.planning.metrics.PlanMetricsCalculator;
import com.wyx.travelagent.planning.persistence.TravelPlanRepository;
import com.wyx.travelagent.planning.preparation.DefaultPlanningPreparationGateway;
import com.wyx.travelagent.planning.preparation.PlanningPreparationGateway;
import com.wyx.travelagent.planning.repair.ItineraryRepairService;
import com.wyx.travelagent.planning.repair.ItineraryRepairer;
import com.wyx.travelagent.planning.validation.ItineraryValidator;
import com.wyx.travelagent.planning.validation.RequirementValidator;
import com.wyx.travelagent.tools.dispatch.TravelToolDispatcher;
import com.wyx.travelagent.trace.PlanningTraceRepository;
import com.wyx.travelagent.trace.PlanningTraceService;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 装配任务十三的同步规划、单次修正、事实准备、指标和 Trace 服务。
 * 输入复用任务十至十二已经注册的端口，输出为任务十四以后可直接注入的编排器；构造 Bean 时不调用模型、数据库或外部网络。
 * 任务十三只提供同步业务链，不负责 HTTP、异步执行器、SSE 或任务状态机。
 */
@Configuration(proxyBeanMethods = false)
class PlanningOrchestrationConfiguration {

    @Bean
    RequirementValidator requirementValidator() {
        return new RequirementValidator();
    }

    @Bean
    ItineraryValidator itineraryValidator() {
        return new ItineraryValidator();
    }

    @Bean
    PlanMetricsCalculator planMetricsCalculator() {
        return new PlanMetricsCalculator();
    }

    @Bean
    PlanningPreparationGateway planningPreparationGateway(
            KnowledgeRetrievalService knowledgeService,
            TravelToolDispatcher toolDispatcher) {
        return new DefaultPlanningPreparationGateway(knowledgeService, toolDispatcher);
    }

    @Bean
    ItineraryRepairer itineraryRepairer(
            ContextBudgetProperties properties,
            ConservativeTokenEstimator estimator,
            ChatModel chatModel) {
        return new ItineraryRepairService(properties, estimator, chatModel);
    }

    @Bean
    PlanningTraceService planningTraceService(
            PlanningTraceRepository repository,
            @Qualifier("providerClock") Clock clock) {
        return new PlanningTraceService(repository, clock);
    }

    @Bean
    TravelPlanningOrchestrator travelPlanningOrchestrator(
            PreferenceMemoryService preferenceMemoryService,
            RequirementValidator requirementValidator,
            PlanningPreparationGateway preparationGateway,
            PromptContextAssembler contextAssembler,
            ItineraryGenerator generator,
            ItineraryRepairer repairer,
            ItineraryValidator itineraryValidator,
            TravelPlanRepository planRepository,
            PlanningTraceService traceService,
            PlanMetricsCalculator metricsCalculator) {
        return new TravelPlanningOrchestrator(
                preferenceMemoryService, requirementValidator, preparationGateway,
                contextAssembler, generator, repairer, itineraryValidator,
                planRepository, traceService, metricsCalculator);
    }
}
