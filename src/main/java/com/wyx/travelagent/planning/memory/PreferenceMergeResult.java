package com.wyx.travelagent.planning.memory;

import com.wyx.travelagent.planning.model.TravelRequirementInput;
import java.util.Objects;

/**
 * 业务问题：把偏好合并后的输入与同一时刻的来源快照绑定，避免二者脱节。
 * 输入是合并输入和冻结快照，输出是不可变组合；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十与任务十二不修改本结果；后续编排可把合并输入交给校验器，本类型不负责校验、生成或持久化。
 * @param mergedInput 完成确定性补全但尚未校验的输入
 * @param snapshot 同次合并的冻结快照
 */
public record PreferenceMergeResult(TravelRequirementInput mergedInput, AppliedPreferenceSnapshot snapshot) {
    /**
     * 创建合并结果。
     * 两项输入均不得为 null，且保持其不可变值；不接受显式空对象替代。
     * 构造不访问数据库、模型、缓存、文件或网络，不处理认证。
     * @throws NullPointerException 当 mergedInput 或 snapshot 为 null
     */
    public PreferenceMergeResult { Objects.requireNonNull(mergedInput, "mergedInput"); Objects.requireNonNull(snapshot, "snapshot"); }
}
