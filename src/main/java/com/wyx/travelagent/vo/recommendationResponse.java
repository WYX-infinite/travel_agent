package com.wyx.travelagent.vo;

import lombok.Data;

import java.util.List;

@Data
public class recommendationResponse {
    private List<recommendationVO> recommendations;
}
