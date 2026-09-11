package com.wyx.travelagent.planning.generation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyx.travelagent.planning.context.PromptContext;
import com.wyx.travelagent.planning.context.ItineraryPromptRenderer;
import com.wyx.travelagent.planning.model.CostEstimate;
import com.wyx.travelagent.planning.model.CostEvidenceType;
import com.wyx.travelagent.planning.model.CostStatus;
import com.wyx.travelagent.planning.model.ItineraryDay;
import com.wyx.travelagent.planning.model.ItineraryItem;
import com.wyx.travelagent.planning.model.ItineraryPlan;
import com.wyx.travelagent.tools.model.TravelMode;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * 业务问题：用 LangChain4j ChatModel 单次生成行程，并将不可信文本严格转换为领域对象。
 * 输入仅为预算内 PromptContext，输出为白名单内 ItineraryPlan；本类型调用模型并在构造时读取类路径模板，不访问数据库或缓存，不处理认证。
 * 任务十/十一事实先由任务十二组装，本适配器完成同任务的生成边界，后续调用负责规则校验与修正；本类型不负责检索、重试、日志或自动修正。
 */
public final class LangChain4jItineraryGenerator implements ItineraryGenerator {
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ItineraryPromptRenderer promptRenderer;

    /**
     * 创建严格 JSON 生成适配器并加载固定提示模板。
     *
     * @param chatModel 非空 LangChain4j 同步聊天模型
     * @throws NullPointerException 当模型为空
     * @throws IllegalStateException 当类路径模板缺失、不可读或没有唯一占位符
     * 构造只读取类路径模板，不调用模型、数据库、缓存或网络，不处理认证。
     */
    public LangChain4jItineraryGenerator(ChatModel chatModel) {
        this(chatModel, new ItineraryPromptRenderer());
    }

    /**
     * 创建复用指定 Prompt 渲染器的严格 JSON 生成适配器。
     *
     * @param chatModel 非空 LangChain4j 同步聊天模型
     * @param promptRenderer 非空且与上下文预算器共享的最终 Prompt 渲染器
     * @throws NullPointerException 当任一依赖为空
     * 构造只准备内存解析器，不调用模型，也不访问数据库、缓存、文件或网络，不处理认证。
     */
    public LangChain4jItineraryGenerator(
            ChatModel chatModel,
            ItineraryPromptRenderer promptRenderer) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.promptRenderer = Objects.requireNonNull(promptRenderer, "promptRenderer");
        this.objectMapper = strictObjectMapper();
    }

    /**
     * 以一次模型调用生成并严格校验行程草稿。
     *
     * @param context 非空、预算内且包含最终 POI/知识白名单的 PromptContext
     * @return 非空类型化行程；POI 与 DOCUMENT 切片均属于上下文白名单
     * @throws NullPointerException 当上下文为空
     * @throws ItineraryGenerationException 当模型失败、空响应、JSON 非法、字段漂移、领域构造失败或引用越界
     * 本方法只调用一次模型，不访问数据库、缓存、文件或其他网络来源，不处理认证；失败信息不保留模型原文和底层异常。
     */
    @Override
    public ItineraryPlan generate(PromptContext context) {
        Objects.requireNonNull(context, "context");
        if (!context.budgetReport().withinBudget()) {
            throw new IllegalArgumentException("CONTEXT_BUDGET_EXCEEDED");
        }
        String response;
        try {
            response = chatModel.chat(promptRenderer.render(context.render()));
        } catch (RuntimeException exception) {
            throw new ItineraryGenerationException(ItineraryGenerationException.GENERATION_FAILED);
        }
        if (response == null || response.isBlank()) {
            throw new ItineraryGenerationException(ItineraryGenerationException.GENERATION_FAILED);
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root == null || !root.isObject()) throw new IllegalArgumentException("object root required");
            ResponseDto dto = objectMapper.treeToValue(root, ResponseDto.class);
            ItineraryPlan plan = toPlan(dto);
            verifyWhiteLists(plan, context);
            return plan;
        } catch (Exception exception) {
            throw new ItineraryGenerationException(ItineraryGenerationException.INVALID_RESPONSE);
        }
    }

    /**
     * 使用 LangChain4j 结构化响应 API 生成行程，并读取供应商返回的实际 Token 元数据。
     * 越界 POI 仍被拒绝；越界 DOCUMENT 引用会安全降级为 MISSING 并计数，供任务十三写入 Trace。
     *
     * @param context 非空、预算内且带最终 POI/知识白名单的上下文
     * @return 不含模型原文的结构化结果
     * @throws ItineraryGenerationException 当模型失败、响应不可解析或 POI 越界
     * 本方法只调用一次模型，不访问数据库、缓存或其他网络来源，也不处理认证。
     */
    @Override
    public ItineraryGenerationResult generateResult(PromptContext context) {
        Objects.requireNonNull(context, "context");
        if (!context.budgetReport().withinBudget()) {
            throw new IllegalArgumentException("CONTEXT_BUDGET_EXCEEDED");
        }
        ChatResponse response;
        try {
            String prompt = promptRenderer.render(context.render());
            response = chatModel.chat(ChatRequest.builder()
                    .messages(UserMessage.from(prompt))
                    .build());
        } catch (RuntimeException exception) {
            throw new ItineraryGenerationException(ItineraryGenerationException.GENERATION_FAILED);
        }
        if (response == null || response.aiMessage() == null
                || response.aiMessage().text() == null || response.aiMessage().text().isBlank()) {
            throw new ItineraryGenerationException(ItineraryGenerationException.GENERATION_FAILED);
        }
        try {
            JsonNode root = objectMapper.readTree(response.aiMessage().text());
            if (root == null || !root.isObject()) throw new IllegalArgumentException("object root required");
            ResponseDto dto = objectMapper.treeToValue(root, ResponseDto.class);
            ItineraryPlan parsed = toPlan(dto);
            verifyPoiWhiteList(parsed, context);
            SanitizedPlan sanitized = sanitizeInvalidReferences(parsed, context);
            return new ItineraryGenerationResult(sanitized.plan(), tokenUsage(response.tokenUsage()),
                    sanitized.invalidReferenceCount());
        } catch (Exception exception) {
            throw new ItineraryGenerationException(ItineraryGenerationException.INVALID_RESPONSE);
        }
    }

    private ItineraryPlan toPlan(ResponseDto dto) {
        Objects.requireNonNull(dto, "response");
        Objects.requireNonNull(dto.days(), "days");
        List<ItineraryDay> days = dto.days().stream().map(day -> {
            Objects.requireNonNull(day, "day");
            Objects.requireNonNull(day.items(), "items");
            return new ItineraryDay(day.date(), day.items().stream().map(this::toItem).toList());
        }).toList();
        return new ItineraryPlan(dto.destinationCity(), dto.startDate(), dto.endDate(), days,
                dto.estimatedTotalCost());
    }

    private ItineraryItem toItem(ItemDto item) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(item.cost(), "cost");
        CostEstimate cost = item.cost().status() == CostStatus.MISSING
                ? new CostEstimate(CostStatus.MISSING, item.cost().minimum(), item.cost().maximum(),
                        item.cost().evidenceType(), item.cost().knowledgeChunkId())
                : new CostEstimate(item.cost().status(), item.cost().minimum(), item.cost().maximum(),
                        item.cost().evidenceType(), item.cost().knowledgeChunkId());
        return new ItineraryItem(item.startTime(), item.endTime(), item.poiId(), item.name(),
                item.description(), cost, item.travelModeFromPrevious());
    }

    private void verifyWhiteLists(ItineraryPlan plan, PromptContext context) {
        verifyPoiWhiteList(plan, context);
        for (ItineraryDay day : plan.days()) {
            for (ItineraryItem item : day.items()) {
                if (item.cost().evidenceType() == CostEvidenceType.DOCUMENT
                        && !context.allowedKnowledgeChunkIds().contains(item.cost().knowledgeChunkId())) {
                    throw new IllegalArgumentException("knowledge outside context");
                }
            }
        }
    }

    private void verifyPoiWhiteList(ItineraryPlan plan, PromptContext context) {
        for (ItineraryDay day : plan.days()) {
            for (ItineraryItem item : day.items()) {
                if (!context.allowedPoiIds().contains(item.poiId())) {
                    throw new IllegalArgumentException("poi outside context");
                }
            }
        }
    }

    private SanitizedPlan sanitizeInvalidReferences(ItineraryPlan plan, PromptContext context) {
        int invalidCount = 0;
        List<ItineraryDay> days = new java.util.ArrayList<>();
        for (ItineraryDay day : plan.days()) {
            List<ItineraryItem> items = new java.util.ArrayList<>();
            for (ItineraryItem item : day.items()) {
                if (item.cost().evidenceType() == CostEvidenceType.DOCUMENT
                        && !context.allowedKnowledgeChunkIds().contains(item.cost().knowledgeChunkId())) {
                    invalidCount++;
                    items.add(new ItineraryItem(item.startTime(), item.endTime(), item.poiId(), item.name(),
                            item.description(), new CostEstimate(CostStatus.MISSING, null, null, null, null),
                            item.travelModeFromPrevious()));
                } else {
                    items.add(item);
                }
            }
            days.add(new ItineraryDay(day.date(), items));
        }
        return new SanitizedPlan(new ItineraryPlan(plan.destinationCity(), plan.startDate(), plan.endDate(),
                days, plan.estimatedTotalCost()), invalidCount);
    }

    private static ModelTokenUsage tokenUsage(TokenUsage usage) {
        if (usage == null || usage.inputTokenCount() == null || usage.outputTokenCount() == null) {
            return ModelTokenUsage.unknown();
        }
        return new ModelTokenUsage(usage.inputTokenCount(), usage.outputTokenCount());
    }

    private static ObjectMapper strictObjectMapper() {
        JsonFactory factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        return new ObjectMapper(factory)
                .findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private record ResponseDto(
            String destinationCity,
            LocalDate startDate,
            LocalDate endDate,
            List<DayDto> days,
            BigDecimal estimatedTotalCost) {
    }

    private record DayDto(LocalDate date, List<ItemDto> items) {
    }

    private record ItemDto(
            LocalTime startTime,
            LocalTime endTime,
            String poiId,
            String name,
            String description,
            CostDto cost,
            TravelMode travelModeFromPrevious) {
    }

    private record CostDto(
            CostStatus status,
            BigDecimal minimum,
            BigDecimal maximum,
            CostEvidenceType evidenceType,
            Long knowledgeChunkId) {
    }

    private record SanitizedPlan(ItineraryPlan plan, int invalidReferenceCount) {
    }
}
