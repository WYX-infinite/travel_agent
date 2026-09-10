package com.wyx.travelagent.planning.memory;

import java.util.Optional;

/**
 * 业务问题：隔离长期偏好领域服务与具体 MyBatis 存储实现。
 * 输入始终包含可信正用户编号，输出是该用户的可选偏好或写入结果；本端口自身不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十与任务十二不依赖存储细节；后续编排由服务提供快照，本端口不负责认证、Controller 或行程生成。
 */
public interface UserTravelPreferenceRepository {
    /**
     * 按可信用户编号读取整行偏好。
     * @param userId 正用户编号
     * @return 成功无记录时为空的可选值
     * 实现可能抛存储异常；不处理认证、模型、缓存或网络调用。
     */
    Optional<UserTravelPreference> findByUserId(Integer userId);

    /**
     * 整体覆盖指定用户偏好，包括用 null 清除旧字段。
     * @param userId 正用户编号
     * @param preference 完整替换值
     * 实现可能抛存储异常；不处理认证、模型、缓存或网络调用。
     */
    void replace(Integer userId, UserTravelPreference preference);

    /**
     * 幂等删除指定用户偏好。
     * @param userId 正用户编号
     * 无记录时仍成功；实现可能抛存储异常，不处理认证或其他用户数据。
     */
    void deleteByUserId(Integer userId);
}
