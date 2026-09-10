package com.wyx.travelagent.mapper;

import java.time.Instant;
import java.util.Objects;

/**
 * 业务问题：承载 user_travel_preference 表的一整行数据，保持 SQL 列与领域模型隔离。
 * 输入来自 MyBatis 查询或仓储整体覆盖，输出是不可变行值；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十和任务十二不接触数据库行；后续编排只消费转换后的领域快照，本类型不负责校验、合并或用户识别。
 * @param userId 行所属用户编号
 * @param interestsJson 可空兴趣 JSON
 * @param pace 可空节奏数据库值
 * @param travelMode 可空交通数据库值
 * @param accommodationPreference 可空住宿偏好
 * @param updatedAt 更新时间
 */
public record UserTravelPreferenceRow(Integer userId, String interestsJson, String pace, String travelMode,
                                      String accommodationPreference, Instant updatedAt) {
    /**
     * 创建单个用户的不可变数据库行。
     * 用户编号和更新时间非 null 且用户编号为正，可空偏好列原样保留以支持整体清除；输出不做隐式跨用户转换。
     * 构造不访问数据库、模型、缓存、文件或网络，不处理认证。
     * @throws NullPointerException 当用户编号或更新时间为 null
     * @throws IllegalArgumentException 当用户编号非正数
     */
    public UserTravelPreferenceRow {
        Objects.requireNonNull(userId, "userId"); Objects.requireNonNull(updatedAt, "updatedAt");
        if (userId <= 0) throw new IllegalArgumentException("userId");
    }
}
