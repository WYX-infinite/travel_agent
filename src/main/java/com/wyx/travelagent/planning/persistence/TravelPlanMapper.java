package com.wyx.travelagent.planning.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** travel_plan 的最小 MyBatis 写映射；不读取其他用户数据。 */
@Mapper
interface TravelPlanMapper {

    /** @param row 非空待插入行；@return 受影响行数。 */
    @Insert("""
            INSERT INTO travel_plan
                (user_id, destination_city, start_date, end_date, current_version, status)
            VALUES
                (#{userId}, #{destinationCity}, #{startDate}, #{endDate}, #{currentVersion}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TravelPlanRow row);

    /**
     * 只允许版本号前进，防止并发旧版本覆盖新版本。
     *
     * @param planId 正行程编号
     * @param version 正版本号
     * @param status 非空状态
     * @return 恰好一行表示更新成功
     */
    @Update("""
            UPDATE travel_plan
            SET current_version = #{version}, status = #{status}
            WHERE id = #{planId} AND current_version < #{version}
            """)
    int advanceVersion(@Param("planId") long planId,
                       @Param("version") int version,
                       @Param("status") String status);

    /** @return 恰好一行表示状态更新成功。 */
    @Update("UPDATE travel_plan SET status = #{status} WHERE id = #{planId}")
    int updateStatus(@Param("planId") long planId, @Param("status") String status);
}
