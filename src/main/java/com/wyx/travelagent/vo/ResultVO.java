package com.wyx.travelagent.vo;

import com.wyx.travelagent.entity.User;
import lombok.Data;

@Data
public class ResultVO<T> {
    private Integer code;
    private String msg;
    private T data;
}
