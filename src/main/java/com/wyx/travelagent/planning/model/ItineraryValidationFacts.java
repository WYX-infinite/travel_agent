package com.wyx.travelagent.planning.model;

import com.wyx.travelagent.knowledge.retrieval.KnowledgeReference;
import com.wyx.travelagent.tools.model.WeatherResult;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 业务问题：冻结一次行程生成与校验可使用的 POI、路线、费用、天气和知识白名单事实。
 * 输入只接受任务十/十一之后的内部领域对象，输出为稳定排序且不可变的事实集合；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十提供工具事实和知识引用，任务十二消费本快照，后续任务十三仅可把它交给修正流程；本类型不负责工具调用、Prompt 拼接、校验执行或自动修正。
 *
 * @param places 允许使用的 POI 事实，不得为 {@code null} 或重复 poiId
 * @param routes POI ID 关联的路线事实，不得为 {@code null}
 * @param costs 可信费用事实，不得为 {@code null}
 * @param weather 可缺失天气结果；{@code null} 表示没有天气事实
 * @param routeBufferMinutes 非负确定性路线缓冲分钟数
 * @param knowledgeReferences 本次允许的知识引用，不得为 {@code null}
 */
public record ItineraryValidationFacts(
        List<ValidationPoiFact> places,
        List<RouteFact> routes,
        List<CostFact> costs,
        WeatherResult weather,
        int routeBufferMinutes,
        List<KnowledgeReference> knowledgeReferences) {

    /**
     * 创建稳定不可变的验证事实快照。
     * 所有列表均不得为空引用或含 null，POI ID 不得重复，缓冲不得为负；空列表与缺失天气均有明确语义。
     * 构造只防御复制并排序，不访问模型、数据库、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当任一列表或元素为空
     * @throws IllegalArgumentException 当 POI ID、天气日期重复或缓冲为负
     */
    public ItineraryValidationFacts {
        places = sortedCopy(places, ValidationPoiFact.DETERMINISTIC_ORDER, "places");
        routes = sortedCopy(routes, RouteFact.DETERMINISTIC_ORDER, "routes");
        costs = sortedCopy(costs, CostFact.DETERMINISTIC_ORDER, "costs");
        knowledgeReferences = sortedCopy(knowledgeReferences,
                Comparator.comparingDouble(KnowledgeReference::score).reversed()
                        .thenComparing(KnowledgeReference::chunkId), "knowledgeReferences");
        if (weather != null) {
            List<WeatherResult.DailyForecast> forecasts = weather.dailyForecasts().stream()
                    .sorted(Comparator.comparing(WeatherResult.DailyForecast::date))
                    .toList();
            Set<java.time.LocalDate> uniqueWeatherDates = new HashSet<>();
            for (WeatherResult.DailyForecast forecast : forecasts) {
                if (!uniqueWeatherDates.add(forecast.date())) {
                    throw new IllegalArgumentException("duplicate weather date");
                }
            }
            weather = new WeatherResult(forecasts);
        }
        if (routeBufferMinutes < 0) throw new IllegalArgumentException("routeBufferMinutes");
        Set<String> uniquePoiIds = new HashSet<>();
        for (ValidationPoiFact place : places) {
            if (!uniquePoiIds.add(place.place().poiId())) throw new IllegalArgumentException("duplicate poiId");
        }
    }

    /**
     * 创建兼容任务十一旧构造方式的空事实快照。
     *
     * @return 所有列表为空、天气缺失、缓冲为零的不可变对象
     * 无输入且不抛业务异常；本方法不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public static ItineraryValidationFacts empty() {
        return new ItineraryValidationFacts(List.of(), List.of(), List.of(), null, 0, List.of());
    }

    private static <T> List<T> sortedCopy(List<T> source, Comparator<? super T> comparator, String field) {
        Objects.requireNonNull(source, field);
        source.forEach(value -> Objects.requireNonNull(value, field + " element"));
        return source.stream().sorted(comparator).toList();
    }
}
