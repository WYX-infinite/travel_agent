package com.wyx.travelagent.planning.generation;

import com.wyx.travelagent.planning.context.ContextBudgetReport;
import com.wyx.travelagent.planning.context.PromptContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LangChain4jItineraryGenerationResultTest {

    @Test
    void readsActualUsageFromStructuredResponseMetadataAndSanitizesInvalidDocumentReference() {
        ChatModel model = mock(ChatModel.class);
        String response = """
                {"destinationCity":"杭州","startDate":"2026-10-01","endDate":"2026-10-01",
                 "days":[{"date":"2026-10-01","items":[{"startTime":"09:00","endTime":"10:00",
                 "poiId":"poi-1","name":"西湖","description":null,
                 "cost":{"status":"KNOWN","minimum":20,"maximum":20,"evidenceType":"DOCUMENT","knowledgeChunkId":999},
                 "travelModeFromPrevious":null}]}],"estimatedTotalCost":20}
                """;
        when(model.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from(response))
                .tokenUsage(new TokenUsage(41, 17))
                .build());

        ItineraryGenerationResult result = new LangChain4jItineraryGenerator(model).generateResult(context());

        assertThat(result.tokenUsage()).isEqualTo(new ModelTokenUsage(41, 17));
        assertThat(result.invalidReferenceCount()).isEqualTo(1);
        assertThat(result.plan().days().getFirst().items().getFirst().cost().status().name())
                .isEqualTo("MISSING");
        assertThat(result.plan().days().getFirst().items().getFirst().cost().knowledgeChunkId()).isNull();
        verify(model).chat(any(ChatRequest.class));
    }

    @Test
    void marksUsageUnknownWhenProviderOmitsTokenMetadata() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from(validResponse()))
                .build());

        ItineraryGenerationResult result = new LangChain4jItineraryGenerator(model).generateResult(context());

        assertThat(result.tokenUsage().known()).isFalse();
        assertThat(result.tokenUsage().inputTokens()).isNull();
        assertThat(result.tokenUsage().outputTokens()).isNull();
    }

    private static PromptContext context() {
        ContextBudgetReport report = new ContextBudgetReport(100, 10, 10, 10, 10, 10, 10, 10,
                1, 1, 0, 0, List.of(), List.of(), true);
        return new PromptContext("system", "hard", List.of(), List.of(), List.of(), List.of(),
                List.of(), report, Set.of("poi-1"), Set.of());
    }

    private static String validResponse() {
        return """
                {"destinationCity":"杭州","startDate":"2026-10-01","endDate":"2026-10-01",
                 "days":[{"date":"2026-10-01","items":[{"startTime":"09:00","endTime":"10:00",
                 "poiId":"poi-1","name":"西湖","description":null,
                 "cost":{"status":"MISSING","minimum":null,"maximum":null,"evidenceType":null,"knowledgeChunkId":null},
                 "travelModeFromPrevious":null}]}],"estimatedTotalCost":0}
                """;
    }
}
