package com.wyx.travelagent.planning.memory;

import com.wyx.travelagent.planning.model.*;
import com.wyx.travelagent.tools.model.TravelMode;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * 业务问题：在可信用户边界内读取、整体覆盖、清空并确定性合并四类长期旅行偏好。
 * 输入显式接受认证层提供的正用户编号和不含用户编号的规划输入，输出合并输入与冻结快照；本服务不调用模型、缓存、文件或网络。
 * 任务十仅提供交通枚举，任务十二负责后续行程校验；后续编排消费合并结果，本服务不负责认证、Controller 或行程生成。
 */
@Service
public final class PreferenceMemoryService {
    private static final String INVALID = "INVALID_TRAVEL_PREFERENCE";
    private static final String STORAGE_FAILED = "PREFERENCE_STORAGE_FAILED";
    private final UserTravelPreferenceRepository repository;

    /**
     * 创建偏好记忆服务。
     * @param repository 用户隔离的长期偏好仓储端口
     * 仓储不得为空；构造本身不访问数据库、模型、缓存、文件或网络，也不处理认证。
     * @throws NullPointerException 当 repository 为 null
     */
    public PreferenceMemoryService(UserTravelPreferenceRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * 在仅补全未提供字段的前提下合并长期偏好。
     * @param userId 认证层提供的正用户编号，不得为 null 或非正数
     * @param input 不含用户编号的非空本次规划输入
     * @return 尚待校验的合并输入和冻结来源快照
     * 读取失败会降级并记录 FAILED，不泄露底层异常；除读取数据库仓储外不调用模型、缓存、文件或网络，认证由上层入口负责。
     * @throws PreferenceMemoryException 当 userId 为 null 或非正数，错误码为 INVALID_TRAVEL_PREFERENCE
     * @throws NullPointerException 当 input 为 null
     */
    public PreferenceMergeResult merge(Integer userId, TravelRequirementInput input) {
        Integer trustedUserId = validUserId(userId);
        Objects.requireNonNull(input, "input");
        if (!input.shouldUseSavedPreferences()) {
            return result(input, null, false, PreferenceReadStatus.NOT_REQUESTED);
        }
        Optional<UserTravelPreference> stored;
        try {
            stored = repository.findByUserId(trustedUserId);
        } catch (RuntimeException ignored) {
            // 规划可在没有长期偏好的情况下继续；失败与成功无记录通过 readStatus 明确区分。
            return result(input, null, true, PreferenceReadStatus.FAILED);
        }
        UserTravelPreference saved = Objects.requireNonNull(stored, "repository result").orElse(null);
        if (saved != null && !trustedUserId.equals(saved.userId())) {
            // 错主体是读取数据完整性失败，但仓储调用已经正常返回，不得依赖总括 catch 掩盖后续错误。
            return result(input, null, true, PreferenceReadStatus.FAILED);
        }
        return result(input, saved, true, PreferenceReadStatus.SUCCEEDED);
    }

    /**
     * 读取当前可信用户的完整长期偏好。
     * @param userId 认证层提供的正用户编号，不得为 null 或非正数
     * @return 成功读取的可选偏好，真实无记录时为空
     * 存储故障、坏数据和错主体使用稳定异常且消息不含底层原因；不调用模型、缓存、文件或网络，认证由上层入口负责。
     * @throws PreferenceMemoryException 当 userId 为 null 或非正数，错误码为 INVALID_TRAVEL_PREFERENCE
     * @throws PreferenceMemoryException 当存储访问失败时为 PREFERENCE_STORAGE_FAILED，坏数据或错主体时保留 INVALID_TRAVEL_PREFERENCE
     */
    public Optional<UserTravelPreference> find(Integer userId) {
        Integer trustedUserId = validUserId(userId);
        Optional<UserTravelPreference> stored;
        try { stored = repository.findByUserId(trustedUserId); }
        catch (PreferenceMemoryException cause) { throw cause; }
        catch (RuntimeException cause) { throw new PreferenceMemoryException(STORAGE_FAILED, cause); }
        Optional<UserTravelPreference> checked = Objects.requireNonNull(stored, "repository result");
        if (checked.isPresent() && !trustedUserId.equals(checked.orElseThrow().userId())) {
            throw new PreferenceMemoryException(INVALID, null);
        }
        return checked;
    }

    /**
     * 整体覆盖当前可信用户的四类长期偏好。
     * @param userId 认证层提供的正用户编号，不得为 null 或非正数
     * @param preference 完整替换值，null 字段会清除旧值
     * 无返回值；除写入数据库仓储外不调用模型、缓存、文件或网络，认证由上层入口负责；失败使用稳定代码。
     * @throws PreferenceMemoryException 当 userId、preference 或主体对应关系非法时为 INVALID_TRAVEL_PREFERENCE，仓储失败时为 PREFERENCE_STORAGE_FAILED
     */
    public void replace(Integer userId, UserTravelPreference preference) {
        Integer trustedUserId = validUserId(userId);
        validatePreference(preference);
        if (!trustedUserId.equals(preference.userId())) throw new PreferenceMemoryException(INVALID, null);
        try { repository.replace(trustedUserId, preference); }
        catch (RuntimeException cause) { throw new PreferenceMemoryException(STORAGE_FAILED, cause); }
    }

    /**
     * 幂等清空当前可信用户的长期偏好。
     * @param userId 认证层提供的正用户编号，不得为 null 或非正数
     * 无记录也成功；除删除数据库记录外不调用模型、缓存、文件或网络，认证由上层入口负责；仓储失败使用稳定代码。
     * @throws PreferenceMemoryException 当 userId 非法时为 INVALID_TRAVEL_PREFERENCE，仓储失败时为 PREFERENCE_STORAGE_FAILED
     */
    public void clear(Integer userId) {
        Integer trustedUserId = validUserId(userId);
        try { repository.deleteByUserId(trustedUserId); }
        catch (RuntimeException cause) { throw new PreferenceMemoryException(STORAGE_FAILED, cause); }
    }

    private PreferenceMergeResult result(TravelRequirementInput input, UserTravelPreference saved,
                                         boolean requested, PreferenceReadStatus readStatus) {
        PreferenceValue<List<String>> interests = mergeInterests(input.interests(), saved);
        PreferenceValue<String> pace = mergeText(input.pace(), saved == null || saved.pace() == null ? null : saved.pace().name());
        PreferenceValue<String> mode = mergeText(input.travelMode(), saved == null || saved.travelMode() == null ? null : saved.travelMode().name());
        PreferenceValue<String> accommodation = mergeText(input.accommodationAddress(), saved == null ? null : saved.accommodationPreference());
        boolean changed = interests != input.interests() || pace != input.pace() || mode != input.travelMode() || accommodation != input.accommodationAddress();
        TravelRequirementInput merged = changed ? new TravelRequirementInput(input.destinationCity(), input.startDate(), input.endDate(),
                input.travelers(), input.totalBudget(), input.companionType(), interests, pace, accommodation, mode,
                input.specialRequirements(), input.useSavedPreferences()) : input;
        AppliedPreferenceSnapshot snapshot = new AppliedPreferenceSnapshot(toInterests(interests), toEnum(pace, TravelPace.class),
                toEnum(mode, TravelMode.class), textValue(accommodation), source(input.interests(), saved != null && !saved.interests().isEmpty()),
                source(input.pace(), saved != null && saved.pace() != null), source(input.travelMode(), saved != null && saved.travelMode() != null),
                source(input.accommodationAddress(), saved != null && saved.accommodationPreference() != null), requested, readStatus);
        return new PreferenceMergeResult(merged, snapshot);
    }

    private static PreferenceValue<List<String>> mergeInterests(PreferenceValue<List<String>> request, UserTravelPreference saved) {
        if (request.state() != PreferenceValue.State.NOT_PROVIDED || saved == null || saved.interests().isEmpty()) return request;
        return PreferenceValue.explicitValue(saved.interests().stream().map(Enum::name).toList());
    }
    private static PreferenceValue<String> mergeText(PreferenceValue<String> request, String saved) {
        return request.state() == PreferenceValue.State.NOT_PROVIDED && saved != null ? PreferenceValue.explicitValue(saved) : request;
    }
    private static PreferenceSource source(PreferenceValue<?> request, boolean hasSaved) {
        if (request.state() != PreferenceValue.State.NOT_PROVIDED) return PreferenceSource.EXPLICIT_REQUEST;
        return hasSaved ? PreferenceSource.SAVED_PREFERENCE : PreferenceSource.NOT_PROVIDED;
    }
    private static Set<TravelInterest> toInterests(PreferenceValue<List<String>> value) {
        if (value.state() != PreferenceValue.State.EXPLICIT_VALUE) return Set.of();
        EnumSet<TravelInterest> result = EnumSet.noneOf(TravelInterest.class);
        for (String item : value.value()) try { result.add(TravelInterest.valueOf(item)); } catch (RuntimeException ignored) { }
        return result;
    }
    private static <E extends Enum<E>> E toEnum(PreferenceValue<String> value, Class<E> type) {
        if (value.state() != PreferenceValue.State.EXPLICIT_VALUE) return null;
        try { return Enum.valueOf(type, value.value().strip().toUpperCase(Locale.ROOT)); }
        catch (RuntimeException ignored) { return null; }
    }
    private static String textValue(PreferenceValue<String> value) {
        return value.state() == PreferenceValue.State.EXPLICIT_VALUE ? value.value().strip() : null;
    }
    private static Integer validUserId(Integer userId) {
        if (userId == null || userId <= 0) throw new PreferenceMemoryException(INVALID, null);
        return userId;
    }
    private static void validatePreference(UserTravelPreference preference) {
        if (preference == null || preference.interests().stream().anyMatch(Objects::isNull)
                || preference.accommodationPreference() != null && (preference.accommodationPreference().isBlank()
                || preference.accommodationPreference().length() > 255)) {
            throw new PreferenceMemoryException(INVALID, null);
        }
    }
}
