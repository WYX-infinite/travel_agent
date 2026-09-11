package com.wyx.travelagent.planning.api;

/**
 * 业务问题：按任务十四冻结协议公开活动之后的路线事实，并显式标记是否经过工具验证。
 * 输入来自已持久化路线事实，输出稳定 JSON DTO；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 当前行程领域模型尚未保存完整路线时，上层返回 null 而不是构造本类型；它不负责路线查询、坐标转换或时间推断。
 *
 * @param mode 可为空稳定交通方式
 * @param distanceMeters 可为空非负距离米数
 * @param durationMinutes 可为空非负时长分钟数
 * @param verified 是否由受控路线工具验证
 */
public record PlanTravelSegmentResponse(
        String mode, Integer distanceMeters, Integer durationMinutes, boolean verified) {
    /** 创建路线响应；只校验内存数值，不访问外部资源或处理认证。 */
    public PlanTravelSegmentResponse {
        if (distanceMeters != null && distanceMeters < 0
                || durationMinutes != null && durationMinutes < 0) {
            throw new IllegalArgumentException("INVALID_TRAVEL_SEGMENT");
        }
    }
}
