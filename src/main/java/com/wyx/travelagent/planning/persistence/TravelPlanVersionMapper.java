package com.wyx.travelagent.planning.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

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

    /** @param planId 正行程编号 @param userId 正用户编号 @return 所有权成立时版本号升序行，否则空。 */
    @Select("""
            SELECT v.* FROM travel_plan_version v
            JOIN travel_plan p ON p.id=v.plan_id
            WHERE v.plan_id=#{planId} AND p.user_id=#{userId}
            ORDER BY v.version_number ASC
            """)
    List<TravelPlanVersionRow> findOwnedVersions(@Param("planId") long planId,
                                                 @Param("userId") int userId);
}
