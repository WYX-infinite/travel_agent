package com.wyx.travelagent.planning.memory;

import com.wyx.travelagent.planning.model.TravelInterest;
import com.wyx.travelagent.planning.model.TravelPace;
import com.wyx.travelagent.tools.model.TravelMode;
import java.util.*;

/**
 * 业务问题：冻结本次规划四个偏好字段的最终值、逐字段来源和记忆读取结果。
 * 输入是合并完成时的值与来源，输出是不受后续长期偏好变化影响的不可变快照；本类型不访问外部资源或认证信息。
 * 任务十只提供交通枚举，任务十二只校验生成结果；后续编排和追踪消费快照，本类型不负责读取、保存、校验或行程生成。
 * @param interests 最终兴趣集合
 * @param pace 最终节奏
 * @param travelMode 最终交通方式
 * @param accommodationPreference 最终住宿偏好
 * @param interestsSource 兴趣来源
 * @param paceSource 节奏来源
 * @param travelModeSource 交通来源
 * @param accommodationPreferenceSource 住宿来源
 * @param requested 是否请求读取长期偏好
 * @param readStatus 读取状态
 */
public record AppliedPreferenceSnapshot(Set<TravelInterest> interests, TravelPace pace, TravelMode travelMode,
        String accommodationPreference, PreferenceSource interestsSource, PreferenceSource paceSource,
        PreferenceSource travelModeSource, PreferenceSource accommodationPreferenceSource,
        boolean requested, PreferenceReadStatus readStatus) {
    /**
     * 创建冻结快照。
     * 兴趣按枚举顺序防御复制，所有来源和读取状态必填；可选最终值允许 null。
     * 构造不访问数据库、模型、缓存、文件或网络，不处理认证。
     * @throws NullPointerException 当兴趣、任一来源、读取状态或兴趣元素为 null
     */
    public AppliedPreferenceSnapshot {
        Objects.requireNonNull(interests, "interests");
        interests = interests.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(interests));
        Objects.requireNonNull(interestsSource, "interestsSource"); Objects.requireNonNull(paceSource, "paceSource");
        Objects.requireNonNull(travelModeSource, "travelModeSource");
        Objects.requireNonNull(accommodationPreferenceSource, "accommodationPreferenceSource");
        Objects.requireNonNull(readStatus, "readStatus");
    }
}
