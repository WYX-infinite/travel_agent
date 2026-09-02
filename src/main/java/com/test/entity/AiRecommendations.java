package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * AI生成的旅行推荐结果表
 * </p>
 *
 * @author wyx
 * @since 2026-08-28
 */
@Getter
@Setter
@TableName("ai_recommendations")
public class AiRecommendations implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 推荐记录唯一ID (自增主键)
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    private Integer upId;

    /**
     * 推荐的目的地名称
     */
    private String destination;

    /**
     * 推荐理由
     */
    private String recommendationReason;

    /**
     * 预估费用区间
     */
    private String estimatedCost;

    /**
     * 推荐的住宿方式及理由
     */
    private String recommendedAccommodation;

    /**
     * 推荐的交通方式及理由
     */
    private String recommendedTransportation;

    /**
     * 特别提示
     */
    private String specialTips;

    /**
     * 记录创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最佳出行季节
     */
    private String bestSeason;
}
