package com.wyx.travelagent.common.api;

import com.wyx.travelagent.knowledge.admin.KnowledgeUploadException;
import com.wyx.travelagent.knowledge.ingest.KnowledgeIngestionException;
import com.wyx.travelagent.planning.memory.PreferenceMemoryException;
import com.wyx.travelagent.planning.task.PlanningTaskException;
import com.wyx.travelagent.planning.validation.RequirementValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把 Controller 层可预期异常转换为统一 ApiResponse，避免向客户端暴露堆栈或本机细节。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 处理 Bean Validation 产生的请求字段错误。
     *
     * @param exception Spring 参数校验异常
     * @return HTTP 400 的统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        ApiError error = new ApiError("VALIDATION_ERROR", "request validation failed", null);
        return ResponseEntity.badRequest().body(ApiResponse.fail(error));
    }

    /** @param exception 已按字段脱敏的请求错误 @return HTTP 400 稳定校验响应。 */
    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequestValidation(RequestValidationException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(
                new ApiError("VALIDATION_ERROR", "request validation failed", exception.fields())));
    }

    /** @param exception 完整旅行需求错误 @return HTTP 400 和确定顺序字段映射。 */
    @ExceptionHandler(RequirementValidator.ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequirementValidation(RequirementValidator.ValidationException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.issues().forEach(issue -> fields.putIfAbsent(issue.field(), issue.reason()));
        return ResponseEntity.badRequest().body(ApiResponse.fail(
                new ApiError("VALIDATION_ERROR", "request validation failed", fields)));
    }

    /** @param exception 稳定异步任务异常 @return 异常声明的 HTTP 状态和脱敏错误码。 */
    @ExceptionHandler(PlanningTaskException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlanningTask(PlanningTaskException exception) {
        return ResponseEntity.status(exception.status()).body(ApiResponse.fail(
                new ApiError(exception.code(), exception.getMessage(), null)));
    }

    /** @param exception 偏好领域或存储异常 @return 校验为 400，存储失败为 500，均不泄露 cause。 */
    @ExceptionHandler(PreferenceMemoryException.class)
    public ResponseEntity<ApiResponse<Void>> handlePreferenceMemory(PreferenceMemoryException exception) {
        boolean storage = "PREFERENCE_STORAGE_FAILED".equals(exception.code());
        String code = storage ? exception.code() : "VALIDATION_ERROR";
        String message = storage ? "preference storage failed" : "request validation failed";
        return ResponseEntity.status(storage ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(new ApiError(code, message, null)));
    }

    /** @param exception JSON 语法或类型绑定错误 @return HTTP 400 稳定校验响应。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(
                new ApiError("VALIDATION_ERROR", "request validation failed", null)));
    }

    /**
     * 处理 multipart 必填字段或文件部分缺失。
     *
     * @param exception Spring 请求绑定异常
     * @return HTTP 400 的统一校验错误响应
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMissingUploadField(Exception exception) {
        ApiError error = new ApiError("VALIDATION_ERROR", "request validation failed", null);
        return ResponseEntity.badRequest().body(ApiResponse.fail(error));
    }

    /**
     * 处理知识文档上传服务给出的安全业务错误。
     *
     * @param exception 带稳定错误码和 HTTP 状态的上传异常
     * @return 不包含底层路径、堆栈或正文的统一错误响应
     */
    @ExceptionHandler(KnowledgeUploadException.class)
    public ResponseEntity<ApiResponse<Void>> handleKnowledgeUpload(KnowledgeUploadException exception) {
        ApiError error = new ApiError(exception.code(), exception.getMessage(), null);
        return ResponseEntity.status(exception.status()).body(ApiResponse.fail(error));
    }

    /**
     * 处理知识入库生命周期给出的脱敏业务错误。
     *
     * @param exception 带稳定错误码和 HTTP 状态的入库异常
     * @return 不包含 SQL、正文、向量、密钥、路径或堆栈的统一错误响应
     */
    @ExceptionHandler(KnowledgeIngestionException.class)
    public ResponseEntity<ApiResponse<Void>> handleKnowledgeIngestion(
            KnowledgeIngestionException exception) {
        ApiError error = new ApiError(exception.code(), exception.getMessage(), null);
        return ResponseEntity.status(exception.status()).body(ApiResponse.fail(error));
    }

    /**
     * 处理 Spring multipart 在进入业务服务前发现的全局大小超限。
     *
     * @param exception Spring multipart 大小异常
     * @return HTTP 400 和稳定大小错误码
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        ApiError error = new ApiError(
                "UPLOAD_TOO_LARGE", "uploaded file exceeds the configured size limit", null);
        return ResponseEntity.badRequest().body(ApiResponse.fail(error));
    }
}
