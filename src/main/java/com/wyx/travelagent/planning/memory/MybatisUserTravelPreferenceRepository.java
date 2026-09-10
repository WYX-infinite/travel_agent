package com.wyx.travelagent.planning.memory;

import com.wyx.travelagent.mapper.*;
import com.wyx.travelagent.planning.model.TravelPace;
import com.wyx.travelagent.tools.model.TravelMode;
import org.springframework.stereotype.Repository;
import java.util.*;

/**
 * 业务问题：在领域偏好端口与 user_travel_preference 的 MyBatis 行模型之间安全转换。
 * 输入始终包含可信用户编号，输出只包含该用户的不可变偏好；除委托数据库 mapper 外不调用模型、缓存、文件或网络，认证由上层入口负责。
 * 任务十和任务十二不依赖存储细节；后续编排只消费快照，本适配器不负责认证、Controller、偏好合并或模型学习。
 */
@Repository
public class MybatisUserTravelPreferenceRepository implements UserTravelPreferenceRepository {
    private final UserTravelPreferenceMapper mapper;
    private final TravelInterestJsonCodec codec;

    /**
     * 创建 MyBatis 仓储适配器。
     * @param mapper 已由框架创建的非空参数化 SQL mapper
     * @param codec 非空确定性兴趣 JSON 编解码器
     * 依赖均不得为空；构造不访问数据库、模型、缓存、文件或网络，不处理认证。
     * @throws NullPointerException 当 mapper 或 codec 为 null
     */
    public MybatisUserTravelPreferenceRepository(UserTravelPreferenceMapper mapper, TravelInterestJsonCodec codec) {
        this.mapper = Objects.requireNonNull(mapper, "mapper"); this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * 按可信用户编号读取并严格转换领域偏好。
     * @param userId 非 null 的正用户编号
     * @return 成功无记录时为空的可选领域值
     * 损坏 JSON 或未知枚举会安全失败；除查询数据库外不调用模型、缓存、文件或网络，认证由上层入口负责。
     * @throws PreferenceMemoryException 当数据库行 JSON、枚举或主体字段非法，错误码为 INVALID_TRAVEL_PREFERENCE
     */
    @Override public Optional<UserTravelPreference> findByUserId(Integer userId) {
        UserTravelPreferenceRow row = mapper.findByUserId(userId);
        if (row == null) return Optional.empty();
        return Optional.of(new UserTravelPreference(row.userId(), codec.decode(row.interestsJson()), enumValue(row.pace(), TravelPace.class),
                enumValue(row.travelMode(), TravelMode.class), row.accommodationPreference(), row.updatedAt()));
    }

    /**
     * 按可信用户编号整体覆盖所有偏好列。
     * @param userId 非 null 的正用户编号
     * @param preference 完整替换领域值
     * 空兴趣写为 SQL null；除写入数据库外不调用模型、缓存、文件或网络，认证由上层入口负责。
     * @throws PreferenceMemoryException 当 userId 与 preference.userId 不一致，错误码为 INVALID_TRAVEL_PREFERENCE
     */
    @Override public void replace(Integer userId, UserTravelPreference preference) {
        if (!Objects.equals(userId, preference.userId())) {
            throw new PreferenceMemoryException("INVALID_TRAVEL_PREFERENCE", null);
        }
        String interestsJson = preference.interests().isEmpty() ? null : codec.encode(preference.interests());
        mapper.replace(new UserTravelPreferenceRow(userId, interestsJson, name(preference.pace()), name(preference.travelMode()),
                preference.accommodationPreference(), preference.updatedAt()));
    }

    /**
     * 按可信用户编号幂等删除偏好行。
     * @param userId 非 null 的正用户编号
     * mapper 返回零行不会转为错误；不访问其他用户、模型、缓存、文件或网络，认证由上层入口负责。
     */
    @Override public void deleteByUserId(Integer userId) { mapper.deleteByUserId(userId); }

    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
    private static <E extends Enum<E>> E enumValue(String value, Class<E> type) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException cause) { throw new PreferenceMemoryException("INVALID_TRAVEL_PREFERENCE", cause); }
    }
}
