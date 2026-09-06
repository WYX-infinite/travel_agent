package com.wyx.travelagent.tools.dispatch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Raw model output. Validation intentionally happens later in {@link ToolPlanValidator}. */
public record ToolAction(String toolName, Map<String, Object> arguments) {
    public ToolAction {
        if (arguments != null) {
            arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        }
    }
}
