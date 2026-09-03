package com.wyx.travelagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.wyx.travelagent.mapper")
public class TravelAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(TravelAgentApplication.class, args);
    }
}
