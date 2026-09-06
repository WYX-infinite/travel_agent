package com.wyx.travelagent.knowledge.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.openxml4j.util.ZipSecureFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikaDocumentParserTest {

    @TempDir
    Path temporaryDirectory;

    private final TikaDocumentParser parser = new TikaDocumentParser(10_000);

    @Test
    void extractsTitleAndBodyFromPdf() throws Exception {
        DocumentParser.ParsedDocument parsed = parser.parse(
                TestDocumentFixtures.pdf(temporaryDirectory, "guide.pdf"));

        assertThat(parsed.detectedContentType()).isEqualTo("application/pdf");
        assertThat(parsed.text()).contains(TestDocumentFixtures.TITLE, "First paragraph", TestDocumentFixtures.SOURCE);
    }

    @Test
    void extractsTitleAndBodyFromDocx() throws Exception {
        DocumentParser.ParsedDocument parsed = parser.parse(
                TestDocumentFixtures.docx(temporaryDirectory, "guide.docx"));

        assertThat(parsed.detectedContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(parsed.text()).contains(TestDocumentFixtures.TITLE, "First paragraph", TestDocumentFixtures.SOURCE)
                .doesNotContain("First   paragraph");
    }

    @Test
    void preservesMarkdownHeadingAndParagraphBoundaries() throws Exception {
        DocumentParser.ParsedDocument parsed = parser.parse(
                TestDocumentFixtures.markdown(temporaryDirectory, "guide.md"));

        assertThat(parsed.detectedContentType()).startsWith("text/");
        assertThat(parsed.text()).startsWith("# " + TestDocumentFixtures.TITLE)
                .contains("\n\nFirst paragraph.\n\n" + TestDocumentFixtures.SOURCE)
                .doesNotContain("\r")
                .doesNotContain("\n\n\n");
    }

    @Test
    void extractsAndNormalizesPlainText() throws Exception {
        DocumentParser.ParsedDocument parsed = parser.parse(
                TestDocumentFixtures.text(temporaryDirectory, "guide.txt"));

        assertThat(parsed.detectedContentType()).startsWith("text/plain");
        assertThat(parsed.text()).isEqualTo(TestDocumentFixtures.TITLE
                + "\n\nFirst paragraph.\n\n" + TestDocumentFixtures.SOURCE);
    }

    @Test
    void rejectsDocumentWithoutEffectiveText() throws Exception {
        Path empty = temporaryDirectory.resolve("empty.txt");
        Files.writeString(empty, " \r\n\t\r\n ", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> parser.parse(empty))
                .isInstanceOf(DocumentParsingException.class)
                .extracting("code")
                .isEqualTo("DOCUMENT_EMPTY");
    }

    @Test
    void mapsCorruptedPdfToSafeBusinessError() throws Exception {
        Path corrupt = temporaryDirectory.resolve("corrupt.pdf");
        Files.write(corrupt, "%PDF-1.7\nnot-a-valid-pdf".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> parser.parse(corrupt))
                .isInstanceOf(DocumentParsingException.class)
                .hasMessageNotContaining(temporaryDirectory.toString())
                .extracting("code")
                .isEqualTo("DOCUMENT_PARSE_FAILED");
    }

    @Test
    void rejectsTextBeyondConfiguredExtractionLimit() throws Exception {
        Path text = temporaryDirectory.resolve("large.txt");
        Files.writeString(text, "有效正文".repeat(100), StandardCharsets.UTF_8);
        TikaDocumentParser limited = new TikaDocumentParser(40);

        assertThatThrownBy(() -> limited.parse(text))
                .isInstanceOf(DocumentParsingException.class)
                .extracting("code")
                .isEqualTo("DOCUMENT_TEXT_LIMIT_EXCEEDED");
    }

    @Test
    void reassertsDocxZipBombProtectionInsteadOfTrustingMutablePoiGlobals() throws Exception {
        double previousRatio = ZipSecureFile.getMinInflateRatio();
        try {
            ZipSecureFile.setMinInflateRatio(0.0d);
            Path compressed = TestDocumentFixtures.highlyCompressedDocx(
                    temporaryDirectory, "compressed.docx");

            assertThatThrownBy(() -> new TikaDocumentParser(1_000_000).parse(compressed))
                    .isInstanceOf(DocumentParsingException.class)
                    .extracting("code")
                    .isEqualTo("DOCUMENT_PARSE_FAILED");
        } finally {
            ZipSecureFile.setMinInflateRatio(previousRatio);
        }
    }
}
