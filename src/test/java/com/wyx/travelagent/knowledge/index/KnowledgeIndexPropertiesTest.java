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
                .orElseThrow(() -> new AssertionError("travel.knowledge properties were not bound"));

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
