package com.wyx.travelagent.planning.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wyx.travelagent.common.api.RequestValidationException;
import com.wyx.travelagent.planning.memory.UserTravelPreference;
import com.wyx.travelagent.planning.model.TravelInterest;
import com.wyx.travelagent.planning.model.TravelPace;
import com.wyx.travelagent.tools.model.TravelMode;

import java.time.Instant;
import java.util.*;

/**
 * 业务问题：承载长期偏好的整体替换请求，明确限制为四个可补全字段。
 * 输入来自 JSON，输出为当前认证用户的领域偏好；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它属于任务十四偏好 API；不接收 userId、日期、预算或特殊要求，也不负责保存数据。
 * @param interests 可为空的兴趣名称列表；null 按空集合整体清除
 * @param pace 可为空的节奏枚举名称
 * @param travelMode 可为空的交通枚举名称
 * @param accommodationPreference 可为空的住宿偏好，非空时禁止空白且最多 255 字符
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserTravelPreferenceRequest(List<String> interests, String pace, String travelMode,
                                          String accommodationPreference) {
    /** 创建防御复制请求；只处理内存值，不访问外部资源或认证信息。 */
    public UserTravelPreferenceRequest {
        interests = interests == null ? null : Collections.unmodifiableList(new ArrayList<>(interests));
    }

    /**
     * 转换为当前用户的完整替换值。
     * @param userId 认证层提供的正用户编号
     * @param updatedAt 非空服务端更新时间
     * @return 非空、枚举已严格解析的领域偏好
     * @throws RequestValidationException 枚举、空白、长度或 null 兴趣元素非法时抛出，fields 含稳定字段原因
     * 本方法不访问数据库、模型、缓存、文件或网络；请求体不能覆盖 userId，认证由上层入口负责。
     */
    public UserTravelPreference toDomain(int userId, Instant updatedAt) {
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        Set<TravelInterest> parsedInterests = parseInterests(errors);
        TravelPace parsedPace = parseEnum(pace, TravelPace.class, "pace", errors);
        TravelMode parsedMode = parseEnum(travelMode, TravelMode.class, "travelMode", errors);
        String accommodation = accommodationPreference;
        if (accommodation != null) {
            accommodation = accommodation.strip();
            if (accommodation.isEmpty()) errors.put("accommodationPreference", "must not be blank");
            else if (accommodation.length() > 255) errors.put("accommodationPreference", "must be at most 255 characters");
        }
        if (!errors.isEmpty()) throw new RequestValidationException(errors);
        return new UserTravelPreference(userId, parsedInterests, parsedPace, parsedMode, accommodation, updatedAt);
    }

    private Set<TravelInterest> parseInterests(Map<String, String> errors) {
        if (interests == null || interests.isEmpty()) return Set.of();
        EnumSet<TravelInterest> result = EnumSet.noneOf(TravelInterest.class);
        for (String value : interests) {
            if (value == null) { errors.put("interests", "must contain only known enum values"); continue; }
            TravelInterest parsed = parseEnum(value, TravelInterest.class, "interests", errors);
            if (parsed != null) result.add(parsed);
        }
        return result;
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, String field,
                                                    Map<String, String> errors) {
        if (value == null) return null;
        if (value.isBlank()) { errors.put(field, "must be a known enum value"); return null; }
        try { return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { errors.put(field, "must be a known enum value"); return null; }
    }
}
