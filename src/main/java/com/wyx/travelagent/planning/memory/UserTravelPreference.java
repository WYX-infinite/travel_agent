package com.wyx.travelagent.planning.memory;

import com.wyx.travelagent.planning.model.TravelInterest;
import com.wyx.travelagent.planning.model.TravelPace;
import com.wyx.travelagent.tools.model.TravelMode;
import java.time.Instant;
import java.util.*;

/**
 * 业务问题：表示用户主动保存的四类结构化长期偏好，禁止混入硬约束或自由文本要求。
 * 输入是兴趣、节奏、交通、住宿偏好和更新时间，输出是确定顺序的不可变值；本类型不访问外部资源或认证信息。
 * 任务十仅提供交通枚举，任务十二不消费长期记录；后续编排使用冻结快照，本类型不负责规划、模型学习或用户识别。
 * @param userId 偏好所属的正用户编号，不得为 null
 * @param interests 确定顺序的兴趣集合，不得为 null
 * @param pace 可缺失节奏
 * @param travelMode 可缺失交通方式
 * @param accommodationPreference 可缺失住宿偏好
 * @param updatedAt 合适的更新时间，不得为 null
 */
public record UserTravelPreference(Integer userId, Set<TravelInterest> interests, TravelPace pace, TravelMode travelMode,
                                   String accommodationPreference, Instant updatedAt) {
    /**
     * 创建不可变长期偏好。
     * 用户编号必须为正，兴趣按枚举序冻结，更新时间必填；非 null 住宿会 strip，空白或超过 255 字符稳定拒绝。
     * 构造不访问数据库、模型、缓存、文件或网络，不处理认证。
     * @throws PreferenceMemoryException 当用户编号、兴趣、住宿或更新时间非法，错误码为 INVALID_TRAVEL_PREFERENCE
     */
    public UserTravelPreference {
        if (userId == null || userId <= 0 || interests == null || updatedAt == null) invalid();
        try { interests = interests.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(interests)); }
        catch (RuntimeException cause) { throw new PreferenceMemoryException("INVALID_TRAVEL_PREFERENCE", cause); }
        if (accommodationPreference != null) {
            accommodationPreference = accommodationPreference.strip();
            if (accommodationPreference.isEmpty() || accommodationPreference.length() > 255) invalid();
        }
    }

    private static void invalid() { throw new PreferenceMemoryException("INVALID_TRAVEL_PREFERENCE", null); }
}
