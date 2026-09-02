package com.test.vo;

import com.test.entity.User;
import lombok.Data;

@Data
public class ResultVO<T> {
    private Integer code;
    private String msg;
    private T data;
}
