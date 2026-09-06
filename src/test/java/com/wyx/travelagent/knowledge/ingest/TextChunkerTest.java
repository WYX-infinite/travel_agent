package com.wyx.travelagent.knowledge.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    void createsStableChunksInBaselineRangeWithOneHundredCharacterOverlap() {
        String text = "# 杭州旅行提示\n\n" + sentence("甲", 180) + "\n\n"
                + sentence("乙", 180) + "\n\n" + sentence("丙", 180) + "\n\n"
                + sentence("丁", 180) + "\n\n" + sentence("戊", 180) + "\n\n"
                + sentence("己", 180);

        List<TextChunker.TextChunk> first = chunker.chunk(text);
        List<TextChunker.TextChunk> second = chunker.chunk(text);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSizeGreaterThan(1);
        assertThat(first).extracting(TextChunker.TextChunk::index)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, first.size()).boxed().toList());
        assertThat(first.getFirst().content()).startsWith("# 杭州旅行提示");
        assertThat(first.subList(0, first.size() - 1))
                .allSatisfy(chunk -> assertThat(codePoints(chunk.content())).isBetween(500, 800));
        for (int index = 1; index < first.size(); index++) {
            assertThat(first.get(index - 1).endCodePoint() - first.get(index).startCodePoint())
                    .isEqualTo(100);
        }
    }

    @Test
    void prefersParagraphBoundaryAndAllowsShortFinalChunk() {
        String firstParagraph = "甲".repeat(610);
        String secondParagraph = "乙".repeat(250);
        List<TextChunker.TextChunk> chunks = chunker.chunk(firstParagraph + "\n\n" + secondParagraph);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst().content()).endsWith(firstParagraph);
        assertThat(codePoints(chunks.getLast().content())).isLessThan(500);
    }

    @Test
    void hardSplitsAnOverlongParagraphWithoutBreakingUnicodeSurrogatePairs() {
        String text = "😀旅".repeat(650);

        List<TextChunker.TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(codePoints(chunk.content())).isLessThanOrEqualTo(800);
            assertThat(chunk.content()).doesNotContain("\uFFFD");
            assertNoUnpairedSurrogates(chunk.content());
        });
        assertThat(removeIntentionalOverlap(chunks)).isEqualTo(text);
    }

    @Test
    void blankTextProducesNoChunks() {
        assertThat(chunker.chunk(" \r\n\t ")).isEmpty();
    }

    @Test
    void noNonWhitespaceContentIsLostOutsideIntentionalOverlap() {
        String text = "标题\n\n" + sentence("春", 230) + "\n\n" + sentence("夏", 230)
                + "\n\n" + sentence("秋", 230) + "\n\n" + sentence("冬", 230);

        List<TextChunker.TextChunk> chunks = chunker.chunk(text);

        assertThat(removeIntentionalOverlap(chunks).replaceAll("\\s+", ""))
                .isEqualTo(text.replaceAll("\\s+", ""));
        assertThat(chunks).noneMatch(chunk -> chunk.content().isBlank());
    }

    private static String sentence(String character, int count) {
        return (character.repeat(59) + "。").repeat(count / 60);
    }

    private static int codePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    private static String removeIntentionalOverlap(List<TextChunker.TextChunk> chunks) {
        if (chunks.isEmpty()) {
            return "";
        }
        StringBuilder restored = new StringBuilder(chunks.getFirst().content());
        for (int index = 1; index < chunks.size(); index++) {
            String current = chunks.get(index).content();
            int overlap = chunks.get(index - 1).endCodePoint() - chunks.get(index).startCodePoint();
            restored.append(current.substring(current.offsetByCodePoints(0, overlap)));
        }
        return restored.toString();
    }

    private static void assertNoUnpairedSurrogates(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                assertThat(index + 1).isLessThan(value.length());
                assertThat(Character.isLowSurrogate(value.charAt(++index))).isTrue();
            } else {
                assertThat(Character.isLowSurrogate(current)).isFalse();
            }
        }
    }
}
