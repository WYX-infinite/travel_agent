package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.model.ItineraryDay;
import java.time.LocalDate;
import java.util.List;

/**
 * 业务问题：以稳定 DTO 表示一个自然日及其活动，隔离内部 ItineraryDay。
 * 输入是已解析日程，输出 JSON 字段；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它只服务任务十四行程响应；不负责排序、校验、用户所有权或前端渲染。
 * @param date 非空行程日期
 * @param items 非空且按时间稳定排序的活动列表
 */
public record PlanDayResponse(LocalDate date, List<PlanItemResponse> items) {
    /**
     * @param day 非空领域日程
     * @return 非空显式 DTO
     * 映射只读内存，不访问外部资源或处理认证；领域对象保证列表非空引用。
     */
    public static PlanDayResponse from(ItineraryDay day) {
        return new PlanDayResponse(day.date(), day.items().stream().map(PlanItemResponse::from).toList());
    }
}
