package com.wyx.travelagent.planning.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/** travel_plan_version 的最小 MyBatis 写映射；只保存内部结构化快照。 */
@Mapper
interface TravelPlanVersionMapper {

    /** @param row 非空版本行；@return 受影响行数。 */
    @Insert("""
            INSERT INTO travel_plan_version
                (plan_id, version_number, itinerary_json, validation_json, metrics_json)
            VALUES
                (#{planId}, #{versionNumber}, CAST(#{itineraryJson} AS JSON),
                 CAST(#{validationJson} AS JSON), CAST(#{metricsJson} AS JSON))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TravelPlanVersionRow row);
}
