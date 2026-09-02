package com.test;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;

import java.util.Collections;

public class Main {
    public static void main(String[] args) {
        String projectPath = System.getProperty("user.dir");

        FastAutoGenerator.create(
                        "jdbc:mysql://localhost:3306/travel_agent?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
                        "root",
                        "123456")
                .globalConfig(builder -> {
                    builder.author("wyx")
                            .disableOpenDir()
                            .outputDir(projectPath + "/src/main/java")
                            .fileOverride();
                })
                .packageConfig(builder -> {
                    builder.parent("com.test")
                            .entity("entity")
                            .mapper("mapper")
                            .service("service")
                            .serviceImpl("service.impl")
                            .controller("controller")
                            .pathInfo(Collections.singletonMap(OutputFile.xml,
                                    projectPath + "/src/main/resources/mapper"));
                })
                .strategyConfig(builder -> {
                    builder.addInclude("user","ai_recommendations")
                            .entityBuilder()
                            .enableLombok()
                            .naming(NamingStrategy.underline_to_camel)
                            .columnNaming(NamingStrategy.underline_to_camel)
                            .mapperBuilder()
                            .enableBaseResultMap()
                            .enableBaseColumnList()
                            .serviceBuilder()
                            .formatServiceFileName("%sService")
                            .formatServiceImplFileName("%sServiceImpl")
                            .controllerBuilder()
                            .enableRestStyle();
                })
                .execute();

        System.out.println("代码生成成功！");
        System.out.println("输出目录：" + projectPath + "/src/main/java");
    }
}
