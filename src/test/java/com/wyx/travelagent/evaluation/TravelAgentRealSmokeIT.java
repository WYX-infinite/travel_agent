package com.wyx.travelagent.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.knowledge.retrieval.KnowledgeRetrievalService;
import com.wyx.travelagent.planning.context.ConservativeTokenEstimator;
import com.wyx.travelagent.tools.model.PlaceCandidate;
import com.wyx.travelagent.tools.model.TravelMode;
import com.wyx.travelagent.tools.port.PlaceProvider;
import com.wyx.travelagent.tools.port.RouteProvider;
import com.wyx.travelagent.tools.port.WeatherProvider;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicit real-service connectivity smoke test. It is never selected by ordinary surefire test discovery.
 * Run only with: TRAVEL_REAL_SMOKE_ENABLED=true .\mvnw.cmd -Preal-smoke verify
 */
@Tag("real-smoke")
@EnabledIfEnvironmentVariable(named = "TRAVEL_REAL_SMOKE_ENABLED", matches = "(?i)true")
@ActiveProfiles({"local", "real-smoke"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TravelAgentRealSmokeIT {
    @Autowired private ChatModel chatModel;
    @Autowired private PlaceProvider placeProvider;
    @Autowired private RouteProvider routeProvider;
    @Autowired private WeatherProvider weatherProvider;
    @Autowired private KnowledgeRetrievalService knowledgeRetrievalService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RedisConnectionFactory redisConnectionFactory;

    @Test
    void callsConfiguredRealServicesAndWritesOnlySanitizedCounts() throws Exception {
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualToIgnoringCase("PONG");
        }

        var placeResult = placeProvider.search("杭州", "博物馆", 2);
        assertThat(placeResult.success()).isTrue();
        List<PlaceCandidate> places = Objects.requireNonNull(placeResult.data());
        assertThat(places).hasSizeGreaterThanOrEqualTo(2);
        var routeResult = routeProvider.route("杭州", places.get(0), places.get(1), TravelMode.TRANSIT);
        assertThat(routeResult.success()).isTrue();
        var weatherResult = weatherProvider.forecast(places.getFirst().location(),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(1));
        assertThat(weatherResult.success()).isTrue();
        int knowledgeReferenceCount = knowledgeRetrievalService.retrieve(
                "杭州两日文化行程", "杭州").allowedReferences().size();

        String smokePrompt = "Return one JSON object with field ok=true. Do not include explanations.";
        ChatResponse response = chatModel.chat(ChatRequest.builder()
                .messages(UserMessage.from(smokePrompt))
                .build());
        assertThat(response).isNotNull();
        assertThat(response.aiMessage()).isNotNull();
        assertThat(response.aiMessage().text()).isNotBlank();

        Integer actualInputTokens = response.tokenUsage() == null ? null : response.tokenUsage().inputTokenCount();
        Integer actualOutputTokens = response.tokenUsage() == null ? null : response.tokenUsage().outputTokenCount();
        int estimatedInputTokens = new ConservativeTokenEstimator().estimate(smokePrompt);
        Integer absoluteInputTokenDelta = actualInputTokens == null ? null
                : Math.abs(actualInputTokens - estimatedInputTokens);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("evaluationMode", "REAL_SMOKE");
        report.put("placeCount", places.size());
        report.put("routeVerified", routeResult.data() != null && routeResult.data().verified());
        report.put("weatherDayCount", weatherResult.data() == null ? 0 : weatherResult.data().dailyForecasts().size());
        report.put("knowledgeReferenceCount", knowledgeReferenceCount);
        report.put("estimatedInputTokens", estimatedInputTokens);
        report.put("actualInputTokens", actualInputTokens);
        report.put("actualOutputTokens", actualOutputTokens);
        report.put("absoluteInputTokenDelta", absoluteInputTokenDelta);
        Path output = Path.of("target", "evaluation", "travel-agent-real-smoke.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValueAsString(report) + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
