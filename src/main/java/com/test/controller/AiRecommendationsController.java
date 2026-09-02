package com.test.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.test.entity.AiRecommendations;
import com.test.form.CollectForm;
import com.test.service.AiRecommendationsService;
import com.test.service.TravelService;
import com.test.vo.RoutePlan;
import com.test.vo.recommendationResponse;
import com.test.vo.recommendationVO;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author wyx
 * @since 2026-08-28
 */
@RestController
public class AiRecommendationsController {

    @Autowired
    private TravelService travelService;
    @Autowired
    private AiRecommendationsService aiRecommendationsService;

    //private AiRecommendations aiRecommendations;

    @GetMapping("/recommended")
    public List<recommendationVO> recommended(@RequestParam String msg) {
        recommendationResponse recommendation = this.travelService.recommendation(msg);
        return recommendation.getRecommendations();
    }

    @GetMapping("/suggest")
    public SseEmitter suggest(@RequestParam String msg) {
        SseEmitter sseEmitter = new SseEmitter();
        TokenStream tokenStream = this.travelService.stream(msg);
        tokenStream.onPartialResponse((String partialResponse) -> {
                    try {
                        sseEmitter.send(SseEmitter.event().data(partialResponse));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .onCompleteResponse((ChatResponse chatResponse) -> {
                    sseEmitter.complete();
                    //String result = chatResponse.aiMessage().text();
                    //aiRecommendations = this.travelService.transform(result);
                })
                .onError((Throwable throwable) -> sseEmitter.completeWithError(throwable))
                .start();
        return sseEmitter;
    }

//    @GetMapping("/collect")
//    public Boolean suggest(@RequestParam Integer id) {
//        aiRecommendations.setId(null);
//        aiRecommendations.setUpId(id);
//        aiRecommendations.setCreatedAt(LocalDateTime.now());
//        return this.aiRecommendationsService.save(aiRecommendations);
//    }

    @PostMapping("/collect")
    public Boolean collect(@RequestBody CollectForm form){
        if(form == null || form.getUserId() == null || form.getContent() == null || form.getContent().isBlank())
            return false;
        // 转换的是前端当前显示的方案
        AiRecommendations recommendation = this.travelService.transform(form.getContent());
        if(recommendation == null) return false;
        recommendation.setId(null);
        recommendation.setUpId(form.getUserId());
        recommendation.setCreatedAt(LocalDateTime.now());
        return this.aiRecommendationsService.save(recommendation);
    }

    @GetMapping("/list")
    public List<AiRecommendations> list(@RequestParam Integer id) {
        QueryWrapper<AiRecommendations> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("up_id", id);
        queryWrapper.orderByDesc("created_at");
        return this.aiRecommendationsService.list(queryWrapper);
    }

    @GetMapping("/route")
    public RoutePlan route(@RequestParam String msg) {
        long startTime = System.currentTimeMillis();
        try {
            msg += "，出发日期是"+ LocalDate.now().plusDays(1);
            RoutePlan route = this.travelService.route(msg);
            if(route == null) throw new IllegalStateException("AI返回的路线对象为空");
            LocalDateTime localDateTime = LocalDateTime.now();
            route.setCreateTime(localDateTime);
            route.setUpdateTime(localDateTime);
            System.out.println("路线生成耗时：" + (System.currentTimeMillis() - startTime) + "ms");
            return route;
        } catch (IllegalStateException e) {
            System.out.println("路线生成失败,耗时：" + (System.currentTimeMillis() - startTime) + "ms");
            e.printStackTrace();
            throw e;
        }
    }

}
