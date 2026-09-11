package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.model.ItineraryItem;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * 业务问题：按任务十四冻结协议公开单个活动，同时隔离内部行程实体和未持久化信息。
 * 输入是已校验活动，输出稳定 JSON DTO；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 当前领域模型未保存地址、完整下一段路线或活动级引用元数据，因此对应字段显式为 null 或空列表；本类型不负责推断或伪造这些事实。
 *
 * @param startTime 非空开始时间
 * @param endTime 非空结束时间
 * @param poiId 非空虚构或工具 POI 标识
 * @param poiName 非空活动地点名称
 * @param address 可为空地点地址；当前领域模型未持久化时为 null
 * @param activity 可为空活动说明
 * @param cost 非空显式费用与证据边界
 * @param nextSegment 可为空下一段路线；完整路线未持久化时为 null
 * @param evidence 非空活动级证据列表；引用元数据未持久化时为空
 */
public record PlanItemResponse(
        LocalTime startTime,
        LocalTime endTime,
        String poiId,
        String poiName,
        String address,
        String activity,
        PlanCostResponse cost,
        PlanTravelSegmentResponse nextSegment,
        List<PlanEvidenceResponse> evidence) {

    /** 创建防御复制的公开活动 DTO；只校验内存值，不访问外部资源或处理认证。 */
    public PlanItemResponse {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        Objects.requireNonNull(poiId, "poiId");
        Objects.requireNonNull(poiName, "poiName");
        Objects.requireNonNull(cost, "cost");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
    }

    /**
     * @param item 非空领域活动
     * @return 非空显式 DTO；领域未保存的地址、完整路线和引用元数据分别保持 null、null 和空列表
     * 映射只读内存，不访问外部资源或处理认证，也不把到达当前活动的交通方式误写为离开当前活动的 nextSegment。
     */
    public static PlanItemResponse from(ItineraryItem item) {
        Objects.requireNonNull(item, "item");
        return new PlanItemResponse(item.startTime(), item.endTime(), item.poiId(), item.name(),
                null, item.description(), PlanCostResponse.from(item.cost()), null, List.of());
    }
}
