package com.wyx.travelagent.tools.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.tools.dispatch.ToolAction;
import com.wyx.travelagent.tools.dispatch.ToolExecutionBundle;
import com.wyx.travelagent.tools.dispatch.ToolExecutionPolicy;
import com.wyx.travelagent.tools.dispatch.ToolPlan;
import com.wyx.travelagent.tools.dispatch.ToolPlanValidator;
import com.wyx.travelagent.tools.dispatch.TravelToolDispatcher;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TravelCacheTest {
    private static final String CITY = "杭州";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 12, 0);

    @Test
    void configuresFourExactTtlsAndJsonThatRoundTripsRecordsAndJavaTime() {
        TravelCacheConfiguration configuration = new TravelCacheConfiguration();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var caches = configuration.cacheConfigurations(mapper);

        assertThat(caches).extractingByKey("poi").extracting(config -> config.getTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(caches).extractingByKey("geocode").extracting(config -> config.getTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(caches).extractingByKey("route").extracting(config -> config.getTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(caches).extractingByKey("weather").extracting(config -> config.getTtl()).isEqualTo(Duration.ofMinutes(30));

        ToolResult<PlaceCandidate> original = ToolResult.success("amap", NOW, place("A", 120.1, 30.1));
        ByteBuffer bytes = caches.get("geocode").getValueSerializationPair().write(original);
        Object restored = caches.get("geocode").getValueSerializationPair().read(bytes);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void cacheMissCallsProviderWritesSuccessAndCacheHitSkipsProvider() {
        CountingProviders providers = new CountingProviders();
        RecordingCacheManager caches = new RecordingCacheManager();
        TravelToolDispatcher dispatcher = dispatcher(providers, caches);
        ToolPlan plan = plan(search(" 西　湖 ", 10));

        ToolExecutionBundle first = dispatcher.execute(policy(), plan);
        ToolExecutionBundle second = dispatcher.execute(policy(), plan);

        assertThat(providers.placeCalls).isEqualTo(1);
        assertThat(caches.cache("poi").putCount).isEqualTo(1);
        assertThat(first.executions().getFirst().cacheHit()).isFalse();
        assertThat(second.executions().getFirst().cacheHit()).isTrue();
        assertThat(first.providerCallCounts()).containsEntry("place", 1);
        assertThat(second.providerCallCounts()).containsEntry("place", 0);
        assertThat(caches.cache("poi").lastKey)
                .startsWith("travel:poi:杭州:")
                .doesNotContain("西 湖")
                .doesNotContain("西湖");
    }

    @Test
    void cacheKeysAreStableAcrossDefaultLocalesAndInputNormalization() {
        Locale previous = Locale.getDefault();
        try {
            RecordingCacheManager firstCaches = new RecordingCacheManager();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            dispatcher(new CountingProviders(), firstCaches).execute(policy(), plan(search(" MUSEUM ", 10)));

            RecordingCacheManager secondCaches = new RecordingCacheManager();
            Locale.setDefault(Locale.JAPAN);
            dispatcher(new CountingProviders(), secondCaches).execute(policy(),
                    new ToolPlan("　杭州　", List.of(search("museum", 10))));

            assertThat(firstCaches.cache("poi").lastKey).isEqualTo(secondCaches.cache("poi").lastKey);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void routeModeIsPartOfKeyAndPreventsCrossModeCacheHits() {
        CountingProviders providers = new CountingProviders();
        RecordingCacheManager caches = new RecordingCacheManager();
        TravelToolDispatcher dispatcher = dispatcher(providers, caches);

        ToolExecutionBundle walking = dispatcher.execute(policy(), plan(route("WALKING_ROUTE", "WALKING")));
        String walkingKey = caches.cache("route").lastKey;
        ToolExecutionBundle driving = dispatcher.execute(policy(), plan(route("DRIVING_ROUTE", "DRIVING")));
        String drivingKey = caches.cache("route").lastKey;

        assertThat(walkingKey).endsWith(":WALKING");
        assertThat(drivingKey).endsWith(":DRIVING").isNotEqualTo(walkingKey);
        assertThat(providers.routeCalls).isEqualTo(2);
        assertThat(walking.executions().getFirst().cacheHit()).isFalse();
        assertThat(driving.executions().getFirst().cacheHit()).isFalse();
    }

    @Test
    void poiLimitIsPartOfKeyAndPreventsSmallerResultFromServingLargerRequest() {
        CountingProviders providers = new CountingProviders();
        RecordingCacheManager caches = new RecordingCacheManager();
        TravelToolDispatcher dispatcher = dispatcher(providers, caches);

        dispatcher.execute(policy(), plan(search("西湖", 1)));
        String oneResultKey = caches.cache("poi").lastKey;
        dispatcher.execute(policy(), plan(search("西湖", 10)));
        String tenResultsKey = caches.cache("poi").lastKey;

        assertThat(oneResultKey).isNotEqualTo(tenResultsKey);
        assertThat(providers.placeCalls).isEqualTo(2);
    }

    @Test
    void routeCityIsPartOfKeyForTransitRequests() {
        CountingProviders providers = new CountingProviders();
        RecordingCacheManager caches = new RecordingCacheManager();
        TravelToolDispatcher dispatcher = dispatcher(providers, caches);

        dispatcher.execute(policy(), new ToolPlan("杭州", List.of(route("杭州", "TRANSIT_ROUTE", "TRANSIT"))));
        String hangzhouKey = caches.cache("route").lastKey;
        dispatcher.execute(policy(), new ToolPlan("上海", List.of(route("上海", "TRANSIT_ROUTE", "TRANSIT"))));
        String shanghaiKey = caches.cache("route").lastKey;

        assertThat(hangzhouKey).isNotEqualTo(shanghaiKey);
        assertThat(providers.routeCalls).isEqualTo(2);
    }

    @Test
    void weatherLocationHashExtendsTheApprovedCityDateKeyWithoutCrossHits() {
        CountingProviders providers = new CountingProviders();
        RecordingCacheManager caches = new RecordingCacheManager();
        TravelToolDispatcher dispatcher = dispatcher(providers, caches);
        LocalDate date = LocalDate.of(2026, 9, 10);

        dispatcher.execute(policy(), plan(weather(new GeoPoint(120.1, 30.1, CoordinateSystem.WGS84), date)));
        String firstLocationKey = caches.cache("weather").lastKey;
        dispatcher.execute(policy(), plan(weather(new GeoPoint(120.2, 30.2, CoordinateSystem.WGS84), date)));
        String secondLocationKey = caches.cache("weather").lastKey;
        dispatcher.execute(policy(), plan(weather(new GeoPoint(120.1, 30.1, CoordinateSystem.WGS84), date)));

        assertThat(firstLocationKey).matches("travel:weather:杭州:2026-09-10:[0-9a-f]{64}");
        assertThat(secondLocationKey).isNotEqualTo(firstLocationKey);
        assertThat(providers.weatherCalls).isEqualTo(2);
    }

    @Test
    void failedProviderResultsAreNeverCached() {
        CountingProviders providers = new CountingProviders();
        providers.searchResult = ToolResult.failure("fake-place", NOW, "PROVIDER_UNAVAILABLE", "unavailable");
        RecordingCacheManager caches = new RecordingCacheManager();
        TravelToolDispatcher dispatcher = dispatcher(providers, caches);

        dispatcher.execute(policy(), plan(search("西湖", 10)));
        dispatcher.execute(policy(), plan(search("西湖", 10)));

        assertThat(providers.placeCalls).isEqualTo(2);
        assertThat(caches.cache("poi").putCount).isZero();
    }

    @Test
    void redisGetFailureFallsOpenToProviderAndSuccessfulResult() {
        CountingProviders providers = new CountingProviders();
        RecordingCacheManager caches = new RecordingCacheManager();
        caches.cache("poi").throwOnGet = true;

        ToolExecutionBundle bundle = dispatcher(providers, caches).execute(policy(), plan(search("西湖", 10)));

        assertThat(providers.placeCalls).isEqualTo(1);
        assertThat(bundle.executions().getFirst().result().success()).isTrue();
        assertThat(bundle.executions().getFirst().cacheHit()).isFalse();
    }

    @Test
    void redisPutFailureDoesNotFailSuccessfulProviderCall() {
        CountingProviders providers = new CountingProviders();
        RecordingCacheManager caches = new RecordingCacheManager();
        caches.cache("poi").throwOnPut = true;

        ToolExecutionBundle bundle = dispatcher(providers, caches).execute(policy(), plan(search("西湖", 10)));

        assertThat(providers.placeCalls).isEqualTo(1);
        assertThat(bundle.executions().getFirst().result().success()).isTrue();
    }

    @Test
    void geocodeAndWeatherUseOnlyHashedAddressOrApprovedCityDateKeyParts() {
        CountingProviders providers = new CountingProviders();
        RecordingCacheManager caches = new RecordingCacheManager();
        TravelToolDispatcher dispatcher = dispatcher(providers, caches);
        String privateAddress = "杭州市上城区湖滨街道一个很长的住宿地址";

        dispatcher.execute(policy(), plan(new ToolAction("GEOCODE", Map.of("city", CITY, "address", privateAddress))));
        String geocodeKey = caches.cache("geocode").lastKey;
        dispatcher.execute(policy(), plan(new ToolAction("WEATHER_FORECAST", Map.of(
                "city", CITY,
                "location", new GeoPoint(120.1, 30.1, CoordinateSystem.WGS84),
                "date", LocalDate.of(2026, 9, 10)))));
        String weatherKey = caches.cache("weather").lastKey;

        assertThat(geocodeKey).matches("travel:geocode:[0-9a-f]{64}").doesNotContain(privateAddress);
        assertThat(weatherKey).matches("travel:weather:杭州:2026-09-10:[0-9a-f]{64}");
        assertThat(providers.placeCalls).isEqualTo(1);
        assertThat(providers.weatherCalls).isEqualTo(1);
    }

    private static TravelToolDispatcher dispatcher(CountingProviders providers, CacheManager caches) {
        return new TravelToolDispatcher(new ToolPlanValidator(), providers, providers, providers, caches);
    }

    private static ToolExecutionPolicy policy() {
        return new ToolExecutionPolicy(10);
    }

    private static ToolPlan plan(ToolAction action) {
        return new ToolPlan(CITY, List.of(action));
    }

    private static ToolAction search(String keyword, int limit) {
        return new ToolAction("SEARCH_POI", Map.of("city", CITY, "keyword", keyword, "limit", limit));
    }

    private static ToolAction route(String toolName, String mode) {
        return route(CITY, toolName, mode);
    }

    private static ToolAction route(String city, String toolName, String mode) {
        return new ToolAction(toolName, Map.of(
                "city", city,
                "origin", place("A", 120.1, 30.1),
                "destination", place("B", 120.2, 30.2),
                "mode", mode));
    }

    private static ToolAction weather(GeoPoint location, LocalDate date) {
        return new ToolAction("WEATHER_FORECAST", Map.of(
                "city", CITY, "location", location, "date", date));
    }

    private static PlaceCandidate place(String id, double longitude, double latitude) {
        return new PlaceCandidate(id, id, CITY, new GeoPoint(longitude, latitude, CoordinateSystem.GCJ02));
    }

    private static final class CountingProviders implements PlaceProvider, RouteProvider, WeatherProvider {
        private int placeCalls;
        private int routeCalls;
        private int weatherCalls;
        private ToolResult<List<PlaceCandidate>> searchResult = ToolResult.success(
                "fake-place", NOW, List.of(place("P", 120.1, 30.1)));

        @Override
        public ToolResult<List<PlaceCandidate>> search(String city, String keyword, int limit) {
            placeCalls++;
            return searchResult;
        }

        @Override
        public ToolResult<PlaceCandidate> geocode(String city, String address) {
            placeCalls++;
            return ToolResult.success("fake-place", NOW, place("G", 120.1, 30.1));
        }

        @Override
        public ToolResult<TravelSegment> route(String city, PlaceCandidate from, PlaceCandidate to, TravelMode mode) {
            routeCalls++;
            return ToolResult.success("fake-route", NOW, new TravelSegment(mode, 100L, 2L, true));
        }

        @Override
        public ToolResult<WeatherResult> forecast(GeoPoint location, LocalDate startDate, LocalDate endDate) {
            weatherCalls++;
            return ToolResult.success("fake-weather", NOW, new WeatherResult(List.of(
                    new WeatherResult.DailyForecast(startDate, 20, 25, 10, 0.5))));
        }
    }

    private static final class RecordingCacheManager implements CacheManager {
        private final Map<String, RecordingCache> caches = new LinkedHashMap<>();

        RecordingCacheManager() {
            for (String name : List.of("poi", "geocode", "route", "weather")) {
                caches.put(name, new RecordingCache(name));
            }
        }

        @Override
        public Cache getCache(String name) {
            return caches.get(name);
        }

        @Override
        public Collection<String> getCacheNames() {
            return caches.keySet();
        }

        RecordingCache cache(String name) {
            return caches.get(name);
        }
    }

    private static final class RecordingCache extends ConcurrentMapCache {
        private int putCount;
        private String lastKey;
        private boolean throwOnGet;
        private boolean throwOnPut;

        RecordingCache(String name) {
            super(name);
        }

        @Override
        public ValueWrapper get(Object key) {
            lastKey = String.valueOf(key);
            if (throwOnGet) {
                throw new IllegalStateException("simulated redis get failure");
            }
            return super.get(key);
        }

        @Override
        public void put(Object key, Object value) {
            lastKey = String.valueOf(key);
            putCount++;
            if (throwOnPut) {
                throw new IllegalStateException("simulated redis put failure");
            }
            super.put(key, value);
        }
    }
}
