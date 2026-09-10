package com.wyx.travelagent.planning.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.wyx.travelagent.planning.model.TravelInterest;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * 业务问题：把已知旅行兴趣安全转换为确定顺序 JSON，阻止损坏或未知值静默进入领域模型。
 * 输入仅允许 TravelInterest 集合或 JSON 字符串数组，输出是紧凑 JSON 或不可变枚举集合；不访问数据库、模型、缓存、文件或网络。
 * 任务十与任务十二不读取持久 JSON；后续编排通过仓储适配器消费本编码器，本类型不处理认证，也不负责用户隔离、偏好合并或行程生成。
 */
@Component
public final class TravelInterestJsonCodec {
    private static final String INVALID = "INVALID_TRAVEL_PREFERENCE";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /**
     * 按枚举顺序编码兴趣集合。
     * @param interests 非空且不含 null 的兴趣集合
     * @return 确定顺序紧凑 JSON 数组
     * 非法输入或编码失败抛稳定 INVALID_TRAVEL_PREFERENCE，不访问任何外部资源，也不处理认证。
     * @throws PreferenceMemoryException 当集合为 null、含 null 或无法编码，错误码为 INVALID_TRAVEL_PREFERENCE
     */
    public String encode(Set<TravelInterest> interests) {
        try {
            Objects.requireNonNull(interests, "interests");
            List<String> values = interests.stream().sorted().map(Enum::name).toList();
            return objectMapper.writeValueAsString(values);
        } catch (RuntimeException | JsonProcessingException cause) {
            throw new PreferenceMemoryException(INVALID, cause);
        }
    }

    /**
     * 解码并严格校验兴趣 JSON。
     * @param json 可空数据库 JSON；null 表示字段未保存
     * @return 按枚举顺序冻结的已知兴趣集合
     * 损坏 JSON、非数组、null 元素或未知兴趣均抛稳定错误；不访问数据库、模型、缓存、文件或网络，也不处理认证。
     * @throws PreferenceMemoryException 当 JSON 损坏、结构错误或含未知兴趣，错误码为 INVALID_TRAVEL_PREFERENCE
     */
    public Set<TravelInterest> decode(String json) {
        if (json == null) return Set.of();
        try {
            String[] raw = objectMapper.readValue(json, String[].class);
            EnumSet<TravelInterest> values = EnumSet.noneOf(TravelInterest.class);
            for (String item : raw) values.add(TravelInterest.valueOf(Objects.requireNonNull(item, "interest")));
            return values.isEmpty() ? Set.of() : Collections.unmodifiableSet(values);
        } catch (RuntimeException | JsonProcessingException cause) {
            throw new PreferenceMemoryException(INVALID, cause);
        }
    }
}
