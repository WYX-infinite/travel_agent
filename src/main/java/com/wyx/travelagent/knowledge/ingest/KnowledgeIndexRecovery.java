package com.wyx.travelagent.knowledge.ingest;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 应用启动时用数据库 ACTIVE 文档全集重建正式索引。
 * <p>
 * 数据库状态是恢复事实源；UPLOADED、INDEXING 和 FAILED 文档不会被重新发布为可检索数据。
 * 该组件在所有单例 Bean 创建完成后、Web 服务进入可用阶段前执行。重建失败会让应用启动失败关闭，
 * 避免进程崩溃遗留的候选正式文件在 ACTIVE 数据校验前被正常搜索。
 */
@Component
@ConditionalOnProperty(
        prefix = "travel.knowledge",
        name = "rebuild-on-startup",
        havingValue = "true",
        matchIfMissing = true)
public final class KnowledgeIndexRecovery implements SmartInitializingSingleton {
    private final KnowledgeIngestionService ingestionService;

    /**
     * 创建启动恢复器。
     *
     * @param ingestionService 提供基于 ACTIVE 数据重建的生命周期服务
     */
    public KnowledgeIndexRecovery(KnowledgeIngestionService ingestionService) {
        this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService");
    }

    /**
     * 在 Spring 所有普通单例完成装配后立即执行一次恢复。
     * <p>
     * 该初始化回调早于应用 ready 阶段；它会查询数据库并调用远程 Embedding 与磁盘原子替换，
     * 任一步骤失败都会阻止应用完成启动，而不是带着未经 ACTIVE 集合校验的正式文件继续服务。
     */
    @Override
    public void afterSingletonsInstantiated() {
        ingestionService.rebuildFromActiveDocuments();
    }
}
