package com.wyx.travelagent.planning.model;

import java.time.LocalTime;
import java.util.Objects;

/**
 * 业务问题：区分可靠营业时间与未知状态，防止模型把缺失资料补成“全天营业”。
 * 输入为可靠标记及同日营业区间，输出为不可变营业事实；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十可以向上游提供可信资料，任务十二只在可靠时校验，后续编排展示警告；本类型不负责跨午夜营业、在线查询或自动修正。
 *
 * @param reliable 是否已有可用于硬校验的可靠事实
 * @param opensAt 可靠时的开门时间；未知时必须为 {@code null}
 * @param closesAt 可靠时的关门时间；未知时必须为 {@code null}
 */
public record OpeningHoursFact(boolean reliable, LocalTime opensAt, LocalTime closesAt) {
    /**
     * 创建营业时间事实。
     * 可靠区间要求两端非空且关门晚于开门；未知状态不得携带时间。
     * 构造只校验内存数据，不访问模型、数据库、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当可靠区间缺少任一时间
     * @throws IllegalArgumentException 当区间无效或未知状态携带时间
     */
    public OpeningHoursFact {
        if (reliable) {
            Objects.requireNonNull(opensAt, "opensAt");
            Objects.requireNonNull(closesAt, "closesAt");
            if (!closesAt.isAfter(opensAt)) {
                throw new IllegalArgumentException("opening hours must be within one day");
            }
        } else if (opensAt != null || closesAt != null) {
            throw new IllegalArgumentException("unknown opening hours cannot contain times");
        }
    }

    /**
     * 创建可靠的同日营业区间。
     *
     * @param opensAt 非空开门时间
     * @param closesAt 非空且晚于开门的关门时间
     * @return 可用于硬校验的不可变事实
     * @throws NullPointerException 当任一时间为空
     * @throws IllegalArgumentException 当区间无效
     * 本方法不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public static OpeningHoursFact reliable(LocalTime opensAt, LocalTime closesAt) {
        return new OpeningHoursFact(true, opensAt, closesAt);
    }

    /**
     * 创建未知营业时间。
     *
     * @return 不携带猜测时间的不可变未知事实
     * 无输入且不抛业务异常；本方法不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public static OpeningHoursFact unknown() {
        return new OpeningHoursFact(false, null, null);
    }
}
