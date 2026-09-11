package com.wyx.travelagent.trace;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** planning_trace 的最小写映射；只接收已脱敏行。 */
@Mapper
interface PlanningTraceMapper {

    /** @param row 非空脱敏 Trace 行；@return 受影响行数。 */
    @Insert("""
            INSERT INTO planning_trace
                (task_id, plan_version_id, step, status, duration_ms,
                 input_summary, output_summary, error_code, error_message, created_at)
            VALUES
                (#{taskId}, #{planVersionId}, #{step}, #{status}, #{durationMs},
                 #{inputSummary}, #{outputSummary}, #{errorCode}, #{errorMessage}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PlanningTraceRow row);

    /** @param taskId 正任务编号 @param afterId 非负排他游标 @param limit 有界数量 @return 主键升序行。 */
    @Select("SELECT * FROM planning_trace WHERE task_id=#{taskId} AND id>#{afterId} ORDER BY id ASC LIMIT #{limit}")
    List<PlanningTraceRow> findAfter(@Param("taskId") long taskId,
                                     @Param("afterId") long afterId,
                                     @Param("limit") int limit);
}
