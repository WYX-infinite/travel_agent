package com.wyx.travelagent.planning.model;

import com.wyx.travelagent.tools.model.TravelMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryDeterminismTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);

    @Test
    void itemOrderingUsesEveryRemainingFieldAsNullSafeTieBreaker() {
        assertOrderIndependent(
                item("Alpha", null, "1.00", null),
                item("Beta", null, "1.00", null));
        assertOrderIndependent(
                item("Same", null, "1.00", null),
                item("Same", "notes", "1.00", null));
        assertOrderIndependent(
                item("Same", "notes", "1.00", null),
                item("Same", "notes", "2.00", null));
        assertOrderIndependent(
                item("Same", "notes", "1.0", null),
                item("Same", "notes", "1.00", null));
        assertOrderIndependent(
                item("Same", "notes", "1.00", null),
                item("Same", "notes", "1.00", TravelMode.WALKING));
    }

    @Test
    void dayOrderingUsesContentsWhenDatesTie() {
        ItineraryDay alphaDay = new ItineraryDay(DATE,
                List.of(item("Alpha", null, "1.00", null)));
        ItineraryDay betaDay = new ItineraryDay(DATE,
                List.of(item("Beta", null, "1.00", null)));

        ItineraryPlan forward = plan(List.of(alphaDay, betaDay));
        ItineraryPlan reversed = plan(List.of(betaDay, alphaDay));

        assertThat(forward.days()).containsExactly(alphaDay, betaDay);
        assertThat(reversed.days()).isEqualTo(forward.days());
    }

    private static void assertOrderIndependent(ItineraryItem first, ItineraryItem second) {
        ItineraryDay forward = new ItineraryDay(DATE, List.of(first, second));
        ItineraryDay reversed = new ItineraryDay(DATE, List.of(second, first));

        assertThat(forward.items()).containsExactly(first, second);
        assertThat(reversed.items()).isEqualTo(forward.items());
    }

    private static ItineraryItem item(
            String name, String description, String cost, TravelMode travelMode) {
        return new ItineraryItem(
                LocalTime.of(9, 0), LocalTime.of(10, 0), "poi-1", name,
                description, new BigDecimal(cost), travelMode);
    }

    private static ItineraryPlan plan(List<ItineraryDay> days) {
        return new ItineraryPlan("杭州", DATE, DATE, days, new BigDecimal("100.00"));
    }
}
