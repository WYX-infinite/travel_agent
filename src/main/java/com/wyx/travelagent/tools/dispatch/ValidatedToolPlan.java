package com.wyx.travelagent.tools.dispatch;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ValidatedToolPlan {
    private final String destinationCity;
    private final ToolExecutionPolicy policy;
    private final List<ValidatedToolAction> actions;

    private ValidatedToolPlan(String destinationCity, ToolExecutionPolicy policy, List<ValidatedToolAction> actions) {
        this.destinationCity = Objects.requireNonNull(destinationCity, "destinationCity");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.actions = List.copyOf(actions);
    }

    public String destinationCity() {
        return destinationCity;
    }

    public ToolExecutionPolicy policy() {
        return policy;
    }

    public List<ValidatedToolAction> actions() {
        return actions;
    }

    static ValidatedToolPlan create(
            ToolPlanValidator.ValidationProof proof,
            String destinationCity,
            ToolExecutionPolicy policy,
            List<ValidatedToolAction> actions) {
        if (!ToolPlanValidator.accepts(proof)) {
            throw new IllegalArgumentException("validated plans require validator proof");
        }
        return new ValidatedToolPlan(destinationCity, policy, actions);
    }

    public record ValidatedToolAction(TravelToolType type, Map<String, Object> arguments) {
        public ValidatedToolAction {
            Objects.requireNonNull(type, "type");
            arguments = Map.copyOf(arguments);
        }
    }
}
