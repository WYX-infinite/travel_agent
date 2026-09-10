package com.wyx.travelagent.mapper;

import com.wyx.travelagent.planning.memory.*;
import com.wyx.travelagent.planning.model.*;
import com.wyx.travelagent.tools.model.TravelMode;
import org.apache.ibatis.annotations.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class UserTravelPreferencePersistenceTest {
    @Test void codecIsAvailableToTheSpringRepositoryAdapter() {
        assertThat(TravelInterestJsonCodec.class).hasAnnotation(Component.class);
    }

    @Test void sqlAlwaysScopesByUserIdAndUpsertOverwritesNullableColumns() throws Exception {
        Method find = UserTravelPreferenceMapper.class.getMethod("findByUserId", Integer.class);
        Method replace = UserTravelPreferenceMapper.class.getMethod("replace", UserTravelPreferenceRow.class);
        Method delete = UserTravelPreferenceMapper.class.getMethod("deleteByUserId", Integer.class);
        for (Method method : List.of(find, delete)) {
            Parameter parameter = method.getParameters()[0];
            assertThat(parameter.getAnnotation(Param.class)).isNotNull();
            assertThat(parameter.getAnnotation(Param.class).value()).isEqualTo("userId");
        }
        String selectSql = String.join(" ", find.getAnnotation(Select.class).value());
        String upsertSql = String.join(" ", replace.getAnnotation(Insert.class).value());
        String deleteSql = String.join(" ", delete.getAnnotation(Delete.class).value());
        assertThat(selectSql).contains("WHERE user_id = #{userId}");
        assertThat(deleteSql).contains("WHERE user_id = #{userId}");
        assertThat(upsertSql).contains("#{userId}", "ON DUPLICATE KEY UPDATE",
                "interests_json = VALUES(interests_json)", "pace = VALUES(pace)",
                "travel_mode = VALUES(travel_mode)", "accommodation_preference = VALUES(accommodation_preference)");
        assertThat(upsertSql).doesNotContain("${");
    }

    @Test void codecIsDeterministicAndRejectsMalformedOrUnknownInterests() {
        TravelInterestJsonCodec codec = new TravelInterestJsonCodec();
        assertThat(codec.encode(Set.of(TravelInterest.SHOPPING, TravelInterest.FOOD)))
                .isEqualTo("[\"FOOD\",\"SHOPPING\"]");
        assertThat(codec.decode("[\"NATURE\",\"FOOD\"]")).containsExactly(TravelInterest.FOOD, TravelInterest.NATURE);
        assertThatThrownBy(() -> codec.decode("not-json")).isInstanceOf(PreferenceMemoryException.class)
                .extracting("code").isEqualTo("INVALID_TRAVEL_PREFERENCE");
        assertThatThrownBy(() -> codec.decode("[\"UNKNOWN\"]")).isInstanceOf(PreferenceMemoryException.class)
                .extracting("code").isEqualTo("INVALID_TRAVEL_PREFERENCE");
        assertThat(codec.decode("[]")).isEmpty();
        assertThat(codec.decode("[\"FOOD\",\"FOOD\"]")).containsExactly(TravelInterest.FOOD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"interest\":\"FOOD\"}", "null", "[\"FOOD\",null]", "[\"FOOD\"] true"})
    void codecRejectsNonArrayNullElementsAndTrailingTokens(String json) {
        assertThatThrownBy(() -> new TravelInterestJsonCodec().decode(json))
                .isInstanceOf(PreferenceMemoryException.class)
                .hasMessage("INVALID_TRAVEL_PREFERENCE");
    }

    @Test void adapterMapsRowsAndWritesNullsForWholeReplacement() {
        FakeMapper mapper = new FakeMapper();
        MybatisUserTravelPreferenceRepository repository = new MybatisUserTravelPreferenceRepository(mapper, new TravelInterestJsonCodec());
        UserTravelPreference value = new UserTravelPreference(41, Set.of(TravelInterest.HISTORY), null, null, null,
                Instant.parse("2026-09-09T01:02:03Z"));
        repository.replace(41, value);
        assertThat(mapper.replaced.userId()).isEqualTo(41);
        assertThat(mapper.replaced.interestsJson()).isEqualTo("[\"HISTORY\"]");
        assertThat(mapper.replaced.pace()).isNull();
        assertThat(mapper.replaced.travelMode()).isNull();
        assertThat(mapper.replaced.accommodationPreference()).isNull();
        mapper.row = new UserTravelPreferenceRow(42, "[\"FOOD\"]", "COMPACT", "TAXI", "湖边",
                Instant.parse("2026-09-09T02:03:04Z"));
        assertThat(repository.findByUserId(42).orElseThrow().travelMode()).isEqualTo(TravelMode.TAXI);
        repository.deleteByUserId(43);
        assertThat(mapper.deletedId).isEqualTo(43);
    }

    private static final class FakeMapper implements UserTravelPreferenceMapper {
        UserTravelPreferenceRow row,replaced; int deletedId;
        public UserTravelPreferenceRow findByUserId(Integer id){return row;}
        public int replace(UserTravelPreferenceRow value){replaced=value;return 1;}
        public int deleteByUserId(Integer id){deletedId=id;return 0;}
    }
}
