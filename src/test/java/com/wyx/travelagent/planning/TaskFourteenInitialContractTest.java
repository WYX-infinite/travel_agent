package com.wyx.travelagent.planning;

import com.wyx.travelagent.trace.PlanningStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 用最小反射契约证明任务十四的核心类型和真实进度粒度在实现前确实缺失。
 */
class TaskFourteenInitialContractTest {

    @Test
    void taskFourteenCoreTypesExist() {
        assertThatCode(() -> Class.forName(
                "com.wyx.travelagent.planning.task.PlanningTaskService"))
                .doesNotThrowAnyException();
    }

    @Test
    void preparationProgressDistinguishesKnowledgeAndToolExecution() {
        assertThatCode(() -> PlanningStep.valueOf("KNOWLEDGE_RETRIEVAL"))
                .doesNotThrowAnyException();
        assertThatCode(() -> PlanningStep.valueOf("TOOL_EXECUTION"))
                .doesNotThrowAnyException();
    }
}
