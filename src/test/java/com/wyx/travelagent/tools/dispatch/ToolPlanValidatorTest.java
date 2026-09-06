package com.wyx.travelagent.tools.dispatch;

import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.TravelMode;
import com.wyx.travelagent.tools.model.TravelSegment;
import com.wyx.travelagent.tools.model.WeatherResult;
import com.wyx.travelagent.tools.port.PlaceProvider;
import com.wyx.travelagent.tools.port.RouteProvider;
import com.wyx.travelagent.tools.port.WeatherProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cache.support.NoOpCacheManager;

import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolPlanValidatorTest {
    private static final String CITY = "杭州";
    private final ToolPlanValidator validator = new ToolPlanValidator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPlans")
    void rejectsInvalidPlans(String name, ToolExecutionPolicy policy, ToolPlan plan, String errorCode) {
        assertThatThrownBy(() -> validator.validate(policy, plan))
                .isInstanceOf(ToolPlanValidator.ValidationException.class)
                .extracting("code")
                .isEqualTo(errorCode);
    }

    static Stream<Arguments> invalidPlans() {
        List<ToolAction> tooManyKeywords = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            tooManyKeywords.add(search("关键词" + index, 10));
        }
        List<ToolAction> withNullAction = new ArrayList<>();
        withNullAction.add(null);
        Map<String, Object> urlArgument = new LinkedHashMap<>();
        urlArgument.put("city", CITY);
        urlArgument.put("keyword", "西湖");
        urlArgument.put("limit", 10);
        urlArgument.put("endpointUrl", "not-even-a-url");

        return Stream.of(
                Arguments.of("城市不一致", policy(10), plan(search("上海", "西湖", 10)), "DESTINATION_MISMATCH"),
                Arguments.of("POI 关键词超过五组", policy(10), new ToolPlan(CITY, tooManyKeywords), "POI_KEYWORD_LIMIT_EXCEEDED"),
                Arguments.of("单组 POI 结果超过十条", policy(10), plan(search("西湖", 11)), "POI_RESULT_LIMIT_EXCEEDED"),
                Arguments.of("不支持的工具名", policy(10), plan(new ToolAction("FETCH_URL", Map.of())), "UNSUPPORTED_TOOL"),
                Arguments.of("路线交通方式非法", policy(10), plan(route("WALKING_ROUTE", "FLYING")), "INVALID_TRAVEL_MODE"),
                Arguments.of("参数值包含 URL", policy(10), plan(search("https://evil.example/query", 10)), "URL_NOT_ALLOWED"),
                Arguments.of("参数值包含无 scheme 域名", policy(10), plan(search("evil.io/path", 10)), "URL_NOT_ALLOWED"),
                Arguments.of("参数值包含 IPv4 URL", policy(10), plan(search("192.0.2.1/path", 10)), "URL_NOT_ALLOWED"),
                Arguments.of("参数值包含 mailto URI", policy(10), plan(search("mailto:user@example.dev", 10)), "URL_NOT_ALLOWED"),
                Arguments.of("参数值包含 scheme-relative URL", policy(10), plan(search("//evil.example/path", 10)), "URL_NOT_ALLOWED"),
                Arguments.of("参数值包含 Unicode IRI", policy(10), plan(search("例子.公司/路径", 10)), "URL_NOT_ALLOWED"),
                Arguments.of("参数值包含中文句号 IRI", policy(10), plan(search("例子。公司/路径", 10)), "URL_NOT_ALLOWED"),
                Arguments.of("参数值包含全角 URL", policy(10), plan(search("ｈｔｔｐ：／／ｅｖｉｌ．ｃｏｍ／path", 10)), "URL_NOT_ALLOWED"),
                Arguments.of("强类型地点内部包含 URL", policy(10), plan(routeWithUnsafePlace()), "URL_NOT_ALLOWED"),
                Arguments.of("出现 URL 型参数", policy(10), plan(new ToolAction("SEARCH_POI", urlArgument)), "URL_NOT_ALLOWED"),
                Arguments.of("超大整数不能截断为合法 limit", policy(10), plan(new ToolAction("SEARCH_POI", Map.of(
                        "city", CITY, "keyword", "西湖", "limit", 4_294_967_297L))), "POI_RESULT_LIMIT_EXCEEDED"),
                Arguments.of("总调用数超过策略", policy(1), new ToolPlan(CITY, List.of(search("西湖", 10), search("博物馆", 10))), "TOOL_CALL_LIMIT_EXCEEDED"),
                Arguments.of("空计划", policy(10), new ToolPlan(CITY, List.of()), "EMPTY_PLAN"),
                Arguments.of("空动作", policy(10), new ToolPlan(CITY, withNullAction), "INVALID_ACTION"),
                Arguments.of("缺少必要参数", policy(10), plan(new ToolAction("GEOCODE", Map.of("city", CITY))), "MISSING_PARAMETER")
        );
    }

    @Test
    void returnsValidatedPlanOnlyAfterAllActionsPassValidation() {
        ToolPlan plan = new ToolPlan("  杭州  ", List.of(
                search(" 西湖 ", 10),
                new ToolAction("WEATHER_FORECAST", Map.of(
                        "city", "杭州",
                        "location", point(),
                        "date", LocalDate.of(2026, 9, 10))))) ;

        ValidatedToolPlan validated = validator.validate(policy(4), plan);

        assertThat(validated.destinationCity()).isEqualTo(CITY);
        assertThat(validated.actions()).hasSize(2);
        assertThat(validated.actions().getFirst().type()).isEqualTo(TravelToolType.SEARCH_POI);
        assertThat(ValidatedToolPlan.class.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    void validationFailurePreventsEveryProviderCall() {
        CountingProviders providers = new CountingProviders();
        TravelToolDispatcher dispatcher = new TravelToolDispatcher(
                validator, providers, providers, providers, new NoOpCacheManager());
        ToolPlan invalid = plan(search("上海", "西湖", 10));

        assertThatThrownBy(() -> dispatcher.execute(policy(10), invalid))
                .isInstanceOf(ToolPlanValidator.ValidationException.class);

        assertThat(providers.totalCalls()).isZero();
    }

    private static ToolExecutionPolicy policy(int maxCalls) {
        return new ToolExecutionPolicy(maxCalls);
    }

    private static ToolPlan plan(ToolAction action) {
        return new ToolPlan(CITY, List.of(action));
    }

    private static ToolAction search(String keyword, int limit) {
        return search(CITY, keyword, limit);
    }

    private static ToolAction search(String city, String keyword, int limit) {
        return new ToolAction("SEARCH_POI", Map.of("city", city, "keyword", keyword, "limit", limit));
    }

    private static ToolAction route(String toolName, String mode) {
        return new ToolAction(toolName, Map.of(
                "city", CITY,
                "origin", place("A", 120.1, 30.1),
                "destination", place("B", 120.2, 30.2),
                "mode", mode));
    }

    private static ToolAction routeWithUnsafePlace() {
        PlaceCandidate unsafe = new PlaceCandidate(
                "A", "起点", "https://evil.example/private-address", point());
        return new ToolAction("WALKING_ROUTE", Map.of(
                "city", CITY,
                "origin", unsafe,
                "destination", place("B", 120.2, 30.2),
                "mode", "WALKING"));
    }

    private static PlaceCandidate place(String id, double longitude, double latitude) {
        return new PlaceCandidate(id, id, CITY, new GeoPoint(longitude, latitude, CoordinateSystem.GCJ02));
    }

    private static GeoPoint point() {
        return new GeoPoint(120.1, 30.1, CoordinateSystem.WGS84);
    }

    private static final class CountingProviders implements PlaceProvider, RouteProvider, WeatherProvider {
        private int calls;

        @Override
        public ToolResult<List<PlaceCandidate>> search(String city, String keyword, int limit) {
            calls++;
            return ToolResult.success("fake-place", LocalDateTime.now(), List.of());
        }

        @Override
        public ToolResult<PlaceCandidate> geocode(String city, String address) {
            calls++;
            return ToolResult.success("fake-place", LocalDateTime.now(), place("G", 120.1, 30.1));
        }

        @Override
        public ToolResult<TravelSegment> route(String city, PlaceCandidate from, PlaceCandidate to, TravelMode mode) {
            calls++;
            return ToolResult.success("fake-route", LocalDateTime.now(), new TravelSegment(mode, 1L, 1L, true));
        }

        @Override
        public ToolResult<WeatherResult> forecast(GeoPoint location, LocalDate startDate, LocalDate endDate) {
            calls++;
            return ToolResult.success("fake-weather", LocalDateTime.now(), new WeatherResult(List.of(
                    new WeatherResult.DailyForecast(startDate, 20, 25, 0, 0))));
        }

        int totalCalls() {
            return calls;
        }
    }
}
