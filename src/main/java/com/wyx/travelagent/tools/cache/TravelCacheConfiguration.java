package com.wyx.travelagent.tools.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.tools.dispatch.ToolPlanValidator;
import com.wyx.travelagent.tools.dispatch.TravelToolDispatcher;
import com.wyx.travelagent.tools.port.PlaceProvider;
import com.wyx.travelagent.tools.port.RouteProvider;
import com.wyx.travelagent.tools.port.WeatherProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableCaching
public class TravelCacheConfiguration {
    public static final String POI_CACHE = "poi";
    public static final String GEOCODE_CACHE = "geocode";
    public static final String ROUTE_CACHE = "route";
    public static final String WEATHER_CACHE = "weather";

    @Bean
    CacheManager travelCacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfiguration(objectMapper))
                .withInitialCacheConfigurations(cacheConfigurations(objectMapper))
                .disableCreateOnMissingCache()
                .build();
    }

    @Bean
    ToolPlanValidator toolPlanValidator() {
        return new ToolPlanValidator();
    }

    @Bean
    TravelToolDispatcher travelToolDispatcher(
            ToolPlanValidator validator,
            PlaceProvider placeProvider,
            RouteProvider routeProvider,
            WeatherProvider weatherProvider,
            CacheManager cacheManager) {
        return new TravelToolDispatcher(
                validator, placeProvider, routeProvider, weatherProvider, cacheManager);
    }

    public Map<String, RedisCacheConfiguration> cacheConfigurations(ObjectMapper objectMapper) {
        RedisCacheConfiguration base = baseConfiguration(objectMapper);
        Map<String, RedisCacheConfiguration> configurations = new LinkedHashMap<>();
        configurations.put(POI_CACHE, base.entryTtl(Duration.ofHours(24)));
        configurations.put(GEOCODE_CACHE, base.entryTtl(Duration.ofDays(7)));
        configurations.put(ROUTE_CACHE, base.entryTtl(Duration.ofHours(1)));
        configurations.put(WEATHER_CACHE, base.entryTtl(Duration.ofMinutes(30)));
        return Map.copyOf(configurations);
    }

    private RedisCacheConfiguration baseConfiguration(ObjectMapper objectMapper) {
        GenericJackson2JsonRedisSerializer serializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(objectMapper.copy())
                .defaultTyping(true)
                .build();
        return RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }
}
