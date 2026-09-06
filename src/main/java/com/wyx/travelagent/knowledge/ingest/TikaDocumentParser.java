package com.wyx.travelagent.knowledge.ingest;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.springframework.util.StringUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 使用 Apache Tika 对受控的本地 PDF、DOCX、Markdown 和 TXT 文件做真实检测与正文提取。
 * <p>
 * 输入必须是上传服务已经限制大小的本地文件，输出为实际媒体类型和标准化正文。
 * 本类禁止递归解析嵌入附件，也不接收或访问来源 URL；文件白名单、落盘和任务九的切片/索引不属于本类职责。
 */
public final class TikaDocumentParser implements DocumentParser {
    private static final double MIN_ZIP_INFLATE_RATIO = 0.01d;
    private static final long MAX_ZIP_ENTRY_BYTES = 64L * 1024 * 1024;
    private static final long MAX_ZIP_TEXT_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PDF_MAIN_MEMORY_BYTES = 64L * 1024 * 1024;
    private static final Pattern INLINE_WHITESPACE = Pattern.compile("[\\p{Zs}\\t\\x0B\\f]+");
    private static final EmbeddedDocumentExtractor SKIP_EMBEDDED_CONTENT = new EmbeddedDocumentExtractor() {
        /**
         * 主文档解析不递归进入附件。
         *
         * @param metadata 嵌入对象元数据
         * @return 固定为 false
         */
        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return false;
        }

        /**
         * 跳过嵌入对象；方法保留为空是安全策略，不是遗漏解析步骤。
         *
         * @param stream     嵌入对象输入流
         * @param handler    正文处理器
         * @param metadata   嵌入对象元数据
         * @param outputHtml 是否请求 HTML 输出
         */
        @Override
        public void parseEmbedded(
                InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml) {
            // 主文档正文足以完成任务八；跳过附件可降低嵌套压缩包与异常对象的资源风险。
        }
    };

    private final int maxExtractedCharacters;
    private final AutoDetectParser parser;

    /**
     * 创建具有明确正文字符上限的解析器。
     *
     * @param maxExtractedCharacters 单个文档允许提取的最大 Java 字符数，必须为正数
     * @throws IllegalArgumentException 上限不是正数时抛出
     */
    public TikaDocumentParser(int maxExtractedCharacters) {
        if (maxExtractedCharacters <= 0) {
            throw new IllegalArgumentException("maxExtractedCharacters must be positive");
        }
        this.maxExtractedCharacters = maxExtractedCharacters;
        this.parser = new AutoDetectParser();
        enforceArchiveSafetyLimits();
    }

    /**
     * 根据文件实际字节检测格式、提取正文并执行确定性清理。
     * <p>
     * 提取字符上限用于阻止异常文档无限扩张内存；检测流和解析流分别在方法内关闭。
     *
     * @param file 已存在且上传大小已受控的本地文件
     * @return 实际媒体类型和清理后的非空正文
     * @throws DocumentParsingException 文件不可读、损坏、正文为空或超过提取字符上限时抛出
     */
    @Override
    public ParsedDocument parse(Path file) {
        Objects.requireNonNull(file, "file");
        String detectedContentType = detect(file);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, detectedContentType);
        BodyContentHandler handler = new BodyContentHandler(maxExtractedCharacters);
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, SKIP_EMBEDDED_CONTENT);
        context.set(OfficeParserConfig.class, officeParserConfig());
        context.set(PDFParserConfig.class, pdfParserConfig());
        enforceArchiveSafetyLimits();

        try (InputStream input = Files.newInputStream(file)) {
            parser.parse(input, handler, metadata, context);
        } catch (SAXException exception) {
            if (WriteLimitReachedException.isWriteLimitReached(exception)) {
                throw new DocumentParsingException(
                        "DOCUMENT_TEXT_LIMIT_EXCEEDED",
                        "document text exceeds the configured extraction limit",
                        detectedContentType,
                        exception);
            }
            throw parseFailure(detectedContentType, exception);
        } catch (IOException | TikaException exception) {
            throw parseFailure(detectedContentType, exception);
        } catch (RuntimeException exception) {
            throw parseFailure(detectedContentType, exception);
        }

        String cleaned = normalizeText(handler.toString());
        if (!StringUtils.hasText(cleaned)) {
            throw new DocumentParsingException(
                    "DOCUMENT_EMPTY", "document contains no effective text", detectedContentType, null);
        }
        return new ParsedDocument(detectedContentType, cleaned);
    }

    private String detect(Path file) {
        try (TikaInputStream input = TikaInputStream.get(file)) {
            // 不设置原始文件名，避免扩展名影响 Tika；检测结果必须来自真实字节。
            MediaType detected = parser.getDetector().detect(input, new Metadata());
            return detected.toString();
        } catch (IOException | RuntimeException exception) {
            throw new DocumentParsingException(
                    "DOCUMENT_PARSE_FAILED", "document format detection failed", exception);
        }
    }

    private DocumentParsingException parseFailure(String detectedContentType, Exception cause) {
        return new DocumentParsingException(
                "DOCUMENT_PARSE_FAILED", "document content could not be parsed",
                detectedContentType, cause);
    }

    private void enforceArchiveSafetyLimits() {
        /*
         * DOCX 是 ZIP 容器。POI 的限制是 JVM 全局可变值，不能假设其他库或测试从未改动它们；
         * 每次解析前重申压缩比、单条目、条目数和文本上限，避免小上传膨胀成超大解压对象。
         */
        synchronized (ZipSecureFile.class) {
            ZipSecureFile.setMinInflateRatio(MIN_ZIP_INFLATE_RATIO);
            ZipSecureFile.setMaxEntrySize(MAX_ZIP_ENTRY_BYTES);
            ZipSecureFile.setMaxFileCount(1_000);
            ZipSecureFile.setMaxTextSize(MAX_ZIP_TEXT_BYTES);
        }
    }

    private OfficeParserConfig officeParserConfig() {
        OfficeParserConfig config = new OfficeParserConfig();
        // SAX 提取避免为完整 DOCX 构造大型对象树；宏不属于任务八允许的 DOCX 正文来源。
        config.setUseSAXDocxExtractor(true);
        config.setExtractMacros(false);
        return config;
    }

    private PDFParserConfig pdfParserConfig() {
        PDFParserConfig config = new PDFParserConfig();
        /*
         * 知识上传只需要现有文字层：关闭 OCR 和图片提取可避免启动外部 OCR 进程或解码巨图；
         * 较低的 PDFBox 主内存阈值会让大对象使用临时存储，而不是持续占用 Java 堆。
         */
        config.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        config.setExtractInlineImages(false);
        config.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.NONE);
        config.setMaxMainMemoryBytes(MAX_PDF_MAIN_MEMORY_BYTES);
        config.setParseIncrementalUpdates(false);
        return config;
    }

    private String normalizeText(String raw) {
        String unifiedNewlines = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = unifiedNewlines.split("\n", -1);
        StringBuilder normalized = new StringBuilder(unifiedNewlines.length());
        boolean pendingBlankLine = false;

        for (String line : lines) {
            // 只合并行内空白并限制连续空行；保留非空行次序，避免把标题或正常段落误当页眉页脚删除。
            String cleanedLine = INLINE_WHITESPACE.matcher(line.strip()).replaceAll(" ");
            if (cleanedLine.isEmpty()) {
                if (normalized.length() > 0) {
                    pendingBlankLine = true;
                }
                continue;
            }
            if (normalized.length() > 0) {
                normalized.append(pendingBlankLine ? "\n\n" : "\n");
            }
            normalized.append(cleanedLine);
            pendingBlankLine = false;
        }
        return normalized.toString().strip();
    }
}
