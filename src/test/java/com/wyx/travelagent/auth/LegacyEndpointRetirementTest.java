package com.wyx.travelagent.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties =
        "travel.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class LegacyEndpointRetirementTest {

    private static final Set<String> RETIRED_PATHS = Set.of(
            "/login", "/captcha", "/recommended", "/suggest", "/collect", "/list", "/route");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void legacyRootEndpointsAreNotRegistered() {
        Set<String> registeredPaths = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(registeredPaths).doesNotContainAnyElementsOf(RETIRED_PATHS);
    }
}
