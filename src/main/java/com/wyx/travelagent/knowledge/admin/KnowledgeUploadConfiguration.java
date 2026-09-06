package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.knowledge.ingest.DocumentParser;
import com.wyx.travelagent.knowledge.ingest.TikaDocumentParser;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KnowledgeUploadProperties.class)
class KnowledgeUploadConfiguration {

    @Bean
    DocumentParser knowledgeDocumentParser(KnowledgeUploadProperties properties) {
        return new TikaDocumentParser(properties.maxExtractedCharacters());
    }
}
