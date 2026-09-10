package com.wyx.travelagent.planning.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 业务问题：保存可补全字段的三态，避免把“未提供”和用户“显式清空”混为一谈。
 * 输入为状态及可选值，输出为不可变值对象；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
 * 任务十负责工具边界，任务十二负责行程校验，后续编排消费合并结果；本类型不负责偏好读取、合并、认证、生成或校验行程。
 *
 * @param <T> 字段值类型；显式值仅支持不可变字符串或已防御复制的字符串列表
 */
public final class PreferenceValue<T> {
    /**
     * 业务问题：区分可补全字段的三种外部输入意图，防止显式清空被覆盖。
     * 输入由工厂方法确定，输出是稳定状态枚举；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
     * 任务十和任务十二不消费原始三态，后续编排只消费合并结果；本类型不负责认证、偏好读取、合并或行程校验。
     */
    public enum State {
        /** 调用方没有提供字段，后续可以用已保存偏好补全。 */
        NOT_PROVIDED,
        /** 调用方明确清空字段，后续不得用已保存偏好覆盖。 */
        EXPLICIT_EMPTY,
        /** 调用方提供了具体值，后续不得用已保存偏好覆盖。 */
        EXPLICIT_VALUE
    }

    private final State state;
    private final T value;

    private PreferenceValue(State state, T value) {
        this.state = Objects.requireNonNull(state, "state");
        this.value = value;
    }

    /**
     * 创建未提供状态。
     *
     * @param <T> 目标字段类型
     * @return 不带值、允许后续补全的不可变状态
     * 不接受输入值，不抛业务异常；不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public static <T> PreferenceValue<T> notProvided() {
        return new PreferenceValue<>(State.NOT_PROVIDED, null);
    }

    /**
     * 创建显式清空状态。
     *
     * @param <T> 目标字段类型
     * @return 不带值且禁止后续覆盖的不可变状态
     * 不接受输入值，不抛业务异常；不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public static <T> PreferenceValue<T> explicitEmpty() {
        return new PreferenceValue<>(State.EXPLICIT_EMPTY, null);
    }

    /**
     * 创建字符串显式值状态。
     *
     * @param value 用户明确提供的字符串，不得为 {@code null}；空串或空白仍保留为显式值并交给校验器判断
     * @return 带字符串且禁止后续覆盖的不可变状态
     * @throws NullPointerException 当值为 {@code null}
     * 字符串本身不可变；本方法不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public static PreferenceValue<String> explicitValue(String value) {
        return new PreferenceValue<>(State.EXPLICIT_VALUE, Objects.requireNonNull(value, "value"));
    }

    /**
     * 创建字符串列表显式值状态。
     *
     * @param value 用户明确提供的列表，不得为 {@code null}；可为空且可暂含 {@code null} 元素供校验器统一拒绝
     * @return 带防御复制、不可修改字符串列表且禁止后续覆盖的不可变状态
     * @throws NullPointerException 当列表为 {@code null}
     * 输入列表后续修改不会影响结果；本方法不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public static PreferenceValue<List<String>> explicitValue(List<String> value) {
        Objects.requireNonNull(value, "value");
        List<String> immutableValue = Collections.unmodifiableList(new ArrayList<>(value));
        return new PreferenceValue<>(State.EXPLICIT_VALUE, immutableValue);
    }

    /**
     * 返回输入状态。
     *
     * @return 非空三态值
     * 无参数且不抛业务异常；不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public State state() {
        return state;
    }

    /**
     * 返回显式值。
     *
     * @return 仅在 {@link State#EXPLICIT_VALUE} 时非空，其余状态返回 {@code null}
     * 无参数且不抛业务异常；显式空和未提供都返回 null，但可由 {@link #state()} 区分；不访问外部资源或认证信息。
     */
    public T value() {
        return value;
    }

    /**
     * 比较状态和值是否相等。
     *
     * @param other 可为 {@code null} 的比较对象
     * @return 状态及显式值均相等时为 {@code true}
     * 不抛业务异常，不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PreferenceValue<?> that
                && state == that.state && Objects.equals(value, that.value);
    }

    /**
     * 计算与相等语义一致的哈希值。
     *
     * @return 状态及值的稳定哈希值
     * 无参数，不抛业务异常，不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    @Override
    public int hashCode() {
        return Objects.hash(state, value);
    }

    /**
     * 返回不包含秘密或外部原值的状态描述。
     *
     * @return 仅包含三态名称的安全文本
     * 无参数，不返回显式用户值，不抛业务异常；不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    @Override
    public String toString() {
        return "PreferenceValue[" + state + "]";
    }
}
