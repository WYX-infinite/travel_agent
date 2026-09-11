package com.wyx.travelagent.planning.api;

import com.wyx.travelagent.planning.memory.AppliedPreferenceSnapshot;
import com.wyx.travelagent.planning.memory.PreferenceSource;
import java.util.ArrayList;
import java.util.List;

/**
 * 业务问题：只公开本次规划实际从长期偏好采用的字段和值，避免泄漏来源细节或偏好历史。
 * 输入是任务创建时冻结的快照，输出稳定 JSON DTO；本类型不访问数据库、模型、缓存、文件、线程池或网络，也不处理认证。
 * 它连接任务十一偏好记忆、任务十三规划结果与任务十四详情；不负责公开显式请求值、读取故障内部状态、历史行程或任务十五页面。
 * @param enabled 本次是否请求使用长期偏好
 * @param interests 非空、仅在兴趣来自长期偏好时包含枚举名
 * @param pace 可为空、仅在节奏来自长期偏好时返回
 * @param travelMode 可为空、仅在交通方式来自长期偏好时返回
 * @param accommodationPreference 可为空、仅在住宿偏好来自长期偏好时返回
 * @param adoptedFields 非空、固定顺序列出实际采用的字段名
 */
public record AppliedPreferencesResponse(boolean enabled, List<String> interests, String pace,
        String travelMode, String accommodationPreference, List<String> adoptedFields) {
    /**
     * @param snapshot 可为空冻结快照；为空表示没有可展示的长期偏好采用事实
     * @return 非空稳定 DTO；没有采用字段时列表为空、可选值为 null
     * 本方法只读内存，不访问偏好数据库，不处理认证，也不返回稳定错误码。
     */
    public static AppliedPreferencesResponse from(AppliedPreferenceSnapshot snapshot) {
        if (snapshot == null) return new AppliedPreferencesResponse(false, List.of(), null, null, null, List.of());
        List<String> fields = new ArrayList<>();
        boolean savedInterests = snapshot.interestsSource() == PreferenceSource.SAVED_PREFERENCE;
        boolean savedPace = snapshot.paceSource() == PreferenceSource.SAVED_PREFERENCE;
        boolean savedMode = snapshot.travelModeSource() == PreferenceSource.SAVED_PREFERENCE;
        boolean savedAccommodation = snapshot.accommodationPreferenceSource() == PreferenceSource.SAVED_PREFERENCE;
        if (savedInterests) fields.add("interests");
        if (savedPace) fields.add("pace");
        if (savedMode) fields.add("travelMode");
        if (savedAccommodation) fields.add("accommodationPreference");
        return new AppliedPreferencesResponse(snapshot.requested(),
                savedInterests ? snapshot.interests().stream().map(Enum::name).toList() : List.of(),
                savedPace && snapshot.pace() != null ? snapshot.pace().name() : null,
                savedMode && snapshot.travelMode() != null ? snapshot.travelMode().name() : null,
                savedAccommodation ? snapshot.accommodationPreference() : null, List.copyOf(fields));
    }
}
