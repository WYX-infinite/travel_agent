package com.test.vo;

import lombok.Data;

import java.util.List;

@Data
public class recommendationResponse {
    private List<recommendationVO> recommendations;
}
