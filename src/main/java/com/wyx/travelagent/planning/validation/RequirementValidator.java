package com.wyx.travelagent.planning.validation;

import com.wyx.travelagent.planning.model.CompanionType;
import com.wyx.travelagent.planning.model.PreferenceValue;
import com.wyx.travelagent.planning.model.TravelInterest;
import com.wyx.travelagent.planning.model.TravelPace;
import com.wyx.travelagent.planning.model.TravelRequirement;
import com.wyx.travelagent.planning.model.TravelRequirementInput;
import com.wyx.travelagent.tools.model.TravelMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 业务问题：在偏好合并完成后一次性校验旅行需求，避免未校验或未知枚举进入规划。
 * 输入为无用户标识的 {@link TravelRequirementInput}，输出为 {@link TravelRequirement} 或稳定业务异常。
 * 本类型不访问数据库、模型、缓存、文件或网络，不处理认证；任务十负责工具执行，任务十二负责行程校验，后续编排消费强类型需求；本类型不负责偏好存储、工具调用、生成或修正行程。
 */
public final class RequirementValidator {
    /** 所有旅行需求失败共享的稳定错误码。 */
    public static final String ERROR_CODE = "INVALID_TRAVEL_REQUIREMENT";
    /** 住宿地址允许的最大 UTF-16 字符数。 */
    public static final int MAX_ACCOMMODATION_ADDRESS_LENGTH = 255;
    /** 特殊要求允许的最大 UTF-16 字符数。 */
    public static final int MAX_SPECIAL_REQUIREMENTS_LENGTH = 1000;

    /**
     * 创建无状态校验器。
     * 无输入参数，输出可复用的无状态实例；无 null 或显式空语义，不抛业务异常。
     * 构造不访问数据库、模型、缓存、文件或网络，不处理认证。
     */
    public RequirementValidator() {
    }

    /**
     * 校验完成偏好合并后的输入并创建强类型需求。
     * 未提供与显式空的可选偏好都可形成缺失值，但三态在输入阶段仍保留，供合并器决定是否覆盖；特殊要求从不补全。
     *
     * @param input 待校验输入，可为 {@code null}，此时返回稳定业务错误而非 NPE
     * @return 已去除文本首尾空白、集合不可变且枚举已安全解析的需求
     * @throws ValidationException 当任一硬约束缺失、边界越界、可选显式值非法或外部枚举未知
     * 校验仅使用输入内存数据，不访问数据库、模型、缓存、文件或网络，不处理认证；异常列表不包含用户原值。
     */
    public TravelRequirement validate(TravelRequirementInput input) {
        if (input == null) {
            throw new ValidationException(List.of(new RequirementValidationError("request", "请求不能为空")));
        }

        List<RequirementValidationError> issues = new ArrayList<>();
        String city = normalizeRequiredText(input.destinationCity(), "destinationCity", issues);
        LocalDate start = input.startDate();
        LocalDate end = input.endDate();
        if (start == null) {
            issues.add(new RequirementValidationError("startDate", "开始日期不能为空"));
        }
        if (end == null) {
            issues.add(new RequirementValidationError("endDate", "结束日期不能为空"));
        }
        if (start != null && end != null) {
            long duration = ChronoUnit.DAYS.between(start, end) + 1;
            if (duration < 1 || duration > 5) {
                issues.add(new RequirementValidationError("dateRange", "行程必须为一至五个自然日"));
            }
        }

        Integer travelers = input.travelers();
        if (travelers == null || travelers <= 0) {
            issues.add(new RequirementValidationError("travelers", "出行人数必须为正整数"));
        }
        BigDecimal budget = input.totalBudget();
        if (budget == null || budget.signum() <= 0) {
            issues.add(new RequirementValidationError("totalBudget", "总预算必须大于零"));
        }

        CompanionType companion = parseRequiredEnum(
                input.companionType(), CompanionType.class, "companionType", "同行类型不受支持", issues);
        Set<TravelInterest> interests = parseInterests(input.interests(), issues);
        TravelPace pace = parseOptionalEnum(
                input.pace(), TravelPace.class, "pace", "行程节奏不受支持", issues);
        String accommodation = normalizeOptionalText(
                input.accommodationAddress(), "accommodationAddress", MAX_ACCOMMODATION_ADDRESS_LENGTH, issues);
        TravelMode mode = parseOptionalEnum(
                input.travelMode(), TravelMode.class, "travelMode", "交通方式不受支持", issues);
        String special = normalizeSpecialRequirements(input.specialRequirements(), issues);

        if (!issues.isEmpty()) {
            throw new ValidationException(issues);
        }
        return new TravelRequirement(city, start, end, travelers, budget, companion,
                interests, pace, accommodation, mode, special);
    }

    private static String normalizeRequiredText(String value, String field, List<RequirementValidationError> issues) {
        if (value == null || value.isBlank()) {
            issues.add(new RequirementValidationError(field, "目的城市不能为空"));
            return null;
        }
        return value.strip();
    }

    private static String normalizeOptionalText(
            PreferenceValue<String> input, String field, int maxLength, List<RequirementValidationError> issues) {
        if (input.state() != PreferenceValue.State.EXPLICIT_VALUE) {
            return null;
        }
        String normalized = input.value().strip();
        // 空白显式值不能悄悄降级为“未提供”，否则后续偏好合并可能错误覆盖用户意图。
        if (normalized.isEmpty()) {
            issues.add(new RequirementValidationError(field, "显式文本值不能为空白"));
            return null;
        }
        if (normalized.length() > maxLength) {
            issues.add(new RequirementValidationError(field, "文本长度超过允许上限"));
            return null;
        }
        return normalized;
    }

    private static String normalizeSpecialRequirements(String value, List<RequirementValidationError> issues) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_SPECIAL_REQUIREMENTS_LENGTH) {
            issues.add(new RequirementValidationError("specialRequirements", "特殊要求长度超过允许上限"));
        }
        // 特殊要求永不从偏好补全，因此显式空串可以安全保留，不与 null 合并。
        return normalized;
    }

    private static Set<TravelInterest> parseInterests(
            PreferenceValue<List<String>> input, List<RequirementValidationError> issues) {
        if (input.state() != PreferenceValue.State.EXPLICIT_VALUE) {
            return Set.of();
        }
        EnumSet<TravelInterest> parsed = EnumSet.noneOf(TravelInterest.class);
        boolean invalid = false;
        for (String raw : input.value()) {
            TravelInterest interest = parseEnum(raw, TravelInterest.class);
            if (interest == null) {
                invalid = true;
            } else {
                parsed.add(interest);
            }
        }
        if (invalid) {
            issues.add(new RequirementValidationError("interests", "兴趣类型不受支持"));
        }
        return parsed.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(parsed));
    }

    private static <E extends Enum<E>> E parseRequiredEnum(
            String raw, Class<E> type, String field, String reason, List<RequirementValidationError> issues) {
        E parsed = parseEnum(raw, type);
        if (parsed == null) {
            issues.add(new RequirementValidationError(field, reason));
        }
        return parsed;
    }

    private static <E extends Enum<E>> E parseOptionalEnum(
            PreferenceValue<String> input, Class<E> type, String field, String reason,
            List<RequirementValidationError> issues) {
        if (input.state() != PreferenceValue.State.EXPLICIT_VALUE) {
            return null;
        }
        E parsed = parseEnum(input.value(), type);
        if (parsed == null) {
            issues.add(new RequirementValidationError(field, reason));
        }
        return parsed;
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // 外部枚举可能领先于服务版本；稳定拒绝比泄漏 valueOf 细节或产生 NPE 更安全。
            return null;
        }
    }

    /**
     * 业务问题：描述一个需求域内可安全返回的校验错误，避免与任务十二的行程问题类型冲突。
     * 输入是稳定字段名和固定原因，输出是不可变错误元素；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
     * 任务十与任务十二不使用此类型，后续编排可读取错误并中止；本类型不负责认证、格式化用户原值、工具调用或行程校验。
     *
     * @param field 稳定字段标识，不得为空白
     * @param reason 固定原因描述，不得包含用户输入、堆栈或本机信息
     */
    public record RequirementValidationError(String field, String reason) {
        /**
         * 创建安全需求校验错误。
         * 字段和原因均不得为 null、空串或空白，且调用方前置条件是不传用户原值；输出不会做外部查询。
         * 构造不访问数据库、模型、缓存、文件或网络，不处理认证。
         *
         * @throws IllegalArgumentException 当字段或原因为空白
         * @throws NullPointerException 当字段或原因为 {@code null}
         */
        public RequirementValidationError {
            if (field == null || reason == null) {
                throw new NullPointerException("validation issue");
            }
            if (field.isBlank() || reason.isBlank()) {
                throw new IllegalArgumentException("validation issue");
            }
        }
    }

    /**
     * 业务问题：表示完整旅行需求校验失败，并以单一异常携带稳定错误码及确定顺序错误列表。
     * 输入是非空需求错误列表，输出是可由调用层读取的业务异常；本类型不访问数据库、模型、缓存、文件或网络，不处理认证。
     * 任务十和任务十二拥有各自错误边界，后续编排遇此异常必须停止；本类型不负责认证、HTTP 映射、工具调用或行程修正。
     * 异常消息不包含用户原值、SQL、认证令牌、堆栈文本或本机路径。
     */
    public static final class ValidationException extends IllegalArgumentException {
        private final List<RequirementValidationError> issues;

        private ValidationException(List<RequirementValidationError> issues) {
            super(ERROR_CODE);
            this.issues = List.copyOf(issues);
        }

        /**
         * 返回稳定业务错误码。
         *
         * @return 固定值 {@value RequirementValidator#ERROR_CODE}
         * 无参数、无 null 或显式空语义且不抛业务异常；不访问数据库、模型、缓存、文件或网络，不处理认证。
         */
        public String code() {
            return ERROR_CODE;
        }

        /**
         * 返回确定顺序的安全问题列表。
         *
         * @return 不可变且非空的问题列表
         * 无参数、返回值不为 null，调用方不能修改；不抛业务异常，不访问数据库、模型、缓存、文件或网络，不处理认证。
         */
        public List<RequirementValidationError> issues() {
            return issues;
        }
    }
}
