package com.wyx.travelagent.knowledge.ingest;

import java.nio.file.Path;

/**
 * 定义本地知识文档解析的最小边界。
 * <p>
 * 调用方提供已经受上传大小限制的本地文件，解析器返回根据实际字节检测的媒体类型和清理后正文。
 * 接口不接收来源 URL，因此不会下载外部内容；它也不切片、不写数据库、不生成 Embedding。
 */
public interface DocumentParser {

    /**
     * 检测并提取本地文件正文。
     *
     * @param file 已存在且大小已受控的本地文件；不得是需要解析器下载的 URL
     * @return 实际媒体类型和确定性清理后的有效正文
     * @throws DocumentParsingException 文件损坏、无有效正文、提取字符超限或底层解析失败时抛出
     */
    ParsedDocument parse(Path file);

    /**
     * 保存一次解析的确定性输出，供上传服务校验实际类型并供后续任务切片。
     *
     * @param detectedContentType Apache Tika 根据文件实际字节检测到的媒体类型
     * @param text                清理后的有效正文
     */
    record ParsedDocument(String detectedContentType, String text) {
    }
}
