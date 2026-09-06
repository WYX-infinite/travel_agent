package com.wyx.travelagent.knowledge.ingest;

/**
 * 将 Apache Tika 和文件读取异常转换成可供项目统一处理的安全业务错误。
 * <p>
 * 它输出稳定错误码和可选的实际媒体类型，不暴露本机路径、堆栈、密钥或完整正文；
 * 上传服务据此区分格式不一致与解析失败，但本异常不负责 HTTP 响应或文件清理。
 */
public final class DocumentParsingException extends RuntimeException {
    private final String code;
    private final String detectedContentType;

    /**
     * 创建不携带已检测媒体类型的解析错误。
     *
     * @param code    稳定业务错误码
     * @param message 可安全返回或记录的简短说明
     * @param cause   仅供服务端诊断的原始异常
     */
    public DocumentParsingException(String code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    /**
     * 创建携带实际媒体类型的解析错误。
     *
     * @param code                稳定业务错误码
     * @param message             可安全返回或记录的简短说明
     * @param detectedContentType Tika 已检测出的实际媒体类型；检测失败时可以为空
     * @param cause               仅供服务端诊断的原始异常
     */
    public DocumentParsingException(
            String code, String message, String detectedContentType, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.detectedContentType = detectedContentType;
    }

    /**
     * 返回供 Controller 统一响应使用的稳定错误码。
     *
     * @return 不包含本机环境信息的业务错误码
     */
    public String code() {
        return code;
    }

    /**
     * 返回 Tika 已检测到的实际媒体类型。
     *
     * @return 媒体类型；检测尚未完成时为空
     */
    public String detectedContentType() {
        return detectedContentType;
    }
}
