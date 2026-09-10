package com.wyx.travelagent.planning.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector;

/**
 * 业务问题：冻结并稳定排序全部行程问题，同时提供 ERROR/WARNING 分视图和继续判定。
 * 输入为任意迭代顺序的问题列表，输出为不可变确定顺序报告；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十提供事实但不产生报告，任务十二以 Java 规则构造报告，后续编排只信任本报告；本类型不负责调用模型、自动修正或状态持久化。
 *
 * @param issues 不得为 {@code null} 或含 null 的问题列表，构造后按稳定规则排序
 */
public record ValidationReport(List<ValidationIssue> issues) {
    /**
     * 创建不可变、稳定排序的校验报告。
     * 输入列表不得为空引用或含 null，显式空列表表示没有问题且允许继续。
     * 构造只复制和排序内存数据，不访问模型、数据库、缓存、文件或网络，不处理认证。
     *
     * @throws NullPointerException 当列表或元素为空
     */
    public ValidationReport {
        Objects.requireNonNull(issues, "issues");
        issues.forEach(issue -> Objects.requireNonNull(issue, "issue"));
        // 稳定顺序让测试、持久化和任务十三修正输入不受规则注册或集合迭代顺序影响。
        issues = issues.stream().sorted(ValidationIssue.DETERMINISTIC_ORDER).toList();
    }

    /**
     * 返回全部硬错误。
     *
     * @return 按报告顺序排列的不可变 ERROR 列表，可能为空但不为 null
     * 无输入且不抛业务异常；本方法只过滤内存列表，不访问外部资源，不处理认证。
     */
    public List<ValidationIssue> errors() {
        return issues.stream().filter(issue -> issue.severity() == IssueSeverity.ERROR).toList();
    }

    /**
     * 返回全部风险警告。
     *
     * @return 按报告顺序排列的不可变 WARNING 列表，可能为空但不为 null
     * 无输入且不抛业务异常；本方法只过滤内存列表，不访问外部资源，不处理认证。
     */
    public List<ValidationIssue> warnings() {
        return issues.stream().filter(issue -> issue.severity() == IssueSeverity.WARNING).toList();
    }

    /**
     * 判断草稿能否进入后续阶段。
     *
     * @return 不含 ERROR 时为 {@code true}；WARNING 不阻止继续
     * 无输入且不抛业务异常；本方法不相信模型自报结果，只读取 Java 规则报告，不访问外部资源或认证信息。
     */
    public boolean canProceed() {
        return issues.stream().noneMatch(issue -> issue.severity() == IssueSeverity.ERROR);
    }

    /**
     * 提供规则流聚合器，统一通过本类型完成防御复制与稳定排序。
     *
     * @return 接收 ValidationIssue 并生成 ValidationReport 的非并发收集器
     * 无输入且不抛业务异常；收集仅发生在内存中，不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    public static Collector<ValidationIssue, ?, ValidationReport> collector() {
        return Collector.of(ArrayList::new, List::add, (left, right) -> {
            left.addAll(right);
            return left;
        }, ValidationReport::new);
    }
}
