package com.wyx.travelagent.planning.memory;

import com.wyx.travelagent.planning.model.*;
import com.wyx.travelagent.tools.model.TravelMode;
import com.wyx.travelagent.planning.validation.RequirementValidator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PreferenceMemoryServiceTest {
    @Test void snapshotUsesTheSameNormalizedValuesAsTheFinalRequirement() {
        TravelRequirementInput input = request()
                .withInterests(PreferenceValue.explicitValue(List.of(" nature ", "food")))
                .withPace(PreferenceValue.explicitValue(" relaxed "))
                .withTravelMode(PreferenceValue.explicitValue(" transit "))
                .withAccommodationAddress(PreferenceValue.explicitValue("  西湖边  "));
        PreferenceMergeResult result = new PreferenceMemoryService(new FakeRepo()).merge(7, input);
        TravelRequirement finalRequirement = new RequirementValidator().validate(result.mergedInput());
        assertThat(result.snapshot().interests()).isEqualTo(finalRequirement.interests());
        assertThat(result.snapshot().pace()).isEqualTo(finalRequirement.pace());
        assertThat(result.snapshot().travelMode()).isEqualTo(finalRequirement.travelMode());
        assertThat(result.snapshot().accommodationPreference()).isEqualTo(finalRequirement.accommodationAddress());
    }

    @Test void missAndFailureAreDistinctAndDoNotChangeRequest() {
        FakeRepo repo = new FakeRepo(); TravelRequirementInput input = request();
        PreferenceMergeResult miss = new PreferenceMemoryService(repo).merge(7, input);
        assertThat(miss.mergedInput()).isSameAs(input);
        assertThat(miss.snapshot().readStatus()).isEqualTo(PreferenceReadStatus.SUCCEEDED);
        assertThat(miss.snapshot().interestsSource()).isEqualTo(PreferenceSource.NOT_PROVIDED);
        repo.failRead = true;
        PreferenceMergeResult failed = new PreferenceMemoryService(repo).merge(7, input);
        assertThat(failed.mergedInput()).isSameAs(input);
        assertThat(failed.snapshot().readStatus()).isEqualTo(PreferenceReadStatus.FAILED);
    }

    @Test void mergeDoesNotMisreportPostRepositoryContractFailuresAsReadFailures() {
        FakeRepo repo = new FakeRepo(); repo.returnNullOptional = true;
        assertThatThrownBy(() -> new PreferenceMemoryService(repo).merge(7, request()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void findDistinguishesMissStorageFailureInvalidDataAndWrongOwner() {
        FakeRepo repo = new FakeRepo(); PreferenceMemoryService service = new PreferenceMemoryService(repo);
        assertThat(service.find(7)).isEmpty();
        repo.readFailure = new IllegalStateException("database secret");
        assertThatThrownBy(() -> service.find(7)).isInstanceOf(PreferenceMemoryException.class)
                .hasMessage("PREFERENCE_STORAGE_FAILED").extracting("code").isEqualTo("PREFERENCE_STORAGE_FAILED");
        repo.readFailure = new PreferenceMemoryException("INVALID_TRAVEL_PREFERENCE", new IllegalStateException("bad json secret"));
        assertThatThrownBy(() -> service.find(7)).isInstanceOf(PreferenceMemoryException.class)
                .hasMessage("INVALID_TRAVEL_PREFERENCE").extracting("code").isEqualTo("INVALID_TRAVEL_PREFERENCE");
        repo.readFailure = null;
        repo.forcedRead = pref(8, EnumSet.of(TravelInterest.FOOD), null, null, null);
        assertThatThrownBy(() -> service.find(7)).isInstanceOf(PreferenceMemoryException.class)
                .hasMessage("INVALID_TRAVEL_PREFERENCE").extracting("code").isEqualTo("INVALID_TRAVEL_PREFERENCE");
    }

    @Test void fillsFourMissingFieldsAndFreezesActualValuesAndSources() {
        FakeRepo repo = new FakeRepo();
        repo.savedByUser.put(7, pref(7, EnumSet.of(TravelInterest.FOOD, TravelInterest.NATURE), TravelPace.RELAXED, TravelMode.TRANSIT, "西湖边"));
        PreferenceMergeResult result = new PreferenceMemoryService(repo).merge(7, request());
        assertThat(result.mergedInput().interests()).isEqualTo(PreferenceValue.explicitValue(List.of("FOOD", "NATURE")));
        assertThat(result.mergedInput().pace()).isEqualTo(PreferenceValue.explicitValue("RELAXED"));
        assertThat(result.mergedInput().travelMode()).isEqualTo(PreferenceValue.explicitValue("TRANSIT"));
        assertThat(result.mergedInput().accommodationAddress()).isEqualTo(PreferenceValue.explicitValue("西湖边"));
        AppliedPreferenceSnapshot snapshot = result.snapshot(); repo.savedByUser.clear();
        assertThat(snapshot.interests()).containsExactly(TravelInterest.FOOD, TravelInterest.NATURE);
        assertThat(snapshot.pace()).isEqualTo(TravelPace.RELAXED);
        assertThat(snapshot.travelMode()).isEqualTo(TravelMode.TRANSIT);
        assertThat(snapshot.accommodationPreference()).isEqualTo("西湖边");
        assertThat(snapshot.interestsSource()).isEqualTo(PreferenceSource.SAVED_PREFERENCE);
        assertThatThrownBy(() -> snapshot.interests().add(TravelInterest.SHOPPING)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void explicitValueAndEmptyOverrideSavedAndHardFieldsStaySame() {
        FakeRepo repo = new FakeRepo(); repo.savedByUser.put(7, pref(7, EnumSet.of(TravelInterest.FOOD), TravelPace.RELAXED, TravelMode.TRANSIT, "旧"));
        TravelRequirementInput input = request().withInterests(PreferenceValue.explicitEmpty())
                .withPace(PreferenceValue.explicitValue("COMPACT")).withTravelMode(PreferenceValue.explicitEmpty())
                .withAccommodationAddress(PreferenceValue.explicitValue("新")).withSpecialRequirements("  原样  ");
        PreferenceMergeResult result = new PreferenceMemoryService(repo).merge(7, input);
        assertThat(result.mergedInput()).isSameAs(input);
        assertThat(result.snapshot().interests()).isEmpty();
        assertThat(result.snapshot().pace()).isEqualTo(TravelPace.COMPACT);
        assertThat(result.snapshot().travelMode()).isNull();
        assertThat(result.snapshot().accommodationPreference()).isEqualTo("新");
        assertThat(result.snapshot().paceSource()).isEqualTo(PreferenceSource.EXPLICIT_REQUEST);
        assertThat(result.mergedInput().destinationCity()).isSameAs(input.destinationCity());
        assertThat(result.mergedInput().startDate()).isSameAs(input.startDate());
        assertThat(result.mergedInput().travelers()).isSameAs(input.travelers());
        assertThat(result.mergedInput().totalBudget()).isSameAs(input.totalBudget());
        assertThat(result.mergedInput().companionType()).isSameAs(input.companionType());
        assertThat(result.mergedInput().specialRequirements()).isSameAs(input.specialRequirements());
    }

    @Test void falseNeverReads() {
        FakeRepo repo = new FakeRepo(); repo.failRead = true;
        PreferenceMergeResult result = new PreferenceMemoryService(repo).merge(7, request().withUseSavedPreferences(false));
        assertThat(repo.findCount).isZero();
        assertThat(result.snapshot().requested()).isFalse();
        assertThat(result.snapshot().readStatus()).isEqualTo(PreferenceReadStatus.NOT_REQUESTED);
    }

    @Test void replaceStripsAccommodationAndRejectsNonNullBlank() {
        FakeRepo repo = new FakeRepo(); PreferenceMemoryService service = new PreferenceMemoryService(repo);
        service.replace(7, pref(7, EnumSet.of(TravelInterest.FOOD), null, null, "  湖边  "));
        assertThat(repo.replaced.accommodationPreference()).isEqualTo("湖边");
        assertThatThrownBy(() -> service.replace(7,
                pref(7, EnumSet.of(TravelInterest.FOOD), null, null, "   ")))
                .isInstanceOf(PreferenceMemoryException.class)
                .extracting("code").isEqualTo("INVALID_TRAVEL_PREFERENCE");
    }

    @Test void writesAreIsolatedWholeReplaceClearIsIdempotentAndFailuresStable() {
        FakeRepo repo = new FakeRepo(); PreferenceMemoryService service = new PreferenceMemoryService(repo);
        UserTravelPreference empty = pref(11, EnumSet.noneOf(TravelInterest.class), null, null, null);
        service.replace(11, empty); service.merge(12, request()); service.clear(13); service.clear(13);
        assertThat(repo.replaceId).isEqualTo(11); assertThat(repo.findId).isEqualTo(12); assertThat(repo.deleteId).isEqualTo(13);
        assertThat(repo.replaced).isEqualTo(empty); assertThat(repo.replaced.pace()).isNull(); assertThat(repo.deleteCount).isEqualTo(2);
        repo.failWrite = true;
        UserTravelPreference failureValue = pref(7, EnumSet.noneOf(TravelInterest.class), null, null, null);
        assertThatThrownBy(() -> service.replace(7, failureValue)).isInstanceOf(PreferenceMemoryException.class)
                .extracting("code").isEqualTo("PREFERENCE_STORAGE_FAILED");
        assertThatThrownBy(() -> service.clear(7)).isInstanceOf(PreferenceMemoryException.class)
                .extracting("code").isEqualTo("PREFERENCE_STORAGE_FAILED");
    }

    @Test void rejectsInvalidTrustedUserAndPreferenceWithStableCode() {
        PreferenceMemoryService service = new PreferenceMemoryService(new FakeRepo());
        assertThatThrownBy(() -> service.merge(0, request())).isInstanceOf(PreferenceMemoryException.class)
                .extracting("code").isEqualTo("INVALID_TRAVEL_PREFERENCE");
        assertThatThrownBy(() -> service.find(null)).isInstanceOf(PreferenceMemoryException.class)
                .extracting("code").isEqualTo("INVALID_TRAVEL_PREFERENCE");
        assertThatThrownBy(() -> service.replace(7, pref(7, EnumSet.of(TravelInterest.FOOD), null, null, "x".repeat(256))))
                .isInstanceOf(PreferenceMemoryException.class).extracting("code").isEqualTo("INVALID_TRAVEL_PREFERENCE");
        assertThatThrownBy(() -> service.replace(7, pref(8, EnumSet.of(TravelInterest.FOOD), null, null, null)))
                .isInstanceOf(PreferenceMemoryException.class).extracting("code").isEqualTo("INVALID_TRAVEL_PREFERENCE");
    }

    @Test void usersArePartitionedAndMismatchedRepositoryRowsAreNeverApplied() {
        FakeRepo repo = new FakeRepo(); PreferenceMemoryService service = new PreferenceMemoryService(repo);
        service.replace(11, pref(11, EnumSet.of(TravelInterest.FOOD), TravelPace.RELAXED, null, null));
        service.replace(12, pref(12, EnumSet.of(TravelInterest.HISTORY), TravelPace.COMPACT, null, null));
        assertThat(service.merge(11, request()).snapshot().interests()).containsExactly(TravelInterest.FOOD);
        assertThat(service.merge(12, request()).snapshot().interests()).containsExactly(TravelInterest.HISTORY);
        service.clear(12);
        assertThat(service.merge(12, request()).snapshot().interests()).isEmpty();
        assertThat(service.merge(11, request()).snapshot().interests()).containsExactly(TravelInterest.FOOD);
        repo.forcedRead = pref(99, EnumSet.of(TravelInterest.SHOPPING), null, null, null);
        PreferenceMergeResult guarded = service.merge(11, request());
        assertThat(guarded.snapshot().interests()).isEmpty();
        assertThat(guarded.snapshot().readStatus()).isEqualTo(PreferenceReadStatus.FAILED);
    }

    private static TravelRequirementInput request() {
        return new TravelRequirementInput("杭州", LocalDate.of(2026,10,1), LocalDate.of(2026,10,2), 2,
                new BigDecimal("3000"), "FRIENDS", PreferenceValue.notProvided(), PreferenceValue.notProvided(),
                PreferenceValue.notProvided(), PreferenceValue.notProvided(), "不要早起", true);
    }
    private static UserTravelPreference pref(Integer userId, EnumSet<TravelInterest> i, TravelPace p, TravelMode m, String a) {
        return new UserTravelPreference(userId,i,p,m,a,Instant.parse("2026-09-09T00:00:00Z"));
    }
    private static final class FakeRepo implements UserTravelPreferenceRepository {
        final Map<Integer,UserTravelPreference> savedByUser = new HashMap<>();
        UserTravelPreference replaced,forcedRead; int findCount,deleteCount; Integer findId,replaceId,deleteId; boolean failRead,failWrite,returnNullOptional;
        RuntimeException readFailure;
        public Optional<UserTravelPreference> findByUserId(Integer id){findCount++;findId=id;if(readFailure!=null)throw readFailure;if(failRead)throw new IllegalStateException("secret");if(returnNullOptional)return null;return Optional.ofNullable(forcedRead != null ? forcedRead : savedByUser.get(id));}
        public void replace(Integer id,UserTravelPreference p){replaceId=id;if(failWrite)throw new IllegalStateException("secret");replaced=p;savedByUser.put(id,p);}
        public void deleteByUserId(Integer id){deleteId=id;deleteCount++;if(failWrite)throw new IllegalStateException("secret");savedByUser.remove(id);}
    }
}
