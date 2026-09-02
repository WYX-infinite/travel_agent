package com.test.form;

import lombok.Data;

@Data
public class CollectForm {
    private Integer userId;

    //当前页面显示的AI推荐内容
    private String content;
}
