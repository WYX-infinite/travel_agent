package com.wyx.travelagent.configuration;

import com.wyx.travelagent.service.TravelService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TravelServiceFactory {
    @Autowired
    private ChatModel chatModel;

    @Autowired
    private StreamingChatModel streamingChatModel;

    @Bean
    public TravelService getTravelService(){
        TravelService travelService = AiServices.builder(TravelService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
        return travelService;
    }
}
