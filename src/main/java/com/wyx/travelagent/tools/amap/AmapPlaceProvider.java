package com.wyx.travelagent.tools.amap;

import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.ToolResult;
import com.wyx.travelagent.tools.port.PlaceProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AmapPlaceProvider implements PlaceProvider {
    private static final String PROVIDER = "amap";
    private static final Set<String> RATE_LIMIT_INFO_CODES = Set.of(
            "10003", "10004", "10010", "10014", "10015", "10019", "10020", "10021",
            "10029", "10044", "10045");

    private final RestClient restClient;
    private final AmapProperties properties;
    private final Clock clock;

    public AmapPlaceProvider(RestClient restClient, AmapProperties properties, Clock clock) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ToolResult<List<PlaceCandidate>> search(String city, String keyword, int limit) {
        if (isBlank(city) || isBlank(keyword) || limit <= 0 || limit > 25) {
            return failure("INVALID_PROVIDER_REQUEST", "place search request is invalid");
        }
        if (isBlank(properties.apiKey())) {
            return failure("PROVIDER_UNAVAILABLE", "map provider is not configured");
        }

        try {
            PlaceSearchResponse response = restClient.get()
                    .uri(builder -> builder.path("/v3/place/text")
                            .queryParam("key", properties.apiKey())
                            .queryParam("keywords", keyword)
                            .queryParam("city", city)
                            .queryParam("citylimit", true)
                            .queryParam("offset", limit)
                            .queryParam("page", 1)
                            .queryParam("extensions", "base")
                            .queryParam("output", "JSON")
                            .build())
                    .retrieve()
                    .body(PlaceSearchResponse.class);
            return mapSearchResponse(response);
        } catch (ResourceAccessException exception) {
            return failure("PROVIDER_TIMEOUT", "map provider timed out");
        } catch (RestClientResponseException exception) {
            return transportFailure(exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
    }

    @Override
    public ToolResult<PlaceCandidate> geocode(String city, String address) {
        if (isBlank(city) || isBlank(address)) {
            return failure("INVALID_PROVIDER_REQUEST", "geocode request is invalid");
        }
        if (isBlank(properties.apiKey())) {
            return failure("PROVIDER_UNAVAILABLE", "map provider is not configured");
        }

        try {
            GeocodeResponse response = restClient.get()
                    .uri(builder -> builder.path("/v3/geocode/geo")
                            .queryParam("key", properties.apiKey())
                            .queryParam("address", address)
                            .queryParam("city", city)
                            .queryParam("output", "JSON")
                            .build())
                    .retrieve()
                    .body(GeocodeResponse.class);
            return mapGeocodeResponse(response, address);
        } catch (ResourceAccessException exception) {
            return failure("PROVIDER_TIMEOUT", "map provider timed out");
        } catch (RestClientResponseException exception) {
            return transportFailure(exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
    }

    private ToolResult<List<PlaceCandidate>> mapSearchResponse(PlaceSearchResponse response) {
        if (response == null) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
        if (!"1".equals(response.status())) {
            return amapBusinessFailure(response.infocode());
        }
        if (response.pois() == null) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
        List<PlaceCandidate> candidates = new ArrayList<>(response.pois().size());
        for (PoiResponse poi : response.pois()) {
            if (poi == null || isBlank(poi.id()) || isBlank(poi.name()) || isBlank(poi.address())) {
                return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
            }
            candidates.add(new PlaceCandidate(
                    poi.id(), poi.name(), poi.address(), parseGcj02(poi.location())));
        }
        return ToolResult.success(PROVIDER, now(), candidates);
    }

    private ToolResult<PlaceCandidate> mapGeocodeResponse(GeocodeResponse response, String requestedAddress) {
        if (response == null) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
        if (!"1".equals(response.status())) {
            return amapBusinessFailure(response.infocode());
        }
        if (response.geocodes() == null) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
        if (response.geocodes().isEmpty()) {
            return failure("PLACE_NOT_FOUND", "address was not found");
        }
        GeocodeEntry entry = response.geocodes().getFirst();
        if (entry == null || isBlank(entry.formattedAddress())) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
        PlaceCandidate candidate = new PlaceCandidate(
                null, requestedAddress, entry.formattedAddress(), parseGcj02(entry.location()));
        return ToolResult.success(PROVIDER, now(), candidate);
    }

    private GeoPoint parseGcj02(String location) {
        if (isBlank(location)) {
            throw new IllegalArgumentException("missing coordinate");
        }
        String[] coordinates = location.split(",", -1);
        if (coordinates.length != 2) {
            throw new IllegalArgumentException("invalid coordinate");
        }
        double longitude = Double.parseDouble(coordinates[0].trim());
        double latitude = Double.parseDouble(coordinates[1].trim());
        return new GeoPoint(longitude, latitude, CoordinateSystem.GCJ02);
    }

    private <T> ToolResult<T> transportFailure(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return failure("PROVIDER_RATE_LIMITED", "map provider rate limit exceeded");
        }
        return failure("PROVIDER_UNAVAILABLE", "map provider is unavailable");
    }

    private <T> ToolResult<T> amapBusinessFailure(String infoCode) {
        if (isBlank(infoCode)) {
            return failure("INVALID_PROVIDER_RESPONSE", "map provider returned an invalid response");
        }
        if (RATE_LIMIT_INFO_CODES.contains(infoCode)) {
            return failure("PROVIDER_RATE_LIMITED", "map provider rate limit exceeded");
        }
        return failure("PROVIDER_UNAVAILABLE", "map provider is unavailable");
    }

    private <T> ToolResult<T> failure(String code, String message) {
        return ToolResult.failure(PROVIDER, now(), code, message);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record PlaceSearchResponse(String status, String infocode, List<PoiResponse> pois) {}

    private record PoiResponse(String id, String name, String address, String location) {}

    private record GeocodeResponse(String status, String infocode, List<GeocodeEntry> geocodes) {}

    private record GeocodeEntry(
            @com.fasterxml.jackson.annotation.JsonProperty("formatted_address") String formattedAddress,
            String location
    ) {}
}
