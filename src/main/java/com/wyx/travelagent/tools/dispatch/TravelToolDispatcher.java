package com.wyx.travelagent.tools.dispatch;

import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.TravelMode;
import com.wyx.travelagent.tools.port.PlaceProvider;
import com.wyx.travelagent.tools.port.RouteProvider;
import com.wyx.travelagent.tools.port.WeatherProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class TravelToolDispatcher {
    private final ToolPlanValidator validator;
    private final PlaceProvider placeProvider;
    private final RouteProvider routeProvider;
    private final WeatherProvider weatherProvider;
    private final CacheManager cacheManager;

    public TravelToolDispatcher(
            ToolPlanValidator validator,
            PlaceProvider placeProvider,
            RouteProvider routeProvider,
            WeatherProvider weatherProvider,
            CacheManager cacheManager) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.placeProvider = Objects.requireNonNull(placeProvider, "placeProvider");
        this.routeProvider = Objects.requireNonNull(routeProvider, "routeProvider");
        this.weatherProvider = Objects.requireNonNull(weatherProvider, "weatherProvider");
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
    }

    public ToolExecutionBundle execute(ToolExecutionPolicy policy, ToolPlan plan) {
        ValidatedToolPlan valid = validator.validate(policy, plan);
        return execute(valid);
    }

    private ToolExecutionBundle execute(ValidatedToolPlan plan) {
        List<ToolExecutionBundle.Execution> executions = new ArrayList<>(plan.actions().size());
        Map<String, Integer> calls = new LinkedHashMap<>();
        calls.put("place", 0);
        calls.put("route", 0);
        calls.put("weather", 0);

        int attemptedActions = 0;
        for (ValidatedToolPlan.ValidatedToolAction action : plan.actions()) {
            if (attemptedActions++ >= plan.policy().maxToolCalls()) {
                break;
            }
            executions.add(executeAction(action, calls));
        }
        return new ToolExecutionBundle(executions, calls);
    }

    private ToolExecutionBundle.Execution executeAction(
            ValidatedToolPlan.ValidatedToolAction action, Map<String, Integer> calls) {
        Map<String, Object> arguments = action.arguments();
        return switch (action.type()) {
            case SEARCH_POI -> cached("poi", poiKey(arguments), action.type(), calls, "place", () ->
                    placeProvider.search(text(arguments, "city"), text(arguments, "keyword"), integer(arguments, "limit")));
            case GEOCODE -> cached("geocode", geocodeKey(arguments), action.type(), calls, "place", () ->
                    placeProvider.geocode(text(arguments, "city"), text(arguments, "address")));
            case WALKING_ROUTE, DRIVING_ROUTE, TRANSIT_ROUTE -> cached(
                    "route", routeKey(arguments), action.type(), calls, "route", () -> routeProvider.route(
                            text(arguments, "city"),
                            place(arguments, "origin"),
                            place(arguments, "destination"),
                            mode(arguments, "mode")));
            case WEATHER_FORECAST -> cached("weather", weatherKey(arguments), action.type(), calls, "weather", () -> {
                LocalDate date = date(arguments, "date");
                return weatherProvider.forecast(point(arguments, "location"), date, date);
            }).withWeatherCoordinates(point(arguments, "location").coordinateSystem(),
                    weatherProvider.requestCoordinateSystem(point(arguments, "location")));
        };
    }

    private ToolExecutionBundle.Execution cached(
            String cacheName,
            String key,
            TravelToolType type,
            Map<String, Integer> calls,
            String providerCounter,
            Supplier<ToolResult<?>> providerCall) {
        Cache cache = cache(cacheName);
        if (cache != null) {
            try {
                Cache.ValueWrapper wrapper = cache.get(key);
                if (wrapper != null && wrapper.get() instanceof ToolResult<?> result && result.success()) {
                    return new ToolExecutionBundle.Execution(type, result, true);
                }
            } catch (RuntimeException ignored) {
                // Redis is an optional acceleration layer. Provider execution is the fallback.
            }
        }

        calls.compute(providerCounter, (ignored, count) -> count == null ? 1 : count + 1);
        ToolResult<?> result = providerCall.get();
        if (cache != null && result.success()) {
            try {
                cache.put(key, result);
            } catch (RuntimeException ignored) {
                // A successful provider result remains usable when Redis writes fail.
            }
        }
        return new ToolExecutionBundle.Execution(type, result, false);
    }

    private Cache cache(String name) {
        try {
            return cacheManager.getCache(name);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String poiKey(Map<String, Object> arguments) {
        return "travel:poi:" + ToolPlanValidator.canonical(text(arguments, "city")) + ":"
                + hash(ToolPlanValidator.canonical(text(arguments, "keyword"))
                + "\u0000limit=" + integer(arguments, "limit"));
    }

    private static String geocodeKey(Map<String, Object> arguments) {
        String qualifiedAddress = ToolPlanValidator.canonical(text(arguments, "city")) + "\u0000"
                + ToolPlanValidator.canonical(text(arguments, "address"));
        return "travel:geocode:" + hash(qualifiedAddress);
    }

    private static String routeKey(Map<String, Object> arguments) {
        String cityQualifiedOrigin = ToolPlanValidator.canonical(text(arguments, "city")) + "\u0000"
                + placeIdentity(place(arguments, "origin"));
        return "travel:route:" + hash(cityQualifiedOrigin) + ":"
                + hash(placeIdentity(place(arguments, "destination"))) + ":" + mode(arguments, "mode").name();
    }

    private static String weatherKey(Map<String, Object> arguments) {
        GeoPoint location = point(arguments, "location");
        return "travel:weather:" + ToolPlanValidator.canonical(text(arguments, "city")) + ":"
                + date(arguments, "date") + ":" + hash(Double.toString(location.longitude()) + "|"
                + Double.toString(location.latitude()) + "|" + location.coordinateSystem().name());
    }

    private static String placeIdentity(PlaceCandidate place) {
        return ToolPlanValidator.canonical(Objects.toString(place.poiId(), "")) + "|"
                + Double.toString(place.location().longitude()) + "|"
                + Double.toString(place.location().latitude()) + "|"
                + place.location().coordinateSystem().name();
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String text(Map<String, Object> arguments, String name) {
        return (String) arguments.get(name);
    }

    private static int integer(Map<String, Object> arguments, String name) {
        return (Integer) arguments.get(name);
    }

    private static PlaceCandidate place(Map<String, Object> arguments, String name) {
        return (PlaceCandidate) arguments.get(name);
    }

    private static TravelMode mode(Map<String, Object> arguments, String name) {
        return (TravelMode) arguments.get(name);
    }

    private static GeoPoint point(Map<String, Object> arguments, String name) {
        return (GeoPoint) arguments.get(name);
    }

    private static LocalDate date(Map<String, Object> arguments, String name) {
        return (LocalDate) arguments.get(name);
    }
}
