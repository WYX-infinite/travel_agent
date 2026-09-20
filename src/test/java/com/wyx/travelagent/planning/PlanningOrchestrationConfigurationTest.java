package com.wyx.travelagent.planning;

import com.wyx.travelagent.planning.metrics.PlanMetricsCalculator;
import com.wyx.travelagent.planning.preparation.PlanningPreparationGateway;
import com.wyx.travelagent.planning.repair.ItineraryRepairer;
import com.wyx.travelagent.planning.task.PlanningTaskRepository;
import com.wyx.travelagent.trace.PlanningTraceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties =
        "travel.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class PlanningOrchestrationConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void completeTaskThirteenChainIsInjectableWithoutAmbiguousBeans() {
        assertThat(context.getBean(TravelPlanningOrchestrator.class)).isNotNull();
        assertThat(context.getBean(ItineraryRepairer.class)).isNotNull();
        assertThat(context.getBean(PlanningPreparationGateway.class)).isNotNull();
        assertThat(context.getBean(PlanningTraceService.class)).isNotNull();
        assertThat(context.getBean(PlanMetricsCalculator.class)).isNotNull();
    }

    @Test
    void planningTaskRepositoryHasOnlyTheMybatisProductionBean() {
        assertThat(context.getBeansOfType(PlanningTaskRepository.class))
                .containsOnlyKeys("mybatisPlanningTaskRepository");
    }
}
