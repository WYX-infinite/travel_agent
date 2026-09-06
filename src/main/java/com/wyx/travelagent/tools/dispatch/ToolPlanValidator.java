package com.wyx.travelagent.tools.dispatch;

import com.wyx.travelagent.tools.model.CoordinateSystem;
import com.wyx.travelagent.tools.model.GeoPoint;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.TravelMode;

import java.text.Normalizer;
import java.net.IDN;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ToolPlanValidator {
    private static final int MAX_POI_KEYWORD_GROUPS = 5;
    private static final int MAX_POI_RESULTS = 10;
    private static final int MAX_CITY_LENGTH = 64;
    private static final int MAX_TEXT_LENGTH = 512;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern CITY = Pattern.compile("[\\p{L}\\p{N} \\-·]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern URI_SCHEME = Pattern.compile("(?i)\\b[a-z][a-z0-9+.-]*:[^\\s]+");
    private static final Pattern SCHEME_RELATIVE_URL = Pattern.compile("(?:^|\\s)//[^\\s]+");
    private static final Pattern IPV4_URL = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?(?:/[^\\s]*)?");
    private static final Pattern DOMAIN_CANDIDATE = Pattern.compile(
            "(?iu)([\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,62}[\\p{L}\\p{N}])?"
                    + "(?:\\.[\\p{L}](?:[\\p{L}\\p{N}-]{0,62}[\\p{L}\\p{N}])?)+)"
                    + "(?::\\d+)?(?:/[^\\s]*)?",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> URL_ARGUMENT_NAMES = Set.of(
            "url", "uri", "endpoint", "endpointurl", "baseurl", "host", "hostname");
    private static final Map<TravelToolType, Set<String>> ALLOWED_ARGUMENTS = Map.of(
            TravelToolType.SEARCH_POI, Set.of("city", "keyword", "limit"),
            TravelToolType.GEOCODE, Set.of("city", "address"),
            TravelToolType.WALKING_ROUTE, Set.of("city", "origin", "destination", "mode"),
            TravelToolType.DRIVING_ROUTE, Set.of("city", "origin", "destination", "mode"),
            TravelToolType.TRANSIT_ROUTE, Set.of("city", "origin", "destination", "mode"),
            TravelToolType.WEATHER_FORECAST, Set.of("city", "location", "date"));

    public ValidatedToolPlan validate(ToolExecutionPolicy policy, ToolPlan plan) {
        if (policy == null) {
            throw invalid("INVALID_POLICY", "tool execution policy is required");
        }
        if (plan == null) {
            throw invalid("EMPTY_PLAN", "tool plan is required");
        }
        String destinationCity = validatedCity(plan.destinationCity(), "INVALID_DESTINATION");
        if (plan.actions() == null || plan.actions().isEmpty()) {
            throw invalid("EMPTY_PLAN", "tool plan must contain at least one action");
        }
        if (plan.actions().size() > policy.maxToolCalls()) {
            throw invalid("TOOL_CALL_LIMIT_EXCEEDED", "tool plan exceeds the configured call limit");
        }

        int poiSearches = 0;
        List<ValidatedToolPlan.ValidatedToolAction> validatedActions = new ArrayList<>(plan.actions().size());
        for (ToolAction action : plan.actions()) {
            if (action == null) {
                throw invalid("INVALID_ACTION", "tool action is required");
            }
            TravelToolType type = parseToolType(action.toolName());
            if (type == TravelToolType.SEARCH_POI && ++poiSearches > MAX_POI_KEYWORD_GROUPS) {
                throw invalid("POI_KEYWORD_LIMIT_EXCEEDED", "too many POI keyword groups");
            }
            validatedActions.add(validateAction(destinationCity, type, action.arguments()));
        }
        return ValidatedToolPlan.create(VALIDATION_PROOF, destinationCity, policy, validatedActions);
    }

    private ValidatedToolPlan.ValidatedToolAction validateAction(
            String destinationCity, TravelToolType type, Map<String, Object> rawArguments) {
        if (rawArguments == null) {
            throw invalid("MISSING_PARAMETER", "tool arguments are required");
        }
        rejectUrls(rawArguments);
        Set<String> allowed = ALLOWED_ARGUMENTS.get(type);
        if (!allowed.containsAll(rawArguments.keySet())) {
            throw invalid("UNKNOWN_PARAMETER", "tool action contains an unsupported parameter");
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        String city = requiredText(rawArguments, "city", MAX_CITY_LENGTH);
        if (!canonical(destinationCity).equals(canonical(city))) {
            throw invalid("DESTINATION_MISMATCH", "tool city does not match the requested destination");
        }
        arguments.put("city", clean(city));

        switch (type) {
            case SEARCH_POI -> {
                arguments.put("keyword", requiredText(rawArguments, "keyword", MAX_TEXT_LENGTH));
                int limit = requiredInteger(rawArguments, "limit");
                if (limit <= 0 || limit > MAX_POI_RESULTS) {
                    throw invalid("POI_RESULT_LIMIT_EXCEEDED", "POI result limit must be between 1 and 10");
                }
                arguments.put("limit", limit);
            }
            case GEOCODE -> arguments.put("address", requiredText(rawArguments, "address", MAX_TEXT_LENGTH));
            case WALKING_ROUTE, DRIVING_ROUTE, TRANSIT_ROUTE -> validateRoute(type, rawArguments, arguments);
            case WEATHER_FORECAST -> {
                arguments.put("location", requiredGeoPoint(rawArguments.get("location"), "location"));
                arguments.put("date", requiredDate(rawArguments.get("date"), "date"));
            }
        }
        return new ValidatedToolPlan.ValidatedToolAction(type, arguments);
    }

    private void validateRoute(TravelToolType type, Map<String, Object> raw, Map<String, Object> target) {
        target.put("origin", requiredPlace(raw.get("origin"), "origin"));
        target.put("destination", requiredPlace(raw.get("destination"), "destination"));
        String rawMode = requiredText(raw, "mode", 32);
        TravelMode mode;
        try {
            mode = TravelMode.valueOf(canonical(rawMode).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("INVALID_TRAVEL_MODE", "route travel mode is unsupported");
        }
        TravelMode expected = switch (type) {
            case WALKING_ROUTE -> TravelMode.WALKING;
            case DRIVING_ROUTE -> TravelMode.DRIVING;
            case TRANSIT_ROUTE -> TravelMode.TRANSIT;
            default -> throw new IllegalStateException("not a route type");
        };
        if (mode != expected) {
            throw invalid("INVALID_TRAVEL_MODE", "route travel mode does not match the selected tool");
        }
        target.put("mode", mode);
    }

    private static TravelToolType parseToolType(String toolName) {
        if (toolName == null) {
            throw invalid("UNSUPPORTED_TOOL", "tool name is required");
        }
        try {
            return TravelToolType.valueOf(clean(toolName).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("UNSUPPORTED_TOOL", "tool name is not whitelisted");
        }
    }

    private static String validatedCity(Object value, String errorCode) {
        String city = text(value, "destinationCity", MAX_CITY_LENGTH);
        if (!CITY.matcher(city).matches()) {
            throw invalid(errorCode, "destination city format is invalid");
        }
        return city;
    }

    private static String requiredText(Map<String, Object> values, String name, int maxLength) {
        if (!values.containsKey(name)) {
            throw invalid("MISSING_PARAMETER", "required tool parameter is missing");
        }
        return text(values.get(name), name, maxLength);
    }

    private static String text(Object value, String name, int maxLength) {
        if (!(value instanceof String text)) {
            throw invalid("MISSING_PARAMETER", "required text parameter is missing");
        }
        String cleaned = clean(text);
        if (cleaned.isEmpty() || cleaned.length() > maxLength) {
            throw invalid("INVALID_PARAMETER", name + " is invalid");
        }
        return cleaned;
    }

    private static int requiredInteger(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof Number number)) {
            throw invalid("MISSING_PARAMETER", "required integer parameter is missing");
        }
        try {
            return new java.math.BigDecimal(number.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw invalid("POI_RESULT_LIMIT_EXCEEDED", "POI result limit must be between 1 and 10");
        }
    }

    private static LocalDate requiredDate(Object value, String name) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof String text) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeException ignored) {
                // Mapped to the stable validation error below.
            }
        }
        throw invalid("MISSING_PARAMETER", "required date parameter is missing: " + name);
    }

    private static PlaceCandidate requiredPlace(Object value, String name) {
        if (value instanceof PlaceCandidate place) {
            return place;
        }
        if (value instanceof Map<?, ?> map) {
            try {
                return new PlaceCandidate(
                        nullableString(map.get("poiId")),
                        requiredMapString(map, "name"),
                        requiredMapString(map, "address"),
                        requiredGeoPoint(map.get("location"), "location"));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw invalid("INVALID_PARAMETER", name + " is invalid");
            }
        }
        throw invalid("MISSING_PARAMETER", "required place parameter is missing: " + name);
    }

    private static GeoPoint requiredGeoPoint(Object value, String name) {
        if (value instanceof GeoPoint point) {
            return point;
        }
        if (value instanceof Map<?, ?> map) {
            try {
                double longitude = requiredNumber(map.get("longitude"));
                double latitude = requiredNumber(map.get("latitude"));
                CoordinateSystem system = CoordinateSystem.valueOf(
                        requiredMapString(map, "coordinateSystem").toUpperCase(Locale.ROOT));
                return new GeoPoint(longitude, latitude, system);
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw invalid("INVALID_PARAMETER", name + " is invalid");
            }
        }
        throw invalid("MISSING_PARAMETER", "required coordinate parameter is missing: " + name);
    }

    private static double requiredNumber(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("number required");
        }
        return number.doubleValue();
    }

    private static String requiredMapString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("string required");
        }
        return clean(text);
    }

    private static String nullableString(Object value) {
        return value == null ? null : requiredString(value);
    }

    private static String requiredString(Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("string required");
        }
        return clean(text);
    }

    private static void rejectUrls(Map<String, Object> arguments) {
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String normalizedName = entry.getKey() == null ? "" : canonical(entry.getKey()).replace("-", "").replace("_", "");
            if (URL_ARGUMENT_NAMES.contains(normalizedName)
                    || normalizedName.contains("url")
                    || normalizedName.contains("uri")
                    || normalizedName.contains("endpoint")
                    || normalizedName.contains("host")
                    || containsUrl(entry.getValue())) {
                throw invalid("URL_NOT_ALLOWED", "URLs and URL-like parameters are not allowed");
            }
        }
    }

    private static boolean containsUrl(Object value) {
        if (value instanceof String text) {
            String normalized = clean(text);
            return URI_SCHEME.matcher(normalized).find()
                    || SCHEME_RELATIVE_URL.matcher(normalized).find()
                    || IPV4_URL.matcher(normalized).find()
                    || containsDomain(normalized);
        }
        if (value instanceof PlaceCandidate place) {
            return containsUrl(place.poiId())
                    || containsUrl(place.name())
                    || containsUrl(place.address());
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().anyMatch(entry -> containsUrl(entry.getKey()) || containsUrl(entry.getValue()));
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsUrl(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsDomain(String value) {
        var matcher = DOMAIN_CANDIDATE.matcher(value);
        while (matcher.find()) {
            try {
                String ascii = IDN.toASCII(matcher.group(1), IDN.USE_STD3_ASCII_RULES);
                if (ascii.contains(".")) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Not a syntactically valid internationalized host name.
            }
        }
        return false;
    }

    static String clean(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u3002', '.')
                .replace('\uFF0E', '.')
                .replace('\uFF61', '.')
                .trim();
        return WHITESPACE.matcher(normalized).replaceAll(" ");
    }

    static String canonical(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static ValidationException invalid(String code, String message) {
        return new ValidationException(code, message);
    }

    private static final ValidationProof VALIDATION_PROOF = new ValidationProof();

    static boolean accepts(ValidationProof proof) {
        return proof == VALIDATION_PROOF;
    }

    static final class ValidationProof {
        private ValidationProof() {}
    }

    public static final class ValidationException extends IllegalArgumentException {
        private final String code;

        ValidationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
