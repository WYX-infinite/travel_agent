package com.wyx.travelagent.vo;

import lombok.Data;

@Data
public class RouteScheme {
    private String schemeId;
    private String trafficCombination;
    private String totalTime;
    private String transferWaitTime;
    private String totalCost;
    private String additionalCost;
    private String comfortLevel;
    private Integer transferTimes;
    private String departureTime;
    private String arrivalTime;
}
