package com.wyx.travelagent.planning.validation;

import com.wyx.travelagent.planning.model.CompanionType;
import com.wyx.travelagent.planning.model.PreferenceValue;
import com.wyx.travelagent.planning.model.TravelInterest;
import com.wyx.travelagent.planning.model.TravelPace;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirementInput;
import com.wyx.travelagent.tools.model.TravelMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequirementValidatorTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private final RequirementValidator validator = new RequirementValidator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidHardConstraints")
    void rejectsMissingOrInvalidHardConstraints(String name, TravelRequirementInput input, String expectedField) {
        assertInvalid(input, expectedField);
    }

    static Stream<Arguments> invalidHardConstraints() {
        return Stream.of(
                Arguments.of("城市为 null", validInput().withDestinationCity(null), "destinationCity"),
                Arguments.of("城市为空", validInput().withDestinationCity(""), "destinationCity"),
                Arguments.of("城市仅有空格", validInput().withDestinationCity("   "), "destinationCity"),
                Arguments.of("人数为 null", validInput().withTravelers(null), "travelers"),
                Arguments.of("人数为零", validInput().withTravelers(0), "travelers"),
                Arguments.of("人数为负数", validInput().withTravelers(-1), "travelers"),
                Arguments.of("预算为 null", validInput().withTotalBudget(null), "totalBudget"),
                Arguments.of("预算为零", validInput().withTotalBudget(BigDecimal.ZERO), "totalBudget"),
                Arguments.of("预算为负数", validInput().withTotalBudget(new BigDecimal("-0.01")), "totalBudget"),
                Arguments.of("开始日期为 null", validInput().withDates(null, START), "startDate"),
                Arguments.of("结束日期为 null", validInput().withDates(START, null), "endDate"),
                Arguments.of("结束日期早于开始日期", validInput().withDates(START, START.minusDays(1)), "dateRange"),
                Arguments.of("超过五个自然日", validInput().withDates(START, START.plusDays(5)), "dateRange"),
                Arguments.of("同行类型缺失", validInput().withCompanionType(null), "companionType"),
                Arguments.of("同行类型空白", validInput().withCompanionType("  "), "companionType")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("supportedBoundaryDurations")
    void acceptsInclusiveDurationsFromOneToFiveDays(String name, LocalDate endDate, int expectedDays) {
        TravelRequirement result = validator.validate(validInput().withDates(START, endDate));

        assertThat(result.durationDays()).isEqualTo(expectedDays);
    }

    static Stream<Arguments> supportedBoundaryDurations() {
        return Stream.of(
                Arguments.of("同一天为一天行程", START, 1),
                Arguments.of("首尾共五天", START.plusDays(4), 5));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsupportedEnumValues")
    void safelyRejectsUnsupportedExternalEnumValues(String name, TravelRequirementInput input, String expectedField) {
        assertInvalid(input, expectedField);
    }

    static Stream<Arguments> unsupportedEnumValues() {
        return Stream.of(
                Arguments.of("未知同行类型", validInput().withCompanionType("BUSINESS_PARTNERS"), "companionType"),
                Arguments.of("未知兴趣", validInput().withInterests(PreferenceValue.explicitValue(List.of("FOOD", "SPACE"))), "interests"),
                Arguments.of("未知节奏", validInput().withPace(PreferenceValue.explicitValue("EXTREME")), "pace"),
                Arguments.of("未知交通方式", validInput().withTravelMode(PreferenceValue.explicitValue("FLYING")), "travelMode"));
    }

    @Test
    void enforcesDocumentedOptionalTextLengthBoundaries() {
        validator.validate(validInput()
                .withAccommodationAddress(PreferenceValue.explicitValue("住".repeat(255)))
                .withSpecialRequirements("需".repeat(1000)));

        assertInvalid(validInput().withAccommodationAddress(
                PreferenceValue.explicitValue("住".repeat(256))), "accommodationAddress");
        assertInvalid(validInput().withSpecialRequirements("需".repeat(1001)), "specialRequirements");
    }

    @Test
    void convertsAndNormalizesAValidRequestToImmutableRequirement() {
        TravelRequirement requirement = validator.validate(validInput());

        assertThat(requirement.destinationCity()).isEqualTo("杭州");
        assertThat(requirement.startDate()).isEqualTo(START);
        assertThat(requirement.endDate()).isEqualTo(START.plusDays(2));
        assertThat(requirement.travelers()).isEqualTo(2);
        assertThat(requirement.totalBudget()).isEqualByComparingTo("3000.00");
        assertThat(requirement.companionType()).isEqualTo(CompanionType.FAMILY);
        assertThat(requirement.interests()).containsExactly(TravelInterest.FOOD, TravelInterest.HISTORY);
        assertThat(requirement.pace()).isEqualTo(TravelPace.MODERATE);
        assertThat(requirement.accommodationAddress()).isEqualTo("西湖区龙井路");
        assertThat(requirement.travelMode()).isEqualTo(TravelMode.TRANSIT);
        assertThat(requirement.specialRequirements()).isEqualTo("需要无障碍路线");
        assertThatThrownBy(() -> requirement.interests().add(TravelInterest.NATURE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void directRequirementConstructionNormalizesSpecialRequirementsWithoutCollapsingEmptyAndNull() {
        TravelRequirement base = validator.validate(validInput());

        TravelRequirement text = requirementWithSpecial(base, "  安静房间  ");
        TravelRequirement empty = requirementWithSpecial(base, "   ");
        TravelRequirement missing = requirementWithSpecial(base, null);

        assertThat(text.specialRequirements()).isEqualTo("安静房间");
        assertThat(empty.specialRequirements()).isEmpty();
        assertThat(missing.specialRequirements()).isNull();
    }

    @Test
    void preferenceInputPreservesThreeStatesAndDefensivelyCopiesInterestOrder() {
        List<String> mutable = new ArrayList<>(List.of("HISTORY", "food", "FOOD", "HISTORY"));
        TravelRequirementInput input = validInput().withInterests(PreferenceValue.explicitValue(mutable));
        mutable.clear();

        assertThat(PreferenceValue.notProvided().state()).isEqualTo(PreferenceValue.State.NOT_PROVIDED);
        assertThat(PreferenceValue.explicitEmpty().state()).isEqualTo(PreferenceValue.State.EXPLICIT_EMPTY);
        assertThat(input.interests().state()).isEqualTo(PreferenceValue.State.EXPLICIT_VALUE);
        assertThat(input.interests().value()).containsExactly("FOOD", "HISTORY");
        assertThatThrownBy(() -> input.interests().value().add("NATURE"))
                .isInstanceOf(UnsupportedOperationException.class);

        TravelRequirement emptyInterests = validator.validate(
                validInput().withInterests(PreferenceValue.explicitEmpty()));
        assertThat(emptyInterests.interests()).isEmpty();
    }

    @Test
    void explicitListPreferenceValueDefensivelyCopiesAndDoesNotExposeMutableState() {
        List<String> mutable = new ArrayList<>(List.of("FOOD", "HISTORY"));

        PreferenceValue<List<String>> preference = PreferenceValue.explicitValue(mutable);
        mutable.set(0, "NATURE");
        mutable.add("SHOPPING");

        assertThat(preference.value()).containsExactly("FOOD", "HISTORY");
        assertThatThrownBy(() -> preference.value().add("PHOTOGRAPHY"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void exposesRequirementSpecificErrorElementNameWithoutUsingTaskTwelveIssueName() {
        assertThatThrownBy(() -> validator.validate(validInput().withTravelers(0)))
                .isInstanceOfSatisfying(RequirementValidator.ValidationException.class, exception -> {
                    String simpleName = exception.issues().getFirst().getClass().getSimpleName();
                    assertThat(simpleName).isEqualTo("RequirementValidationError");
                    assertThat(simpleName).isNotEqualTo("ValidationIssue");
                });
    }

    @Test
    void onlyExplicitFalseDisablesSavedPreferences() {
        assertThat(validInput().withUseSavedPreferences(null).shouldUseSavedPreferences()).isTrue();
        assertThat(validInput().withUseSavedPreferences(true).shouldUseSavedPreferences()).isTrue();
        assertThat(validInput().withUseSavedPreferences(false).shouldUseSavedPreferences()).isFalse();
    }

    @Test
    void rejectsBlankExplicitTextWithoutConfusingItWithNotProvidedOrExplicitEmpty() {
        TravelRequirementInput input = validInput()
                .withAccommodationAddress(PreferenceValue.explicitValue("   "));

        assertThat(input.accommodationAddress().state()).isEqualTo(PreferenceValue.State.EXPLICIT_VALUE);
        assertInvalid(input, "accommodationAddress");
        assertThat(validator.validate(validInput().withAccommodationAddress(
                PreferenceValue.explicitEmpty())).accommodationAddress()).isNull();
        assertThat(validator.validate(validInput().withAccommodationAddress(
                PreferenceValue.notProvided())).accommodationAddress()).isNull();
    }

    @Test
    void reportsAllProblemsInStableOrderWithoutLeakingSensitiveOrRawValues() {
        String attackerControlled = "jdbc:mysql://localhost/private JWT C:\\secret\\application-local.yml";
        TravelRequirementInput input = new TravelRequirementInput(
                attackerControlled, null, null, 0, BigDecimal.ZERO, "UNKNOWN",
                PreferenceValue.explicitValue(List.of("UNKNOWN_INTEREST")),
                PreferenceValue.explicitValue("UNKNOWN_PACE"),
                PreferenceValue.explicitValue(" "),
                PreferenceValue.explicitValue("UNKNOWN_MODE"),
                attackerControlled.repeat(50), null);

        assertThatThrownBy(() -> validator.validate(input))
                .isInstanceOfSatisfying(RequirementValidator.ValidationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("INVALID_TRAVEL_REQUIREMENT");
                    assertThat(exception.issues()).extracting(RequirementValidator.RequirementValidationError::field)
                            .containsExactly("startDate", "endDate", "travelers", "totalBudget",
                                    "companionType", "interests", "pace", "accommodationAddress",
                                    "travelMode", "specialRequirements");
                    assertThat(exception.getMessage()).isEqualTo("INVALID_TRAVEL_REQUIREMENT");
                    assertThat(exception.toString())
                            .doesNotContain("jdbc", "SQL", "JWT", "application-local.yml", "C:\\secret");
                });
    }

    private void assertInvalid(TravelRequirementInput input, String expectedField) {
        assertThatThrownBy(() -> validator.validate(input))
                .isInstanceOfSatisfying(RequirementValidator.ValidationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("INVALID_TRAVEL_REQUIREMENT");
                    assertThat(exception.issues()).extracting(RequirementValidator.RequirementValidationError::field)
                            .contains(expectedField);
                });
    }

    private static TravelRequirementInput validInput() {
        return new TravelRequirementInput(
                "  杭州  ", START, START.plusDays(2), 2, new BigDecimal("3000.00"), " family ",
                PreferenceValue.explicitValue(List.of("HISTORY", "FOOD", "HISTORY")),
                PreferenceValue.explicitValue(" moderate "),
                PreferenceValue.explicitValue(" 西湖区龙井路 "),
                PreferenceValue.explicitValue(" transit "),
                " 需要无障碍路线 ", null);
    }

    private static TravelRequirement requirementWithSpecial(TravelRequirement base, String specialRequirements) {
        return new TravelRequirement(
                base.destinationCity(), base.startDate(), base.endDate(), base.travelers(),
                base.totalBudget(), base.companionType(), base.interests(), base.pace(),
                base.accommodationAddress(), base.travelMode(), specialRequirements);
    }
}
