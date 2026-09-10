package com.wyx.travelagent.planning.context;

import com.wyx.travelagent.planning.generation.ItineraryGenerator;
import com.wyx.travelagent.planning.generation.LangChain4jItineraryGenerator;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 业务问题：在应用启动时注册任务十二的上下文预算、估算器和确定性组装器。
 * 输入为 travel.ai.context 配置，输出为可注入的本地组件；本类型不访问模型、数据库、缓存、文件或网络，不处理认证。
 * 任务十/十一提供内部事实，任务十二完成预算装配，后续调用复用这些 Bean；本类型不负责调用模型、执行检索或自动修正行程。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ContextBudgetProperties.class)
public class PlanningContextConfiguration {

    /**
     * 创建无状态保守 Token 估算器。
     *
     * @return 非空、线程安全且结果确定的估算器
     * 无输入且不抛业务异常；本方法不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    @Bean
    public ConservativeTokenEstimator conservativeTokenEstimator() {
        return new ConservativeTokenEstimator();
    }

    /**
     * 创建预算器与生成器共享的最终 Prompt 渲染器。
     *
     * @return 非空且只含固定类路径模板的渲染器
     * @throws IllegalStateException 当任务十二类路径模板缺失或非法
     * 本方法构造会读取类路径模板，但不访问模型、数据库、缓存或网络，不处理认证。
     */
    @Bean
    public ItineraryPromptRenderer itineraryPromptRenderer() {
        return new ItineraryPromptRenderer();
    }

    /**
     * 创建上下文组装器并绑定已经校验的预算配置。
     *
     * @param properties 非空且已通过启动期校验的预算配置
     * @param estimator 非空本地估算器
     * @param promptRenderer 非空且与生成器共享的最终 Prompt 渲染器
     * @return 非空确定性上下文组装器
     * @throws NullPointerException 当 Spring 装配传入空依赖
     * 本方法只创建内存对象，不访问模型、数据库、缓存、文件或网络，不处理认证。
     */
    @Bean
    public PromptContextAssembler promptContextAssembler(
            ContextBudgetProperties properties,
            ConservativeTokenEstimator estimator,
            ItineraryPromptRenderer promptRenderer) {
        return new PromptContextAssembler(properties, estimator, promptRenderer);
    }

    /**
     * 创建只消费 PromptContext 的 LangChain4j 行程生成适配器。
     *
     * @param chatModel 非空同步聊天模型
     * @param promptRenderer 非空且与预算器共享的最终 Prompt 渲染器
     * @return 非空结构化行程生成端口实现
     * @throws NullPointerException 当 Spring 装配传入空模型
     * @throws IllegalStateException 当任务十二类路径模板缺失或非法
     * 本方法构造会读取类路径模板但不调用模型、数据库、缓存或网络，不处理认证。
     */
    @Bean
    public ItineraryGenerator itineraryGenerator(
            ChatModel chatModel,
            ItineraryPromptRenderer promptRenderer) {
        return new LangChain4jItineraryGenerator(chatModel, promptRenderer);
    }
}
