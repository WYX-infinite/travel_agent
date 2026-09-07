package com.wyx.travelagent.knowledge.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/** 任务九内部 JSON 编解码边界，使截断文件和序列化失败能够被确定性测试。 */
interface KnowledgeIndexSnapshotCodec {
    byte[] encode(KnowledgeIndexSnapshotFile snapshot);

    KnowledgeIndexSnapshotFile decode(byte[] bytes);
}

/** 使用 Jackson 严格读写版本化快照，不接受未知字段静默改变磁盘协议。 */
class JacksonKnowledgeIndexSnapshotCodec implements KnowledgeIndexSnapshotCodec {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public byte[] encode(KnowledgeIndexSnapshotFile snapshot) {
        try {
            return objectMapper.writeValueAsBytes(snapshot);
        } catch (JsonProcessingException exception) {
            throw new KnowledgeIndexException(
                    "KNOWLEDGE_INDEX_SERIALIZATION_FAILED",
                    "knowledge index serialization failed",
                    exception);
        }
    }

    @Override
    public KnowledgeIndexSnapshotFile decode(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, KnowledgeIndexSnapshotFile.class);
        } catch (Exception exception) {
            throw new KnowledgeIndexException(
                    "KNOWLEDGE_INDEX_LOAD_FAILED",
                    "knowledge index could not be loaded",
                    exception);
        }
    }
}

/** 持久化文件顶层结构：版本、模型、维度和稳定排序的完整条目。 */
record KnowledgeIndexSnapshotFile(
        int formatVersion,
        String embeddingModel,
        int vectorDimension,
        List<KnowledgeIndexEntryFile> entries
) {
}

/** 单条持久化向量记录，同时保存数据库关联标识和 TextSegment 正文。 */
record KnowledgeIndexEntryFile(
        String embeddingId,
        Long documentId,
        Long chunkId,
        int chunkIndex,
        String content,
        float[] vector
) {
}
