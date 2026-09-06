package com.wyx.travelagent.tools.dispatch;

import com.wyx.travelagent.tools.model.ToolResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ToolExecutionBundle(List<Execution> executions, Map<String, Integer> providerCallCounts) {
    public ToolExecutionBundle {
        executions = List.copyOf(executions);
        providerCallCounts = Map.copyOf(new LinkedHashMap<>(providerCallCounts));
    }

    public record Execution(TravelToolType type, ToolResult<?> result, boolean cacheHit) {
        public Execution {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(result, "result");
        }
    }
}
