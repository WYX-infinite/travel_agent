package com.wyx.travelagent.tools.dispatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Raw model output. It can carry invalid actions so the validator can reject them explicitly. */
public record ToolPlan(String destinationCity, List<ToolAction> actions) {
    public ToolPlan {
        if (actions != null) {
            actions = Collections.unmodifiableList(new ArrayList<>(actions));
        }
    }
}
