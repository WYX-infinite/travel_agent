package com.wyx.travelagent.mapper;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class UserTravelPreferenceMapperIntegrationTest {
    @Test void executesInsertSelectWholeReplaceUserIsolationAndIdempotentDelete() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:preference_mapper;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE user_travel_preference (
                      user_id INT PRIMARY KEY,
                      interests_json JSON NULL,
                      pace VARCHAR(16) NULL,
                      travel_mode VARCHAR(16) NULL,
                      accommodation_preference VARCHAR(255) NULL,
                      updated_at TIMESTAMP(6) NOT NULL)
                    """);
        }
        Configuration configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(UserTravelPreferenceMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        Instant firstTime = Instant.parse("2026-09-09T01:02:03Z");
        Instant secondTime = Instant.parse("2026-09-09T04:05:06Z");
        try (SqlSession session = factory.openSession(true)) {
            UserTravelPreferenceMapper mapper = session.getMapper(UserTravelPreferenceMapper.class);
            assertThat(mapper.replace(new UserTravelPreferenceRow(11, "[\"FOOD\"]", "RELAXED", "TRANSIT", "湖边", firstTime))).isEqualTo(1);
            assertThat(mapper.replace(new UserTravelPreferenceRow(12, "[\"HISTORY\"]", "COMPACT", "TAXI", "城中", firstTime))).isEqualTo(1);
            UserTravelPreferenceRow inserted = mapper.findByUserId(11);
            assertThat(inserted.userId()).isEqualTo(11);
            assertThat(inserted.interestsJson()).contains("FOOD");
            assertThat(inserted.pace()).isEqualTo("RELAXED");
            assertThat(inserted.updatedAt()).isEqualTo(firstTime);
            assertThat(mapper.replace(new UserTravelPreferenceRow(11, null, null, null, null, secondTime))).isEqualTo(2);
            UserTravelPreferenceRow cleared = mapper.findByUserId(11);
            assertThat(cleared.interestsJson()).isNull();
            assertThat(cleared.pace()).isNull();
            assertThat(cleared.travelMode()).isNull();
            assertThat(cleared.accommodationPreference()).isNull();
            assertThat(cleared.updatedAt()).isEqualTo(secondTime);
            assertThat(mapper.findByUserId(12).interestsJson()).contains("HISTORY");
            assertThat(mapper.deleteByUserId(11)).isEqualTo(1);
            assertThat(mapper.deleteByUserId(11)).isZero();
            assertThat(mapper.findByUserId(12)).isNotNull();
        }
    }
}
