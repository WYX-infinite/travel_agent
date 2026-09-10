package com.wyx.travelagent.planning.generation;

import com.wyx.travelagent.planning.context.ContextBudgetReport;
import com.wyx.travelagent.planning.context.PromptContext;
import com.wyx.travelagent.planning.model.CostEvidenceType;
import com.wyx.travelagent.planning.model.CostStatus;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LangChain4jItineraryGeneratorTest {
    private static final String VALID_RESPONSE = """
            {
              "destinationCity":"杭州",
              "startDate":"2026-10-01",
              "endDate":"2026-10-01",
              "days":[{"date":"2026-10-01","items":[{
                "startTime":"09:00","endTime":"11:00","poiId":"poi-1","name":"西湖",
                "description":"步行游览","cost":{"status":"KNOWN","minimum":0,"maximum":0,
                "evidenceType":"FACT","knowledgeChunkId":null},"travelModeFromPrevious":null
              }]}],
              "estimatedTotalCost":0
            }
            """;

    @Test
    void callsModelExactlyOnceWithTemplateAndReturnsTypedPlan() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(anyString())).thenReturn(VALID_RESPONSE);
        ItineraryGenerator generator = new LangChain4jItineraryGenerator(chatModel);

        ItineraryPlan result = generator.generate(context());

        assertThat(result.destinationCity()).isEqualTo("杭州");
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.days()).hasSize(1);
        assertThat(result.days().getFirst().items().getFirst().startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(result.days().getFirst().items().getFirst().cost().status()).isEqualTo(CostStatus.KNOWN);
        assertThat(result.days().getFirst().items().getFirst().cost().evidenceType())
                .isEqualTo(CostEvidenceType.FACT);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatModel, times(1)).chat(prompt.capture());
        assertThat(prompt.getValue())
                .contains("只能输出单个 JSON 对象", "禁止 Markdown", "poiId", "AI_ESTIMATE")
                .contains(context().render())
                .doesNotContain("{{PROMPT_CONTEXT}}");
    }

    @ParameterizedTest
    @MethodSource("invalidResponses")
    void rejectsNonSchemaOrUntrustedResponsesWithoutLeakingRawContent(String response) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(anyString())).thenReturn(response);
        ItineraryGenerator generator = new LangChain4jItineraryGenerator(chatModel);

        assertThatThrownBy(() -> generator.generate(context()))
                .isInstanceOf(ItineraryGenerationException.class)
                .hasMessage("INVALID_ITINERARY_RESPONSE")
                .hasNoCause()
                .satisfies(exception -> assertThat(((ItineraryGenerationException) exception).code())
                        .isEqualTo("INVALID_ITINERARY_RESPONSE"))
                .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain(response));
        verify(chatModel, times(1)).chat(anyString());
    }

    @Test
    void mapsModelFailureToStableSanitizedExceptionWithoutRetry() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(anyString())).thenThrow(new IllegalStateException("secret provider failure"));
        ItineraryGenerator generator = new LangChain4jItineraryGenerator(chatModel);

        assertThatThrownBy(() -> generator.generate(context()))
                .isInstanceOf(ItineraryGenerationException.class)
                .hasMessage("ITINERARY_GENERATION_FAILED")
                .hasNoCause()
                .satisfies(exception -> assertThat(((ItineraryGenerationException) exception).code())
                        .isEqualTo("ITINERARY_GENERATION_FAILED"));
        verify(chatModel, times(1)).chat(anyString());
    }

    @Test
    void ignoresHarmlessUnknownFieldsWhileKeepingKnownFieldsStrict() {
        ChatModel chatModel = mock(ChatModel.class);
        String response = VALID_RESPONSE
                .replace("\"description\":\"步行游览\"",
                        "\"description\":\"步行游览\",\"displayHint\":\"morning\"")
                .replace("\"estimatedTotalCost\":0",
                        "\"estimatedTotalCost\":0,\"responseVersion\":\"future\"");
        when(chatModel.chat(anyString())).thenReturn(response);

        ItineraryPlan result = new LangChain4jItineraryGenerator(chatModel).generate(context());

        assertThat(result.days().getFirst().items().getFirst().poiId()).isEqualTo("poi-1");
        verify(chatModel, times(1)).chat(anyString());
    }

    @Test
    void rejectsOverBudgetContextBeforeAnyModelCall() {
        ChatModel chatModel = mock(ChatModel.class);
        ItineraryGenerator generator = new LangChain4jItineraryGenerator(chatModel);

        assertThatThrownBy(() -> generator.generate(context(false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CONTEXT_BUDGET_EXCEEDED");
        verify(chatModel, never()).chat(anyString());
    }

    private static Stream<String> invalidResponses() {
        return Stream.of(
                "```json\n" + VALID_RESPONSE + "\n```",
                VALID_RESPONSE + " trailing secret",
                VALID_RESPONSE.replace("\"poiId\":\"poi-1\"", "\"poiId\":\"poi-1\",\"poiId\":\"poi-2\""),
                VALID_RESPONSE.replace("\"poi-1\"", "\"poi-outside-context\""),
                VALID_RESPONSE.replace("\"FACT\"", "\"DOCUMENT\"").replace("\"knowledgeChunkId\":null", "\"knowledgeChunkId\":999"),
                VALID_RESPONSE.replace("\"KNOWN\"", "\"BROKEN\""),
                VALID_RESPONSE.replace("\"poiId\":\"poi-1\",", ""),
                "[]",
                "not-json");
    }

    private static PromptContext context() {
        return context(true);
    }

    private static PromptContext context(boolean withinBudget) {
        ContextBudgetReport report = new ContextBudgetReport(
                100, 20, 20, 10, 20, 10, 10, 10,
                1, 1, 1, 1, List.of(), List.of(), withinBudget);
        return new PromptContext(
                "system schema", "hard constraints", List.of(),
                List.of("{\"type\":\"POI\",\"poiId\":\"poi-1\"}"), List.of(),
                List.of("{\"chunkId\":101}"), List.of(), report,
                Set.of("poi-1"), Set.of(101L));
    }
}
