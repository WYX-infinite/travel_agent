# Knowledge Embedding Batching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bound every knowledge-index embedding request by a configurable batch size so DashScope never receives more than its supported number of text segments per call.

**Architecture:** Extend the existing `travel.knowledge` configuration record with a positive `embeddingBatchSize` that defaults to 10. Keep deterministic chunk sorting in `LangChain4jKnowledgeIndex`, partition only the ordered `TextSegment` list, validate every batch response, concatenate embeddings in order, and publish the snapshot only after all batches succeed.

**Tech Stack:** Java 21, Spring Boot 3.5 configuration properties, LangChain4j `EmbeddingModel`, JUnit 5, AssertJ, Maven Wrapper

---

## File Map

- Modify `src/main/java/com/wyx/travelagent/knowledge/index/KnowledgeIndexProperties.java`: bind and validate the embedding batch size while preserving the existing one-argument constructor.
- Create `src/test/java/com/wyx/travelagent/knowledge/index/KnowledgeIndexPropertiesTest.java`: verify the default, Spring binding, and fail-fast validation.
- Modify `src/test/java/com/wyx/travelagent/knowledge/index/DeterministicFakeEmbeddingModel.java`: add test-only batch-limit enforcement and call-size observation.
- Modify `src/test/java/com/wyx/travelagent/knowledge/index/LangChain4jKnowledgeIndexTest.java`: reproduce the provider limit with 21 chunks and verify `10, 10, 1` batching plus complete indexing.
- Modify `src/main/java/com/wyx/travelagent/knowledge/index/LangChain4jKnowledgeIndex.java`: perform bounded ordered embedding calls and per-batch response validation.

### Task 1: Add the validated batch-size configuration

**Files:**
- Create: `src/test/java/com/wyx/travelagent/knowledge/index/KnowledgeIndexPropertiesTest.java`
- Modify: `src/main/java/com/wyx/travelagent/knowledge/index/KnowledgeIndexProperties.java`

- [ ] **Step 1: Write failing configuration tests**

Create `KnowledgeIndexPropertiesTest.java`:

```java
package com.wyx.travelagent.knowledge.index;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeIndexPropertiesTest {

    @Test
    void defaultsEmbeddingBatchSizeToTenForExistingCallers() {
        KnowledgeIndexProperties properties = new KnowledgeIndexProperties(
                Path.of("data/index/test-index.json"));

        assertThat(properties.embeddingBatchSize()).isEqualTo(10);
    }

    @Test
    void bindsConfiguredEmbeddingBatchSize() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "travel.knowledge.index-file", "data/index/test-index.json",
                "travel.knowledge.embedding-batch-size", "7"));

        KnowledgeIndexProperties properties = new Binder(source)
                .bind("travel.knowledge", Bindable.of(KnowledgeIndexProperties.class))
                .orElseThrow();

        assertThat(properties.embeddingBatchSize()).isEqualTo(7);
    }

    @Test
    void rejectsNonPositiveEmbeddingBatchSize() {
        assertThatThrownBy(() -> new KnowledgeIndexProperties(
                Path.of("data/index/test-index.json"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embedding-batch-size");
    }
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='D:\program\jdk-21-temurin'
$env:Path='D:\program\jdk-21-temurin\bin;' + $env:Path
.\mvnw.cmd '-Dtest=KnowledgeIndexPropertiesTest' test
```

Expected: compilation fails because `embeddingBatchSize()` and the two-argument constructor do not exist.

- [ ] **Step 3: Implement the minimal configuration contract**

Replace the record declaration and constructor logic in `KnowledgeIndexProperties.java` with:

```java
@ConfigurationProperties("travel.knowledge")
public record KnowledgeIndexProperties(
        Path indexFile,
        @org.springframework.boot.context.properties.bind.DefaultValue("10")
        int embeddingBatchSize) {
    private static final Path DEFAULT_INDEX_FILE =
            Path.of("data", "index", "knowledge-index.json");
    private static final int DEFAULT_EMBEDDING_BATCH_SIZE = 10;

    public KnowledgeIndexProperties(Path indexFile) {
        this(indexFile, DEFAULT_EMBEDDING_BATCH_SIZE);
    }

    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public KnowledgeIndexProperties {
        if (indexFile == null) {
            indexFile = DEFAULT_INDEX_FILE;
        }
        indexFile = indexFile.normalize();
        if (embeddingBatchSize < 1) {
            throw new IllegalArgumentException(
                    "travel.knowledge.embedding-batch-size must be greater than zero");
        }
    }

    public Path temporaryFile() {
        Path parent = indexFile.getParent();
        Path filename = Path.of("knowledge-index.json.tmp");
        return parent == null ? filename : parent.resolve(filename);
    }
}
```

Spring Boot relaxed external binding maps `TRAVEL_KNOWLEDGE_EMBEDDING_BATCH_SIZE` to this record component without modifying the already user-edited `application.yml`. The `@DefaultValue` annotation supplies 10 when neither YAML nor an environment variable overrides it.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the command from Step 2.

Expected: `KnowledgeIndexPropertiesTest` passes with zero failures.

- [ ] **Step 5: Commit only the configuration contract**

```powershell
git add -- 'src/main/java/com/wyx/travelagent/knowledge/index/KnowledgeIndexProperties.java' 'src/test/java/com/wyx/travelagent/knowledge/index/KnowledgeIndexPropertiesTest.java'
git commit -m "feat: configure knowledge embedding batch size"
```

### Task 2: Reproduce and implement bounded ordered embedding batches

**Files:**
- Modify: `src/test/java/com/wyx/travelagent/knowledge/index/DeterministicFakeEmbeddingModel.java`
- Modify: `src/test/java/com/wyx/travelagent/knowledge/index/LangChain4jKnowledgeIndexTest.java`
- Modify: `src/main/java/com/wyx/travelagent/knowledge/index/LangChain4jKnowledgeIndex.java:288-313`

- [ ] **Step 1: Extend the fake model with batch observation**

Add fields to `DeterministicFakeEmbeddingModel`:

```java
private final List<Integer> embeddedBatchSizes = new CopyOnWriteArrayList<>();
private int maximumBatchSize = Integer.MAX_VALUE;
```

Add methods:

```java
public void rejectBatchesLargerThan(int maximumBatchSize) {
    if (maximumBatchSize < 1) {
        throw new IllegalArgumentException("maximumBatchSize must be positive");
    }
    this.maximumBatchSize = maximumBatchSize;
}

public List<Integer> embeddedBatchSizes() {
    return List.copyOf(embeddedBatchSizes);
}
```

At the start of `embedAll(...)`, record and enforce the size before generating vectors:

```java
embeddedBatchSizes.add(segments.size());
if (segments.size() > maximumBatchSize) {
    throw new IllegalArgumentException("simulated embedding batch limit exceeded");
}
```

- [ ] **Step 2: Write the failing 21-chunk rebuild test**

Add to `LangChain4jKnowledgeIndexTest`:

```java
@Test
void rebuildSplitsEmbeddingRequestsAtConfiguredBatchBoundary() {
    DeterministicFakeEmbeddingModel model = new DeterministicFakeEmbeddingModel(MODEL, 4);
    model.rejectBatchesLargerThan(10);
    LangChain4jKnowledgeIndex index = new LangChain4jKnowledgeIndex(
            model, MODEL, new KnowledgeIndexProperties(formalFile(), 10));
    List<KnowledgeChunk> chunks = java.util.stream.LongStream.rangeClosed(1, 21)
            .mapToObj(id -> chunk(id, 1_000 + id, 0, "批量切片-" + id))
            .toList();

    index.rebuild(chunks);

    assertThat(model.embeddedBatchSizes()).containsExactly(10, 10, 1);
    assertThat(index.search(new KnowledgeQuery("批量切片"), 21)).hasSize(21);
}
```

- [ ] **Step 3: Run the single test and verify RED**

Run:

```powershell
$env:JAVA_HOME='D:\program\jdk-21-temurin'
$env:Path='D:\program\jdk-21-temurin\bin;' + $env:Path
.\mvnw.cmd '-Dtest=LangChain4jKnowledgeIndexTest#rebuildSplitsEmbeddingRequestsAtConfiguredBatchBoundary' test
```

Expected: `KnowledgeIndexException` with code `KNOWLEDGE_EMBEDDING_FAILED`, caused by `simulated embedding batch limit exceeded`, because the production implementation sends all 21 segments at once.

- [ ] **Step 4: Implement ordered batching in `embedChunks`**

Replace the one-shot embedding block in `LangChain4jKnowledgeIndex.embedChunks(...)` with:

```java
List<Embedding> embeddings = new ArrayList<>(segments.size());
int batchSize = properties.embeddingBatchSize();
for (int start = 0; start < segments.size(); start += batchSize) {
    int end = Math.min(start + batchSize, segments.size());
    List<TextSegment> batchSegments = segments.subList(start, end);
    final List<Embedding> batchEmbeddings;
    try {
        batchEmbeddings = embeddingModel.embedAll(batchSegments).content();
    } catch (RuntimeException exception) {
        throw new KnowledgeIndexException(
                "KNOWLEDGE_EMBEDDING_FAILED",
                "knowledge embeddings could not be generated", exception);
    }
    if (batchEmbeddings == null || batchEmbeddings.size() != batchSegments.size()) {
        throw new KnowledgeIndexException(
                "KNOWLEDGE_EMBEDDING_FAILED",
                "knowledge embedding response size is invalid");
    }
    embeddings.addAll(batchEmbeddings);
}
```

Retain the existing combined-size and vector-dimension validation after this block as defense in depth.

- [ ] **Step 5: Run the single test and verify GREEN**

Run the command from Step 3.

Expected: one test passes; observed rebuild request sizes are exactly `10`, `10`, and `1`. The later query embedding occurs only after the batch-size assertion.

- [ ] **Step 6: Run the complete index test class**

Run:

```powershell
.\mvnw.cmd '-Dtest=LangChain4jKnowledgeIndexTest' test
```

Expected: all `LangChain4jKnowledgeIndexTest` tests pass, including deterministic-file, concurrency, recovery, and model-compatibility coverage.

- [ ] **Step 7: Commit only batching implementation files**

```powershell
git add -- 'src/main/java/com/wyx/travelagent/knowledge/index/LangChain4jKnowledgeIndex.java' 'src/test/java/com/wyx/travelagent/knowledge/index/DeterministicFakeEmbeddingModel.java' 'src/test/java/com/wyx/travelagent/knowledge/index/LangChain4jKnowledgeIndexTest.java'
git commit -m "fix: batch knowledge embedding requests"
```

### Task 3: Verify ingestion, recovery, and workspace boundaries

**Files:**
- Verify only; no planned production edits

- [ ] **Step 1: Run relevant backend regression tests**

```powershell
$env:JAVA_HOME='D:\program\jdk-21-temurin'
$env:Path='D:\program\jdk-21-temurin\bin;' + $env:Path
$env:TRAVEL_JWT_SECRET='MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY='
.\mvnw.cmd '-Dtest=KnowledgeIndexPropertiesTest,LangChain4jKnowledgeIndexTest,KnowledgeIndexRecoveryTest,KnowledgeIngestionServiceTest,KnowledgeAdministrationServiceTest' test
```

Expected: all selected tests pass with zero failures and zero errors.

- [ ] **Step 2: Compile the application**

```powershell
.\mvnw.cmd '-DskipTests' compile
```

Expected: Maven reports `BUILD SUCCESS`.

- [ ] **Step 3: Inspect the final diff and status**

```powershell
git diff --stat HEAD~2..HEAD
git status --short
```

Expected: the two implementation commits contain only the files listed in Tasks 1 and 2. Pre-existing unrelated workspace changes remain uncommitted and untouched.

- [ ] **Step 4: Restart and verify the original runtime failure**

Restart the Spring Boot application so it loads the new configuration. With more than ten active knowledge chunks, startup recovery must complete without `batch size is invalid`, and the administrator knowledge page must list documents normally.

