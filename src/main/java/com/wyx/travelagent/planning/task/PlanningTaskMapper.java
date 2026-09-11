package com.wyx.travelagent.planning.task;

import org.apache.ibatis.annotations.*;

import java.time.Instant;

/** planning_task 的参数化 SQL 映射；所有用户查询都显式携带 user_id。 */
@Mapper
interface PlanningTaskMapper {
    @Select("SELECT id FROM `user` WHERE id = #{userId} FOR UPDATE")
    Integer lockUser(@Param("userId") int userId);

    @Select("SELECT COUNT(*) FROM planning_task WHERE user_id = #{userId} AND status IN ('PENDING','RUNNING')")
    int countActive(@Param("userId") int userId);

    @Insert("""
            INSERT INTO planning_task (user_id, request_json, resolved_requirement_json,
              applied_preferences_json, current_step, status, created_at, updated_at)
            VALUES (#{userId}, #{requestJson}, #{resolvedRequirementJson}, #{appliedPreferencesJson},
              #{currentStep}, #{status}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PlanningTaskRow row);

    @Select("SELECT * FROM planning_task WHERE id = #{taskId} AND user_id = #{userId}")
    PlanningTaskRow findOwned(@Param("taskId") long taskId, @Param("userId") int userId);

    @Select("SELECT * FROM planning_task WHERE id = #{taskId}")
    PlanningTaskRow findById(@Param("taskId") long taskId);

    @Select("SELECT * FROM planning_task WHERE plan_id = #{planId} AND user_id = #{userId} ORDER BY id DESC LIMIT 1")
    PlanningTaskRow findLatestByPlanAndUser(@Param("planId") long planId, @Param("userId") int userId);

    @Update("UPDATE planning_task SET status='RUNNING', current_step='REQUIREMENT', updated_at=#{at} WHERE id=#{id} AND status='PENDING'")
    int markRunning(@Param("id") long id, @Param("at") Instant at);

    @Update("UPDATE planning_task SET current_step=#{step}, updated_at=#{at} WHERE id=#{id} AND status='RUNNING'")
    int updateStep(@Param("id") long id, @Param("step") String step, @Param("at") Instant at);

    @Update("""
            UPDATE planning_task SET status=#{status}, current_step='COMPLETION', plan_id=#{planId},
              failure_code=#{code}, failure_reason=#{reason}, updated_at=#{at}
            WHERE id=#{id} AND status='RUNNING'
            """)
    int finish(@Param("id") long id, @Param("status") String status, @Param("planId") Long planId,
               @Param("code") String code, @Param("reason") String reason, @Param("at") Instant at);

    @Delete("DELETE FROM planning_task WHERE id=#{id} AND status='PENDING'")
    int deletePending(@Param("id") long id);

    @Update("UPDATE planning_task SET status='FAILED', current_step='COMPLETION', failure_code=#{code}, failure_reason=#{reason}, updated_at=#{at} WHERE status='RUNNING'")
    int failRunning(@Param("at") Instant at, @Param("code") String code, @Param("reason") String reason);
}
