package com.wyx.travelagent.planning.validation;

import com.wyx.travelagent.planning.model.CostEstimate;
import com.wyx.travelagent.planning.model.CostFact;
import com.wyx.travelagent.planning.model.ItineraryDay;
import com.wyx.travelagent.planning.model.ItineraryItem;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.planning.model.ItineraryValidationFacts;
import com.wyx.travelagent.planning.model.OpeningHoursFact;
import com.wyx.travelagent.planning.model.RouteFact;
import com.wyx.travelagent.planning.model.TravelPace;
import com.wyx.travelagent.planning.model.TravelPlanningContext;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.model.ValidationPoiFact;
import com.wyx.travelagent.tools.model.TravelSegment;
import com.wyx.travelagent.tools.model.WeatherResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 业务问题：以纯 Java 规则验证模型生成草稿的 POI、日期、时间、路线、强度、预算、营业时间和天气风险。
 * 输入为可信内部规划上下文与不可信 ItineraryPlan，输出为稳定 ValidationReport；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十提供 POI/路线/天气事实，任务十二在模型生成后执行本校验，后续任务十三必须重新执行全部规则；本类型不负责生成 Prompt、调用模型或自动修正。
 */
public final class ItineraryValidator {
    private static final int ADVERSE_PRECIPITATION_PROBABILITY_PERCENT = 70;
    private static final double ADVERSE_PRECIPITATION_MILLIMETERS = 10.0;
    /*
     * 三档阈值集中定义，避免规则中散落魔法数字。数量和总活动分钟任一超限都视为强度过高；
     * 紧凑档仍保留十二小时上限，防止“紧凑”被解释为无休止安排。
     */
    private static final Map<TravelPace, DailyIntensityLimit> INTENSITY_LIMITS = intensityLimits();

    private final List<ItineraryRule> rules;

    /**
     * 创建包含任务十二全部确定性规则的无外部依赖校验器。
     * 无输入，规则列表固定且不可变；构造不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public ItineraryValidator() {
        rules = List.of(
                this::validatePoiWhitelist,
                this::validateDates,
                this::validateTimes,
                this::validateRoutes,
                this::validateIntensity,
                this::validateBudget,
                this::validateOpeningHours,
                this::validateWeather);
    }

    /**
     * 对不可信模型草稿执行全部 Java 规则。
     * 模型即使声明“校验通过”也不能改变本结果；事实缺失使用 WARNING，已证实违反硬约束使用 ERROR。
     *
     * @param context 非空可信内部上下文；空事实集合表示相应事实未知
     * @param plan 非空待校验草稿
     * @return 不可变且稳定排序的完整报告
     * @throws NullPointerException 当上下文或方案为空
     * 本方法只读取内存对象，不调用模型，也不访问数据库、缓存、文件或网络，不处理认证。
     */
    public ValidationReport validate(TravelPlanningContext context, ItineraryPlan plan) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(plan, "plan");
        // Java 校验器禁止调用模型：只有确定性事实才能阻止幻觉地点或约束漂移进入后续阶段。
        return rules.stream()
                .flatMap(rule -> rule.validate(context, plan).stream())
                .collect(ValidationReport.collector());
    }

    private List<ValidationIssue> validatePoiWhitelist(TravelPlanningContext context, ItineraryPlan plan) {
        Set<String> allowed = context.validationFacts().places().stream()
                .map(fact -> fact.place().poiId())
                .collect(Collectors.toUnmodifiableSet());
        List<ValidationIssue> issues = new ArrayList<>();
        forEachItem(plan, (date, index, item) -> {
            // 必须按工具返回的 ID 白名单判断；名称相同并不能证明地点真实或属于本次查询。
            if (!allowed.contains(item.poiId())) {
                issues.add(issue("UNKNOWN_POI", IssueSeverity.ERROR, date, index, item.poiId(),
                        "activity poiId is not in the allowed place list", null, null));
            }
        });
        return issues;
    }

    private List<ValidationIssue> validateDates(TravelPlanningContext context, ItineraryPlan plan) {
        TravelRequirement requirement = requirement(context);
        List<ValidationIssue> issues = new ArrayList<>();
        if (!plan.startDate().equals(requirement.startDate()) || !plan.endDate().equals(requirement.endDate())) {
            issues.add(issue("PLAN_DATE_RANGE_MISMATCH", IssueSeverity.ERROR, null, null, null,
                    "plan date range does not match the request", null, null));
        }

        Map<LocalDate, Integer> counts = new HashMap<>();
        for (ItineraryDay day : plan.days()) counts.merge(day.date(), 1, Integer::sum);
        for (LocalDate expected = requirement.startDate(); !expected.isAfter(requirement.endDate());
             expected = expected.plusDays(1)) {
            int count = counts.getOrDefault(expected, 0);
            if (count == 0) {
                issues.add(issue("DATE_MISSING", IssueSeverity.ERROR, expected, null, null,
                        "required itinerary date is missing", null, null));
            } else if (count > 1) {
                issues.add(issue("DATE_DUPLICATE", IssueSeverity.ERROR, expected, null, null,
                        "itinerary date appears more than once", Integer.toString(count), null));
            }
        }
        counts.keySet().stream()
                .filter(date -> date.isBefore(requirement.startDate()) || date.isAfter(requirement.endDate()))
                .forEach(date -> issues.add(issue("DATE_EXTRA", IssueSeverity.ERROR, date, null, null,
                        "itinerary contains a date outside the request", null, null)));
        return issues;
    }

    private List<ValidationIssue> validateTimes(TravelPlanningContext context, ItineraryPlan plan) {
        List<ValidationIssue> issues = new ArrayList<>();
        for (ItineraryDay day : plan.days()) {
            List<ItineraryItem> items = day.items();
            for (int index = 0; index < items.size(); index++) {
                ItineraryItem current = items.get(index);
                if (!current.endTime().isAfter(current.startTime())) {
                    issues.add(issue("INVALID_ACTIVITY_TIME", IssueSeverity.ERROR, day.date(), index,
                            current.poiId(), "activity endTime must be later than startTime", null, null));
                }
                if (index > 0) {
                    ItineraryItem previous = items.get(index - 1);
                    if (current.startTime().isBefore(previous.endTime())) {
                        issues.add(issue("ACTIVITY_OVERLAP", IssueSeverity.ERROR, day.date(), index,
                                current.poiId(), "activity overlaps the previous activity", null, null));
                    }
                }
            }
        }
        return issues;
    }

    private List<ValidationIssue> validateRoutes(TravelPlanningContext context, ItineraryPlan plan) {
        ItineraryValidationFacts facts = context.validationFacts();
        Map<RouteKey, RouteFact> routes = facts.routes().stream().collect(Collectors.toMap(
                fact -> new RouteKey(fact.fromPoiId(), fact.toPoiId(), fact.segment().mode()),
                Function.identity(),
                (first, ignored) -> first));
        List<ValidationIssue> issues = new ArrayList<>();
        for (ItineraryDay day : plan.days()) {
            for (int index = 1; index < day.items().size(); index++) {
                ItineraryItem previous = day.items().get(index - 1);
                ItineraryItem current = day.items().get(index);
                RouteFact route = routes.get(new RouteKey(
                        previous.poiId(), current.poiId(), current.travelModeFromPrevious()));
                if (route == null || !route.segment().verified()) {
                    // 路线缺失或未验证时不能伪造通行时间，只能给出稳定警告。
                    issues.add(issue("ROUTE_NOT_VERIFIED", IssueSeverity.WARNING, day.date(), index,
                            current.poiId(), "route duration is not verified", null, null));
                    continue;
                }
                long gap = Duration.between(previous.endTime(), current.startTime()).toMinutes();
                long travel = route.segment().durationMinutes();
                long required = travel + facts.routeBufferMinutes();
                if (gap < required) {
                    issues.add(issue("ROUTE_TIME_INSUFFICIENT", IssueSeverity.ERROR, day.date(), index,
                            current.poiId(), "gap does not cover verified travel time and buffer",
                            travel + " minutes travel + " + facts.routeBufferMinutes() + " minutes buffer",
                            (required - gap) + " minutes"));
                }
            }
        }
        return issues;
    }

    private List<ValidationIssue> validateIntensity(TravelPlanningContext context, ItineraryPlan plan) {
        TravelPace pace = requirement(context).pace() == null ? TravelPace.MODERATE : requirement(context).pace();
        DailyIntensityLimit limit = INTENSITY_LIMITS.get(pace);
        List<ValidationIssue> issues = new ArrayList<>();
        for (ItineraryDay day : plan.days()) {
            long minutes = day.items().stream()
                    .filter(item -> item.endTime().isAfter(item.startTime()))
                    .mapToLong(item -> Duration.between(item.startTime(), item.endTime()).toMinutes())
                    .sum();
            if (day.items().size() > limit.maximumActivities() || minutes > limit.maximumActivityMinutes()) {
                issues.add(issue("DAILY_INTENSITY_EXCEEDED", IssueSeverity.ERROR, day.date(), null, null,
                        "daily activity intensity exceeds the selected pace",
                        day.items().size() + " activities, " + minutes + " minutes",
                        "maximum " + limit.maximumActivities() + " activities and "
                                + limit.maximumActivityMinutes() + " minutes"));
            }
        }
        return issues;
    }

    private List<ValidationIssue> validateBudget(TravelPlanningContext context, ItineraryPlan plan) {
        List<ValidationIssue> issues = new ArrayList<>();
        BigDecimal conservativeTotal = BigDecimal.ZERO;
        Set<CostFactKey> trustedCosts = context.validationFacts().costs().stream()
                .map(CostFactKey::from)
                .collect(Collectors.toUnmodifiableSet());
        for (ItineraryDay day : plan.days()) {
            for (int index = 0; index < day.items().size(); index++) {
                ItineraryItem item = day.items().get(index);
                CostEstimate cost = item.cost();
                if (!cost.isKnown()) {
                    issues.add(issue("COST_INFORMATION_INCOMPLETE", IssueSeverity.WARNING, day.date(), index,
                            item.poiId(), "activity cost is missing", null, null));
                    continue;
                }
                // 预算使用区间上限，避免把乐观下限误当成可以支付的最终总额。
                conservativeTotal = conservativeTotal.add(cost.maximum());
                if (cost.evidenceType() != com.wyx.travelagent.planning.model.CostEvidenceType.AI_ESTIMATE
                        && !trustedCosts.contains(CostFactKey.from(item.poiId(), cost))) {
                    issues.add(issue("COST_EVIDENCE_UNVERIFIED", IssueSeverity.WARNING, day.date(), index,
                            item.poiId(), "cost evidence is not present in trusted facts", null, null));
                }
            }
        }
        BigDecimal budget = requirement(context).totalBudget();
        if (conservativeTotal.compareTo(budget) > 0) {
            issues.add(issue("BUDGET_EXCEEDED", IssueSeverity.ERROR, null, null, null,
                    "conservative cost upper bounds exceed totalBudget",
                    conservativeTotal.toPlainString(),
                    conservativeTotal.subtract(budget).toPlainString()));
        }
        return issues;
    }

    private List<ValidationIssue> validateOpeningHours(TravelPlanningContext context, ItineraryPlan plan) {
        Map<String, ValidationPoiFact> places = context.validationFacts().places().stream()
                .collect(Collectors.toMap(fact -> fact.place().poiId(), Function.identity()));
        List<ValidationIssue> issues = new ArrayList<>();
        forEachItem(plan, (date, index, item) -> {
            ValidationPoiFact place = places.get(item.poiId());
            if (place == null) return;
            OpeningHoursFact hours = place.openingHours();
            if (!hours.reliable()) {
                // 未知营业时间只能警告；猜测一个区间会把缺失资料伪装成可靠事实。
                issues.add(issue("OPENING_HOURS_UNKNOWN", IssueSeverity.WARNING, date, index, item.poiId(),
                        "reliable opening hours are unavailable", null, null));
            } else if (item.startTime().isBefore(hours.opensAt()) || item.endTime().isAfter(hours.closesAt())) {
                issues.add(issue("OUTSIDE_OPENING_HOURS", IssueSeverity.ERROR, date, index, item.poiId(),
                        "activity falls outside reliable opening hours",
                        hours.opensAt() + "-" + hours.closesAt(), null));
            }
        });
        return issues;
    }

    private List<ValidationIssue> validateWeather(TravelPlanningContext context, ItineraryPlan plan) {
        Set<String> outdoorPoiIds = context.validationFacts().places().stream()
                .filter(ValidationPoiFact::outdoor)
                .map(fact -> fact.place().poiId())
                .collect(Collectors.toUnmodifiableSet());
        if (outdoorPoiIds.isEmpty()) return List.of();
        Map<LocalDate, WeatherResult.DailyForecast> forecasts = context.validationFacts().weather() == null
                ? Map.of()
                : context.validationFacts().weather().dailyForecasts().stream()
                .collect(Collectors.toMap(WeatherResult.DailyForecast::date, Function.identity(),
                        (first, ignored) -> first));
        List<ValidationIssue> issues = new ArrayList<>();
        forEachItem(plan, (date, index, item) -> {
            if (!outdoorPoiIds.contains(item.poiId())) return;
            WeatherResult.DailyForecast forecast = forecasts.get(date);
            if (forecast == null) {
                // 没有天气数据时不能输出“天气良好”或“存在恶劣天气”等确定结论。
                issues.add(issue("WEATHER_DATA_MISSING", IssueSeverity.WARNING, date, index, item.poiId(),
                        "weather data is unavailable for this outdoor activity", null, null));
            } else if (forecast.precipitationProbabilityPercent()
                    >= ADVERSE_PRECIPITATION_PROBABILITY_PERCENT
                    || forecast.precipitationMillimeters() >= ADVERSE_PRECIPITATION_MILLIMETERS) {
                issues.add(issue("WEATHER_OUTDOOR_RISK", IssueSeverity.WARNING, date, index, item.poiId(),
                        "adverse weather may conflict with an outdoor activity",
                        forecast.precipitationProbabilityPercent() + "% precipitation probability, "
                                + forecast.precipitationMillimeters() + " mm", null));
            }
        });
        return issues;
    }

    private static TravelRequirement requirement(TravelPlanningContext context) {
        return context.resolvedRequirement().requirement();
    }

    private static void forEachItem(ItineraryPlan plan, ItemConsumer consumer) {
        for (ItineraryDay day : plan.days()) {
            for (int index = 0; index < day.items().size(); index++) {
                consumer.accept(day.date(), index, day.items().get(index));
            }
        }
    }

    private static ValidationIssue issue(
            String code, IssueSeverity severity, LocalDate date, Integer itemIndex, String poiId,
            String message, String knownValue, String requiredAdjustment) {
        return new ValidationIssue(code, severity, date, itemIndex, poiId, message, knownValue, requiredAdjustment);
    }

    private static Map<TravelPace, DailyIntensityLimit> intensityLimits() {
        EnumMap<TravelPace, DailyIntensityLimit> limits = new EnumMap<>(TravelPace.class);
        limits.put(TravelPace.RELAXED, new DailyIntensityLimit(4, 480));
        limits.put(TravelPace.MODERATE, new DailyIntensityLimit(6, 600));
        limits.put(TravelPace.COMPACT, new DailyIntensityLimit(8, 720));
        return Map.copyOf(limits);
    }

    private record DailyIntensityLimit(int maximumActivities, int maximumActivityMinutes) {
    }

    private record RouteKey(
            String fromPoiId,
            String toPoiId,
            com.wyx.travelagent.tools.model.TravelMode mode) {
    }

    private record CostFactKey(
            String poiId, BigDecimal minimum, BigDecimal maximum,
            com.wyx.travelagent.planning.model.CostEvidenceType evidenceType, Long chunkId) {
        private static CostFactKey from(CostFact fact) {
            return from(fact.poiId(), fact.estimate());
        }

        private static CostFactKey from(String poiId, CostEstimate estimate) {
            return new CostFactKey(poiId, normalized(estimate.minimum()), normalized(estimate.maximum()),
                    estimate.evidenceType(), estimate.knowledgeChunkId());
        }

        private static BigDecimal normalized(BigDecimal amount) {
            return amount == null ? null : amount.stripTrailingZeros();
        }
    }

    @FunctionalInterface
    private interface ItemConsumer {
        void accept(LocalDate date, int itemIndex, ItineraryItem item);
    }
}
