package com.wyx.travelagent.tools.amap;

import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.model.TravelMode;
import com.wyx.travelagent.tools.model.TravelSegment;
import com.wyx.travelagent.tools.port.RouteProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class AmapRouteProvider implements RouteProvider {
    private static final String PROVIDER = "amap";
    private static final Set<String> RATE_LIMIT_INFO_CODES = Set.of(
            "10003", "10004", "10010", "10014", "10015", "10019", "10020", "10021",
            "10029", "10044", "10045");

    private final RestClient restClient;
    private final AmapProperties properties;
    private final Clock clock;

    public AmapRouteProvider(RestClient restClient, AmapProperties properties, Clock clock) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ToolResult<TravelSegment> route(String city, PlaceCandidate from, PlaceCandidate to, TravelMode mode) {
        if (from == null || to == null || from.location() == null || to.location() == null) {
            return failure("INVALID_COORDINATE", "route coordinates are required");
        }
        if (from.location().coordinateSystem() != CoordinateSystem.GCJ02
                || to.location().coordinateSystem() != CoordinateSystem.GCJ02) {
            return failure("COORDINATE_SYSTEM_UNSUPPORTED", "map routes require GCJ-02 coordinates");
        }
        if (mode == null || isBlank(city)) {
            return failure("INVALID_PROVIDER_REQUEST", "route request is invalid");
        }
        if (isBlank(properties.apiKey())) {
            return failure("PROVIDER_UNAVAILABLE", "map provider is not configured");
        }

        try {
            RouteResponse response = restClient.get()
                    .uri(builder -> buildRouteUri(builder, city, from, to, mode))
                    .retrieve()
                    .body(RouteResponse.class);
            return mapRouteResponse(response, mode);
        } catch (ResourceAccessException exception) {
            return failure("PROVIDER_TIMEOUT", "map provider timed out");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return failure("PROVIDER_RATE_LIMITED", "map provider rate limit exceeded");
            }
            return failure("PROVIDER_UNAVAILABLE", "map provider is unavailable");
        } catch (RestClientException | IllegalArgumentException exception) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
    }

    private java.net.URI buildRouteUri(UriBuilder builder, String city, PlaceCandidate from, PlaceCandidate to, TravelMode mode) {
        UriBuilder request = builder.path(endpoint(mode))
                .queryParam("key", properties.apiKey())
                .queryParam("origin", coordinate(from.location()))
                .queryParam("destination", coordinate(to.location()))
                .queryParam("output", "JSON");
        if (mode == TravelMode.WALKING) {
            addPoiIds(request, from, to, "origin_id", "destination_id");
        } else if (mode == TravelMode.DRIVING || mode == TravelMode.TAXI) {
            addPoiIds(request, from, to, "originid", "destinationid");
        }
        if (mode == TravelMode.TRANSIT) {
            request.queryParam("city", city);
        }
        return request.build();
    }

    private static void addPoiIds(UriBuilder request, PlaceCandidate from, PlaceCandidate to, String originParameter, String destinationParameter) {
        if (from.poiId() != null) {
            request.queryParam(originParameter, from.poiId());
        }
        if (to.poiId() != null) {
            request.queryParam(destinationParameter, to.poiId());
        }
    }

    private ToolResult<TravelSegment> mapRouteResponse(RouteResponse response, TravelMode mode) {
        if (response == null) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
        if (!"1".equals(response.status())) {
            return amapBusinessFailure(response.infocode());
        }
        if (response.route() == null) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
        List<RoutePath> paths = mode == TravelMode.TRANSIT ? response.route().transits() : response.route().paths();
        if (paths == null) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
        if (paths.isEmpty()) {
            return failure("ROUTE_NOT_FOUND", "route was not found");
        }
        RoutePath path = paths.getFirst();
        if (path == null) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
        long distanceMeters = parseNonNegative(path.distance());
        long durationSeconds = parseNonNegative(path.duration());
        long durationMinutes = durationSeconds / 60L + (durationSeconds % 60L == 0L ? 0L : 1L);
        TravelSegment segment = new TravelSegment(mode, distanceMeters, durationMinutes, true);
        return ToolResult.success(PROVIDER, now(), segment);
    }

    private static long parseNonNegative(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("missing numeric fact");
        }
        long parsed = Long.parseLong(value);
        if (parsed < 0L) {
            throw new IllegalArgumentException("negative numeric fact");
        }
        return parsed;
    }

    private static String endpoint(TravelMode mode) {
        return switch (mode) {
            case WALKING -> "/v3/direction/walking";
            case TRANSIT -> "/v3/direction/transit/integrated";
            case DRIVING, TAXI -> "/v3/direction/driving";
        };
    }

    private static String coordinate(GeoPoint point) {
        return String.format(Locale.ROOT, "%.6f,%.6f", point.longitude(), point.latitude());
    }

    private ToolResult<TravelSegment> failure(String code, String message) {
        return ToolResult.failure(PROVIDER, now(), code, message);
    }

    private ToolResult<TravelSegment> amapBusinessFailure(String infoCode) {
        if (isBlank(infoCode)) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
        if (RATE_LIMIT_INFO_CODES.contains(infoCode)) {
            return failure("PROVIDER_RATE_LIMITED", "map provider rate limit exceeded");
        }
        return failure("PROVIDER_UNAVAILABLE", "map provider is unavailable");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record RouteResponse(String status, String infocode, RouteDetails route) {}

    private record RouteDetails(List<RoutePath> paths, List<RoutePath> transits) {}

    private record RoutePath(String distance, String duration) {}
}
