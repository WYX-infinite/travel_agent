package com.wyx.travelagent.tools.config;

import com.wyx.travelagent.tools.amap.AmapPlaceProvider;
import com.wyx.travelagent.tools.amap.AmapProperties;
import com.wyx.travelagent.tools.amap.AmapRouteProvider;
import com.wyx.travelagent.tools.geo.Gcj02ToWgs84Converter;
import com.wyx.travelagent.tools.weather.OpenMeteoWeatherProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AmapProperties.class)
public class ProviderConfiguration {
    private static final String OPEN_METEO_BASE_URL = "https://api.open-meteo.com";
    private static final Duration OPEN_METEO_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration OPEN_METEO_READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean("amapRestClient")
    RestClient amapRestClient(AmapProperties properties) {
        return restClient(properties.baseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    @Bean("openMeteoRestClient")
    RestClient openMeteoRestClient() {
        return restClient(OPEN_METEO_BASE_URL, OPEN_METEO_CONNECT_TIMEOUT, OPEN_METEO_READ_TIMEOUT);
    }

    @Bean
    Clock providerClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    Gcj02ToWgs84Converter gcj02ToWgs84Converter() {
        return new Gcj02ToWgs84Converter();
    }

    @Bean
    AmapPlaceProvider amapPlaceProvider(
            @Qualifier("amapRestClient") RestClient restClient,
            AmapProperties properties,
            Clock providerClock
    ) {
        return new AmapPlaceProvider(restClient, properties, providerClock);
    }

    @Bean
    AmapRouteProvider amapRouteProvider(
            @Qualifier("amapRestClient") RestClient restClient,
            AmapProperties properties,
            Clock providerClock
    ) {
        return new AmapRouteProvider(restClient, properties, providerClock);
    }

    @Bean
    OpenMeteoWeatherProvider openMeteoWeatherProvider(
            @Qualifier("openMeteoRestClient") RestClient restClient,
            Gcj02ToWgs84Converter converter,
            Clock providerClock
    ) {
        return new OpenMeteoWeatherProvider(restClient, converter, providerClock);
    }

    private static RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
