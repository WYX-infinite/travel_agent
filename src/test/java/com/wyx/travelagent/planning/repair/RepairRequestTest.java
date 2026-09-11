package com.wyx.travelagent.planning.repair;

import com.wyx.travelagent.knowledge.retrieval.KnowledgeReference;
import com.wyx.travelagent.planning.model.CompanionType;
import com.wyx.travelagent.planning.model.ItineraryDay;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.validation.IssueSeverity;
import com.wyx.travelagent.planning.validation.ValidationIssue;
import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepairRequestTest {

    @Test
    void freezesOnlyStructuredCurrentRoundInputs() {
        List<ValidationIssue> issues = new ArrayList<>(List.of(issue()));
        List<PlaceCandidate> places = new ArrayList<>(List.of(place()));
        List<KnowledgeReference> references = new ArrayList<>();

        RepairRequest request = new RepairRequest(requirement(), plan(), issues, places, references);
        issues.clear();
        places.clear();

        assertThat(request.issues()).containsExactly(issue());
        assertThat(request.allowedPlaces()).containsExactly(place());
        assertThat(request.allowedReferences()).isEmpty();
        assertThatThrownBy(() -> request.issues().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static TravelRequirement requirement() {
        return new TravelRequirement("杭州", date(), date(), 1, BigDecimal.valueOf(1000),
                CompanionType.SOLO, Set.of(), null, null, null, null);
    }

    private static ItineraryPlan plan() {
        return new ItineraryPlan("杭州", date(), date(), List.of(new ItineraryDay(date(), List.of())),
                BigDecimal.ZERO);
    }

    private static ValidationIssue issue() {
        return new ValidationIssue("PLAN_TEST", IssueSeverity.ERROR, date(), null,
                null, "需要修正", null, null);
    }

    private static PlaceCandidate place() {
        return new PlaceCandidate("poi-1", "西湖", "杭州", new GeoPoint(
                120.1, 30.2, CoordinateSystem.GCJ02));
    }

    private static LocalDate date() {
        return LocalDate.of(2026, 10, 1);
    }
}
