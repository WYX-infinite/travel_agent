package com.wyx.travelagent.knowledge.ingest;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用确定性的纯 Java 规则把已清理正文切成有序片段。
 * <p>
 * 输入为解析器输出的正文，输出包含从 0 开始的稳定序号与代码点边界。
 * 本类不调用模型、不生成 Embedding ID，也不写任务九的数据库或向量索引。
 */
public final class TextChunker {
    /*
     * 500～800 字和约 100 字重叠只是当前项目的初始基线，后续任务会根据检索评测调整；
     * 这里不把它们宣称为适用于所有语言和知识库的通用最优参数。
     */
    private static final int MIN_CHUNK_CODE_POINTS = 500;
    private static final int MAX_CHUNK_CODE_POINTS = 800;
    private static final int OVERLAP_CODE_POINTS = 100;

    /**
     * 按标题、段落、句子和硬长度的优先级切分正文。
     * <p>
     * 空白输入返回空列表；所有边界均按 Unicode 代码点计算，因此不会从代理对中间截断字符。
     *
     * @param rawText 已完成基础清理的正文；可以为空或仅含空白
     * @return 不含空切片、顺序稳定且不可修改的切片列表
     */
    public List<TextChunk> chunk(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        String text = rawText.replace("\r\n", "\n").replace('\r', '\n').strip();
        int totalCodePoints = text.codePointCount(0, text.length());
        List<TextChunk> chunks = new ArrayList<>();
        int startCodePoint = 0;

        while (startCodePoint < totalCodePoints) {
            int remaining = totalCodePoints - startCodePoint;
            int endCodePoint;
            if (remaining <= MAX_CHUNK_CODE_POINTS) {
                endCodePoint = totalCodePoints;
            } else {
                int minimumEnd = startCodePoint + MIN_CHUNK_CODE_POINTS;
                int maximumEnd = startCodePoint + MAX_CHUNK_CODE_POINTS;
                endCodePoint = selectBoundary(text, startCodePoint, minimumEnd, maximumEnd);
            }

            int startChar = text.offsetByCodePoints(0, startCodePoint);
            int endChar = text.offsetByCodePoints(0, endCodePoint);
            String content = text.substring(startChar, endChar);
            if (!content.isBlank()) {
                // 序号只由输入和确定性边界决定，保证同一正文重复执行时顺序完全一致。
                chunks.add(new TextChunk(chunks.size(), content, startCodePoint, endCodePoint));
            }
            if (endCodePoint == totalCodePoints) {
                break;
            }

            /*
             * 下一片从当前结束位置向前回退 100 个代码点，形成可解释的固定重叠。
             * 由于正常片段至少 500 个代码点，回退后仍严格前进，可避免死循环。
             */
            int nextStart = endCodePoint - OVERLAP_CODE_POINTS;
            startCodePoint = Math.max(startCodePoint + 1, nextStart);
        }
        return List.copyOf(chunks);
    }

    private int selectBoundary(String text, int startCodePoint, int minimumEnd, int maximumEnd) {
        int startChar = text.offsetByCodePoints(0, startCodePoint);
        int minimumChar = text.offsetByCodePoints(0, minimumEnd);
        int maximumChar = text.offsetByCodePoints(0, maximumEnd);

        /*
         * 段落和新标题边界最能保留原文结构；句末次之。只有在基线区间内找不到
         * 合适语义边界时才使用硬长度，防止某个超长单段阻塞切片。
         */
        int paragraph = text.lastIndexOf("\n\n", maximumChar - 1);
        if (paragraph >= minimumChar) {
            return text.codePointCount(0, paragraph);
        }
        int heading = lastHeadingBoundary(text, minimumChar, maximumChar);
        if (heading >= minimumChar) {
            return text.codePointCount(0, heading);
        }
        int sentence = lastSentenceBoundary(text, minimumChar, maximumChar);
        if (sentence >= minimumChar) {
            return text.codePointCount(0, sentence);
        }
        int line = text.lastIndexOf('\n', maximumChar - 1);
        if (line >= minimumChar) {
            return text.codePointCount(0, line);
        }
        return startCodePoint + text.codePointCount(startChar, maximumChar);
    }

    private int lastHeadingBoundary(String text, int minimumChar, int maximumChar) {
        int boundary = text.lastIndexOf("\n#", maximumChar - 1);
        return boundary >= minimumChar ? boundary : -1;
    }

    private int lastSentenceBoundary(String text, int minimumChar, int maximumChar) {
        int cursor = maximumChar;
        while (cursor > minimumChar) {
            int codePoint = text.codePointBefore(cursor);
            if (isSentenceEnding(codePoint)) {
                return cursor;
            }
            cursor = text.offsetByCodePoints(cursor, -1);
        }
        return -1;
    }

    private boolean isSentenceEnding(int codePoint) {
        return codePoint == '。' || codePoint == '！' || codePoint == '？'
                || codePoint == '；' || codePoint == '.' || codePoint == '!'
                || codePoint == '?' || codePoint == ';';
    }

    /**
     * 表示一个切片及其在标准化正文中的 Unicode 代码点区间。
     *
     * @param index          从 0 开始的稳定切片序号
     * @param content        切片正文，包含与相邻片段的有意重叠
     * @param startCodePoint 在完整正文中的起始代码点下标（包含）
     * @param endCodePoint   在完整正文中的结束代码点下标（不包含）
     */
    public record TextChunk(int index, String content, int startCodePoint, int endCodePoint) {
        /**
         * 校验切片序号、正文和半开区间不变量。
         *
         * @throws IllegalArgumentException 序号或边界非法、正文为空时抛出
         */
        public TextChunk {
            if (index < 0 || startCodePoint < 0 || endCodePoint <= startCodePoint) {
                throw new IllegalArgumentException("chunk index and boundaries must be valid");
            }
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("chunk content must not be blank");
            }
        }
    }
}
