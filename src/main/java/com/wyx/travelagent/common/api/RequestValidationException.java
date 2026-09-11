package com.wyx.travelagent.common.api;

import java.util.Map;

/**
 * 表示已经脱敏并按字段归类的请求校验失败。
 * 输入是稳定字段到固定原因的映射，输出由统一异常处理器转换；本类型不访问数据库、模型、缓存、文件或网络，也不处理认证。
 */
public final class RequestValidationException extends RuntimeException {
    private final Map<String, String> fields;

    /**
     * @param fields 非空字段错误映射，不得包含用户原值
     * 构造只复制内存数据，不访问外部资源，也不处理认证。
     */
    public RequestValidationException(Map<String, String> fields) {
        super("request validation failed");
        this.fields = Map.copyOf(fields);
    }

    /** @return 非空且不可修改的字段错误映射；本方法不访问外部资源，也不处理认证。 */
    public Map<String, String> fields() { return fields; }
}
