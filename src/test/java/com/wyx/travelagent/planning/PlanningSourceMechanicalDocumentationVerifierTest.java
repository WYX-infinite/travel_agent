package com.wyx.travelagent.planning;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证机械文档检查器的可靠检出范围与允许的同义边界表达。
 * 测试不宣称覆盖全部业务语义，也不要求检查器分析 helper 调用或所有未声明运行时异常。
 */
class PlanningSourceMechanicalDocumentationVerifierTest {

    @Test
    void ignoresUndocumentedConstantsInNonPublicEnum() {
        String syntheticSource = """
                package synthetic;
                /**
                 * 业务问题：承载内部状态。输入是请求，输出是结果。
                 * 本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
                 * 任务十与任务十二均不使用，后续编排不依赖；本类型不负责业务处理。
                 */
                public final class SyntheticApi {
                    private enum InternalState { HIDDEN }
                }
                """;

        assertThat(verifySynthetic(syntheticSource)).isEmpty();
    }

    @Test
    void rejectsPublicEnumConstantWithoutAttachedChineseJavadoc() {
        String syntheticSource = """
                package synthetic;
                /**
                 * 业务问题：表达状态。输入是枚举名，输出是枚举值。
                 * 本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
                 * 任务十与任务十二均不使用，后续编排不依赖；本类型不负责业务处理。
                 */
                public enum SyntheticApi {
                    /** 已启用。 */
                    ENABLED,
                    /** Disabled. */
                    DISABLED
                }
                """;

        assertThat(verifySynthetic(syntheticSource)).anyMatch(message -> message.contains("DISABLED")
                && message.contains("枚举常量 Javadoc"));
    }

    @Test
    void rejectsPublicMethodDocumentationWithoutExternalAccessAndAuthenticationBoundaries() {
        String syntheticSource = """
                package synthetic;
                /**
                 * 业务问题：提供文本查询。输入是键，输出是值。
                 * 本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
                 * 任务十与任务十二均不使用，后续编排不依赖；本类型不负责业务处理。
                 */
                public final class SyntheticApi {
                    /**
                     * 返回输入值。
                     *
                     * @param value 可为 null 或空串的输入
                     * @return 原输入
                     */
                    public String find(String value) { return value; }
                }
                """;

        assertThat(verifySynthetic(syntheticSource))
                .anyMatch(message -> message.contains("find") && message.contains("外部访问边界"))
                .anyMatch(message -> message.contains("find") && message.contains("认证边界"));
    }

    @Test
    void rejectsReferenceParameterDocumentationWithoutNullEmptyOrExplicitSemantics() {
        String syntheticSource = """
                package synthetic;
                /**
                 * 业务问题：提供文本查询。输入是键，输出是值。
                 * 本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
                 * 任务十与任务十二均不使用，后续编排不依赖；本类型不负责业务处理。
                 */
                public final class SyntheticApi {
                    /**
                     * 返回输入值；只进行内存计算，不接触外部资源，也不负责身份校验。
                     *
                     * @param value 待查询文本
                     * @return 原输入
                     */
                    public String find(String value) { return value; }
                }
                """;

        assertThat(verifySynthetic(syntheticSource)).anyMatch(message -> message.contains("find")
                && message.contains("value") && message.contains("null/空/缺失/显式语义"));
    }

    @Test
    void acceptsEquivalentBoundaryWordingAndReferenceParameterSemantics() {
        String syntheticSource = """
                package synthetic;
                /**
                 * 业务目的：表达查询请求；接收查询键并返回匹配值。
                 * 只进行内存计算，不接触任何外部资源；身份校验由调用层负责。
                 * 第十项任务和第十二项任务均不使用，后续流程不依赖；本类型不承担查询执行。
                 */
                public final class SyntheticApi {
                    /**
                     * 返回输入值；仅做内存转换，无外部访问，认证职责属于调用方。
                     *
                     * @param value 不得为空的查询文本
                     * @return 原输入
                     */
                    public String find(String value) { return value; }
                }
                """;

        assertThat(verifySynthetic(syntheticSource)).isEmpty();
    }

    @Test
    void acceptsCompleteAttachedChineseDocumentationWithoutKeywordFalsePositives() {
        String syntheticSource = """
                package synthetic;
                /**
                 * 业务问题：提供一个最小公开 API。输入是待规范化文本，输出是原文本。
                 * 本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
                 * 任务十和任务十二均不使用本类型，后续编排也不依赖；本类型不负责业务处理。
                 */
                public final class SyntheticApi {
                    /**
                     * 返回调用方输入。
                     *
                     * @param value 可为 null 或显式空串的输入
                     * @return 原输入，可为 null 或空串
                     * 本方法不访问数据库、模型、缓存、文件或网络，不处理认证。
                     */
                    public String normalize(String value) { return value; }
                }
                """;

        assertThat(verifySynthetic(syntheticSource)).isEmpty();
    }

    @Test
    void rejectsPublicRecordComponentTagsThatDoNotMatchTheTypeSignature() {
        String syntheticSource = """
                package synthetic;
                /**
                 * 业务问题：表示一个值。输入是组件，输出是记录。
                 * 本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
                 * 任务十和任务十二均不使用，后续编排不依赖；本类型不负责业务处理。
                 */
                public record SyntheticApi(String value) {
                    /** 创建记录。 */
                    public SyntheticApi { }
                }
                """;

        assertThat(verifySynthetic(syntheticSource)).anyMatch(message -> message.contains("SyntheticApi")
                && message.contains("类型 @param 与签名不一致"));
    }

    @Test
    void rejectsDetachedKeywordsUndocumentedPublicMethodAndUndocumentedNestedType() {
        String syntheticSource = """
                package synthetic;
                /*
                 * 业务问题 输入 输出 不访问数据库、模型、缓存、文件或网络 不处理认证
                 * 任务十 任务十二 后续编排 不负责
                 */
                public class SyntheticApi {
                    public String undocumented(String value) { return value; }

                    public static
                    record ValidationIssue(String value) { }
                }
                """;

        List<String> violations = verifySynthetic(syntheticSource);

        assertThat(violations).anyMatch(message -> message.contains("SyntheticApi")
                && message.contains("缺少类型 Javadoc"));
        assertThat(violations).anyMatch(message -> message.contains("undocumented")
                && message.contains("缺少方法 Javadoc"));
        assertThat(violations).anyMatch(message -> message.contains("ValidationIssue")
                && message.contains("缺少类型 Javadoc"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> verifySynthetic(String source) {
        try {
            Class<?> verifier = Class.forName(
                    "com.wyx.travelagent.planning.PlanningSourceMechanicalDocumentationVerifier");
            Method method = verifier.getDeclaredMethod("verifySource", String.class, String.class);
            method.setAccessible(true);
            return (List<String>) method.invoke(null, "SyntheticApi.java", source);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("机械 AST 文档校验器尚不存在", exception);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("机械 AST 文档校验器接口不可用", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("机械 AST 文档校验器执行失败", exception.getCause());
        }
    }
}
