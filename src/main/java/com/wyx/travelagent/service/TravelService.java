package com.wyx.travelagent.service;

import com.wyx.travelagent.entity.AiRecommendations;
import com.wyx.travelagent.vo.RoutePlan;
import com.wyx.travelagent.vo.recommendationResponse;
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
