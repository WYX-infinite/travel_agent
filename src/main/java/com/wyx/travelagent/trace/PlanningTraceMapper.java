package com.wyx.travelagent.trace;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

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
}
