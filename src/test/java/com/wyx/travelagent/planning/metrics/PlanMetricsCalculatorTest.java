package com.wyx.travelagent.planning.metrics;

import com.wyx.travelagent.planning.model.CostEstimate;
import com.wyx.travelagent.planning.model.CostEvidenceType;
import com.wyx.travelagent.planning.model.CostStatus;
import com.wyx.travelagent.planning.model.ItineraryDay;
import com.wyx.travelagent.planning.model.ItineraryItem;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.ItineraryValidationFacts;
import com.wyx.travelagent.planning.validation.IssueSeverity;
import com.wyx.travelagent.planning.validation.ValidationIssue;
import com.wyx.travelagent.planning.validation.ValidationReport;
import com.wyx.travelagent.tools.model.TravelMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanMetricsCalculatorTest {

    @Test
    void preservesAuditableNumeratorsAndDenominators() {
        ItineraryPlan plan = new ItineraryPlan("杭州", date(), date(), List.of(new ItineraryDay(date(), List.of(
                item("poi-1", known(CostEvidenceType.FACT), null),
                item("poi-2", known(CostEvidenceType.AI_ESTIMATE), TravelMode.WALKING),
                item("poi-3", missing(), TravelMode.WALKING)
        ))), BigDecimal.valueOf(120));
        ValidationReport report = new ValidationReport(List.of(
                new ValidationIssue("WEATHER_UNAVAILABLE", IssueSeverity.WARNING, date(), 0,
                        "poi-1", "天气资料不可用", null, null)));

        PlanMetrics metrics = new PlanMetricsCalculator().calculate(
                plan, report, ItineraryValidationFacts.empty(), 8);

        assertThat(metrics.sourceCoverage()).isEqualTo(new MetricRatio(1, 3));
        assertThat(metrics.routeCoverage()).isEqualTo(new MetricRatio(0, 2));
        assertThat(metrics.budgetCompleteness()).isEqualTo(new MetricRatio(2, 3));
        assertThat(metrics.rulePassRate()).isEqualTo(new MetricRatio(7, 8));
        assertThat(metrics.sourceCoverage().applicable()).isTrue();
    }

    @Test
    void zeroDenominatorIsNotApplicableInsteadOfInventingZeroOrOneHundredPercent() {
        ItineraryPlan empty = new ItineraryPlan("杭州", date(), date(),
                List.of(new ItineraryDay(date(), List.of())), BigDecimal.ZERO);

        PlanMetrics metrics = new PlanMetricsCalculator().calculate(
                empty, new ValidationReport(List.of()), ItineraryValidationFacts.empty(), 8);

        assertThat(metrics.sourceCoverage()).isEqualTo(new MetricRatio(0, 0));
        assertThat(metrics.sourceCoverage().applicable()).isFalse();
        assertThat(metrics.sourceCoverage().percentage()).isEmpty();
        assertThat(metrics.routeCoverage().percentage()).isEmpty();
        assertThat(metrics.rulePassRate().percentage()).contains(100.0);
    }

    private static ItineraryItem item(String poiId, CostEstimate cost, TravelMode mode) {
        return new ItineraryItem(LocalTime.of(9, 0), LocalTime.of(10, 0), poiId,
                poiId, null, cost, mode);
    }

    private static CostEstimate known(CostEvidenceType evidenceType) {
        return new CostEstimate(CostStatus.KNOWN, BigDecimal.TEN, BigDecimal.TEN,
                evidenceType, null);
    }

    private static CostEstimate missing() {
        return new CostEstimate(CostStatus.MISSING, null, null, null, null);
    }

    private static LocalDate date() {
        return LocalDate.of(2026, 10, 1);
    }
}
