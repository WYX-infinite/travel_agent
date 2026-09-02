package com.test.service;

import com.test.entity.AiRecommendations;
import com.test.vo.RoutePlan;
import com.test.vo.recommendationResponse;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;

public interface TravelService {
    @SystemMessage(fromResource = "recommended-message.txt")
    public recommendationResponse recommendation(String message);

    @SystemMessage(fromResource = "suggest-message.txt")
    public TokenStream stream(String message);

    @SystemMessage(fromResource = "transform-message.txt")
    public AiRecommendations transform(String message);

    @SystemMessage(fromResource = "route-message.txt")
    public RoutePlan route(String message);

}
