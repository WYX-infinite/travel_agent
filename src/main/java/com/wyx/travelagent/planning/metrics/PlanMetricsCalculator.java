package com.wyx.travelagent.planning.metrics;

import com.wyx.travelagent.planning.model.CostEvidenceType;
import com.wyx.travelagent.planning.model.CostStatus;
import com.wyx.travelagent.planning.model.ItineraryItem;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.ItineraryValidationFacts;
import com.wyx.travelagent.planning.model.RouteFact;
import com.wyx.travelagent.planning.validation.ValidationIssue;
import com.wyx.travelagent.planning.validation.ValidationReport;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 业务问题：只用已保存行程、可信事实和全量校验报告计算确定性质量指标。
 * 输入不含 Prompt、模型原文或历史对话，输出为带分子分母的 {@link PlanMetrics}；本类型不访问外部资源或认证。
 * 任务十三在每次版本落库前调用本类型；本类型不重新调用模型、工具或校验器。
 * 本类型不处理认证，也不负责选择执行规则或保存计算结果。
 */
public final class PlanMetricsCalculator {

    /** 创建无状态指标计算器；构造不访问外部资源，也不处理认证。 */
    public PlanMetricsCalculator() {
    }

    /**
     * 计算当前版本四项指标。
     *
     * @param plan 非空可解析行程
     * @param report 非空全量 Java 校验报告
     * @param facts 非空可信事实快照
     * @param executedRuleCount 实际执行的规则数，必须为正数
     * @return 可审计指标；没有条目或移动时相应分母为零
     * @throws NullPointerException 当任一对象为空
     * @throws IllegalArgumentException 当规则数非正或问题映射出的失败规则超过执行数
     * 本方法只遍历内存对象，不访问模型、数据库、缓存、文件或网络，也不处理认证。
     */
    public PlanMetrics calculate(
            ItineraryPlan plan,
            ValidationReport report,
            ItineraryValidationFacts facts,
            int executedRuleCount) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(facts, "facts");
        if (executedRuleCount <= 0) throw new IllegalArgumentException("executedRuleCount");

        int itemCount = plan.days().stream().mapToInt(day -> day.items().size()).sum();
        int sourcedItems = (int) plan.days().stream().flatMap(day -> day.items().stream())
                .filter(PlanMetricsCalculator::hasTrustedSource).count();
        int knownCostItems = (int) plan.days().stream().flatMap(day -> day.items().stream())
                .filter(item -> item.cost().status() == CostStatus.KNOWN).count();

        Set<RouteKey> verifiedRoutes = new HashSet<>();
        for (RouteFact route : facts.routes()) {
            if (route.segment().verified()) {
                verifiedRoutes.add(new RouteKey(route.fromPoiId(), route.toPoiId(), route.segment().mode()));
            }
        }
        int routeDenominator = 0;
        int routeNumerator = 0;
        for (var day : plan.days()) {
            for (int index = 1; index < day.items().size(); index++) {
                routeDenominator++;
                ItineraryItem previous = day.items().get(index - 1);
                ItineraryItem current = day.items().get(index);
                if (current.travelModeFromPrevious() != null
                        && verifiedRoutes.contains(new RouteKey(previous.poiId(), current.poiId(),
                        current.travelModeFromPrevious()))) {
                    routeNumerator++;
                }
            }
        }

        int failedRules = (int) report.issues().stream()
                .map(PlanMetricsCalculator::ruleGroup)
                .distinct()
                .count();
        if (failedRules > executedRuleCount) throw new IllegalArgumentException("validation rule count");
        return new PlanMetrics(
                new MetricRatio(sourcedItems, itemCount),
                new MetricRatio(routeNumerator, routeDenominator),
                new MetricRatio(knownCostItems, itemCount),
                new MetricRatio(executedRuleCount - failedRules, executedRuleCount));
    }

    private static boolean hasTrustedSource(ItineraryItem item) {
        return item.cost().status() == CostStatus.KNOWN
                && (item.cost().evidenceType() == CostEvidenceType.FACT
                || item.cost().evidenceType() == CostEvidenceType.DOCUMENT);
    }

    private static String ruleGroup(ValidationIssue issue) {
        String code = issue.code();
        if (code.startsWith("POI_")) return "POI";
        if (code.startsWith("PLAN_") || code.startsWith("DAY_")) return "DATE";
        if (code.startsWith("ITEM_") || code.startsWith("TIME_")) return "TIME";
        if (code.startsWith("ROUTE_")) return "ROUTE";
        if (code.startsWith("DAILY_") || code.startsWith("PACE_")) return "INTENSITY";
        if (code.startsWith("BUDGET_") || code.startsWith("COST_")) return "BUDGET";
        if (code.startsWith("OPENING_")) return "OPENING";
        if (code.startsWith("WEATHER_")) return "WEATHER";
        // 未知问题码按独立规则计算，避免新规则上线后被错误计为通过。
        return code;
    }

    private record RouteKey(String fromPoiId, String toPoiId,
                            com.wyx.travelagent.tools.model.TravelMode mode) {
    }
}
