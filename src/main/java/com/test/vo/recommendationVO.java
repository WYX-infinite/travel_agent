package com.test.vo;

import lombok.Data;

@Data
public class recommendationVO {
    private Integer id;
    private String image;
    private String title;
    private String description;
    private String duration;
    private Float rating;
    private Integer groupSize;
    private Integer price;
}
