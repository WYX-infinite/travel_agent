# Knowledge Embedding Batching Design

## Goal

Prevent knowledge-index ingestion and rebuilds from exceeding the embedding provider's per-request input limit while preserving deterministic chunk-to-vector ordering and atomic index publication.

## Scope

The change covers every knowledge-index write path that currently passes all text segments to `EmbeddingModel.embedAll(...)` in one call:

1. Startup recovery rebuilds from all active database chunks.
2. Administrator-triggered full index rebuilds.
3. Incremental document ingestion.

Search query embedding, chunking rules, persisted index format, authentication, and provider selection will not change.

## Configuration Design

`KnowledgeIndexProperties` will expose `embeddingBatchSize` under the existing `travel.knowledge` prefix. The external property name will be:

```yaml
travel:
  knowledge:
    embedding-batch-size: ${TRAVEL_KNOWLEDGE_EMBEDDING_BATCH_SIZE:10}
```

The default is `10`, matching the current DashScope-compatible embedding endpoint limit. Values less than one will fail fast during configuration construction. A one-argument constructor will preserve existing tests and callers that only provide an index path.

## Embedding Flow

`LangChain4jKnowledgeIndex.embedChunks(...)` will retain its existing validation and deterministic sort order. It will then partition the ordered segments into contiguous batches no larger than `embeddingBatchSize`, call `EmbeddingModel.embedAll(...)` once per batch, and append each batch response in request order.

For 21 ordered segments with the default configuration, the remote call sizes will be `10`, `10`, and `1`. The combined embedding at position `n` will continue to belong to the stable chunk at position `n`.

All remote calls complete before the method builds or publishes a candidate snapshot. Consequently, a failure in any batch cannot expose a partial in-memory or on-disk index.

## Error Handling

- A provider exception from any batch remains wrapped as `KNOWLEDGE_EMBEDDING_FAILED`, preserving the existing public error contract.
- Each batch response must contain exactly as many embeddings as its request. A null or mismatched response fails before any snapshot publication.
- Existing cross-batch vector-dimension validation remains authoritative after responses are combined.
- An invalid configured batch size fails at startup instead of allowing an infinite loop or empty batch.

## Alternatives Considered

### Configure only the LangChain4j client

This is smaller, but depends on Starter property binding and provider-specific client behavior that the knowledge index cannot verify. It would also leave the index's remote-call bound implicit.

### Retry and split after a provider rejection

This can adapt to unknown limits, but it deliberately sends a failing request, adds retry complexity, and may mask unrelated HTTP 400 errors. A deterministic configured limit is simpler and testable.

## Verification

1. Add a fake embedding model that rejects any request larger than ten segments and records observed batch sizes.
2. Rebuild an index from 21 chunks and first verify the existing one-shot implementation fails.
3. Implement batching and verify the observed sizes are exactly `10`, `10`, and `1`.
4. Verify all 21 chunks are present in the rebuilt index and that deterministic persisted bytes remain stable.
5. Verify non-positive batch sizes are rejected.
6. Run the complete knowledge-index test class and the relevant ingestion/recovery tests.

