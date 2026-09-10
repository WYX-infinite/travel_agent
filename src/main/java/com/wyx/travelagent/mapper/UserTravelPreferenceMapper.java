package com.wyx.travelagent.mapper;

import org.apache.ibatis.annotations.*;

/**
 * 业务问题：以静态参数化 SQL 按 user_id 读取、整体覆盖和删除长期旅行偏好。
 * 输入是可信用户编号或完整行，输出是该用户的行或受影响数量；仅 MyBatis 执行数据库访问，不调用模型或网络服务。
 * 任务十和任务十二不直接访问偏好表；后续编排只消费领域快照，本映射器不负责认证、合并、Controller 或行程生成。
 */
@Mapper
public interface UserTravelPreferenceMapper {
    /**
     * 按用户主键读取单行偏好。
     * @param userId 非 null 的正用户编号
     * @return 无记录时为 null 的数据库行
     * SQL 固定并仅按 user_id 查询；除访问数据库外不调用模型、缓存、文件或网络，认证由上层入口负责，数据库异常由 MyBatis 运行时异常传播。
     */
    @Select("""
            SELECT user_id AS userId, interests_json AS interestsJson, pace,
                   travel_mode AS travelMode, accommodation_preference AS accommodationPreference,
                   updated_at AS updatedAt
            FROM user_travel_preference
            WHERE user_id = #{userId}
            """)
    public UserTravelPreferenceRow findByUserId(@Param("userId") Integer userId);

    /**
     * 按用户主键插入或整体覆盖所有可空字段。
     * @param row 非 null 完整数据库行，其中可空列的 null 用于清除旧值
     * @return 受影响行数
     * SQL 使用绑定参数整体覆盖；除访问数据库外不调用模型、缓存、文件或网络，认证由上层入口负责，数据库异常由 MyBatis 运行时异常传播。
     */
    @Insert("""
            INSERT INTO user_travel_preference
                (user_id, interests_json, pace, travel_mode, accommodation_preference, updated_at)
            VALUES
                (#{userId}, #{interestsJson}, #{pace}, #{travelMode}, #{accommodationPreference}, #{updatedAt})
            ON DUPLICATE KEY UPDATE
                interests_json = VALUES(interests_json),
                pace = VALUES(pace),
                travel_mode = VALUES(travel_mode),
                accommodation_preference = VALUES(accommodation_preference),
                updated_at = VALUES(updated_at)
            """)
    public int replace(UserTravelPreferenceRow row);

    /**
     * 按用户主键幂等删除偏好。
     * @param userId 非 null 的正用户编号
     * @return 受影响行数，无记录时为零
     * SQL 固定并仅按 user_id 删除；除访问数据库外不调用模型、缓存、文件或网络，认证由上层入口负责，数据库异常由 MyBatis 运行时异常传播。
     */
    @Delete("DELETE FROM user_travel_preference WHERE user_id = #{userId}")
    public int deleteByUserId(@Param("userId") Integer userId);
}
