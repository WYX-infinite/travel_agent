package com.wyx.travelagent.tools.dispatch;

public record ToolExecutionPolicy(int maxToolCalls) {
    public ToolExecutionPolicy {
        if (maxToolCalls <= 0) {
            throw new IllegalArgumentException("maxToolCalls must be positive");
        }
    }
}
