package com.wyx.travelagent.knowledge.ingest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KnowledgeIndexRecoveryTest {

    @Test
    void startupRecoveryRunsDuringSingletonInitializationBeforeServerReady() {
        KnowledgeIngestionService ingestionService = mock(KnowledgeIngestionService.class);
        KnowledgeIndexRecovery recovery = new KnowledgeIndexRecovery(ingestionService);

        assertThat(recovery).isInstanceOf(SmartInitializingSingleton.class);
        ((SmartInitializingSingleton) recovery).afterSingletonsInstantiated();

        verify(ingestionService).rebuildFromActiveDocuments();
    }
}
