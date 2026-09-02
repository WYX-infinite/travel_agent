package com.test.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RoutePlan {
    private String planId;
    private String departurePlace;
    private String destination;
    private String userPriority;
    private String remark;
    private List<RouteScheme> schemes;
    private String suggestion;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
