package com.wyx.travelagent.knowledge.admin;

import com.wyx.travelagent.knowledge.ingest.TestDocumentFixtures;
import com.wyx.travelagent.knowledge.ingest.TikaDocumentParser;
import com.wyx.travelagent.knowledge.model.KnowledgeDocument;
import com.wyx.travelagent.knowledge.model.KnowledgeDocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class KnowledgeDocumentUploadServiceTest {

    @TempDir
    Path temporaryDirectory;

    private Path uploadRoot;
    private KnowledgeDocumentUploadService service;

    @BeforeEach
    void setUp() {
        uploadRoot = temporaryDirectory.resolve("uploads");
        KnowledgeUploadProperties properties = new KnowledgeUploadProperties(
                uploadRoot, DataSize.ofKilobytes(512), 20_000);
        service = new KnowledgeDocumentUploadService(properties, new TikaDocumentParser(20_000));
    }

    @Test
    void acceptsPdfDocxMarkdownAndTextUsingExtensionDeclaredMimeAndDetectedType() throws Exception {
        List<Fixture> fixtures = List.of(
                fixture(TestDocumentFixtures.pdf(temporaryDirectory, "guide.pdf"), "application/pdf"),
                fixture(TestDocumentFixtures.docx(temporaryDirectory, "guide.docx"),
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                fixture(TestDocumentFixtures.markdown(temporaryDirectory, "guide.md"), "text/markdown"),
                fixture(TestDocumentFixtures.text(temporaryDirectory, "guide.txt"), "text/plain"));

        for (Fixture fixture : fixtures) {
            UploadedKnowledgeDocument uploaded = service.upload(fixture.file(), validRequest(), 9);
            assertThat(uploaded.document().status().name()).isEqualTo("UPLOADED");
            assertThat(uploaded.extractedText()).contains(TestDocumentFixtures.TITLE, TestDocumentFixtures.SOURCE);
            assertThat(uploaded.document().uploadedBy()).isEqualTo(9);
        }
    }

    @Test
    void rejectsMissingRequiredMetadata() throws Exception {
        MockMultipartFile file = fixture(
                TestDocumentFixtures.text(temporaryDirectory, "guide.txt"), "text/plain").file();
        List<KnowledgeDocumentUploadRequest> invalid = List.of(
                request(" ", "杭州", "文旅局", "https://example.test/source", LocalDate.of(2026, 8, 1),
                        KnowledgeDocumentType.OFFICIAL_NOTICE),
                request("标题", " ", "文旅局", "https://example.test/source", LocalDate.of(2026, 8, 1),
                        KnowledgeDocumentType.OFFICIAL_NOTICE),
                request("标题", "杭州", " ", "https://example.test/source", LocalDate.of(2026, 8, 1),
                        KnowledgeDocumentType.OFFICIAL_NOTICE),
                request("标题", "杭州", "文旅局", "https://example.test/source", null,
                        KnowledgeDocumentType.OFFICIAL_NOTICE),
                request("标题", "杭州", "文旅局", "https://example.test/source", LocalDate.of(2026, 8, 1), null));

        for (KnowledgeDocumentUploadRequest request : invalid) {
            assertThatThrownBy(() -> service.upload(file, request, 9))
                    .isInstanceOf(KnowledgeUploadException.class)
                    .extracting("code")
                    .isEqualTo("INVALID_UPLOAD_METADATA");
        }
    }

    @Test
    void rejectsEmptyFileAndUnsupportedExtension() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
        MockMultipartFile executable = new MockMultipartFile(
                "file", "guide.exe", "application/octet-stream", "MZ".getBytes(StandardCharsets.US_ASCII));

        assertCode(empty, "EMPTY_UPLOAD");
        assertCode(executable, "UNSUPPORTED_DOCUMENT_TYPE");
    }

    @Test
    void rejectsMimeSpoofAndExtensionThatDisagreesWithDetectedType() throws Exception {
        byte[] pdf = Files.readAllBytes(TestDocumentFixtures.pdf(temporaryDirectory, "real.pdf"));
        MockMultipartFile wrongDeclaredMime = new MockMultipartFile("file", "guide.pdf", "text/plain", pdf);
        MockMultipartFile wrongExtension = new MockMultipartFile("file", "guide.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", pdf);
        MockMultipartFile binaryText = new MockMultipartFile("file", "guide.txt", "text/plain",
                new byte[]{0, 1, 2, 3, 0, 127});

        assertCode(wrongDeclaredMime, "DOCUMENT_TYPE_MISMATCH");
        assertCode(wrongExtension, "DOCUMENT_TYPE_MISMATCH");
        assertCode(binaryText, "DOCUMENT_TYPE_MISMATCH");
    }

    @Test
    void enforcesConfiguredSizeAgainstActualStreamAndAcceptsExactBoundary() {
        byte[] exact = "正文1".getBytes(StandardCharsets.UTF_8);
        KnowledgeUploadProperties exactProperties = new KnowledgeUploadProperties(
                uploadRoot, DataSize.ofBytes(exact.length), 100);
        KnowledgeDocumentUploadService exactService = new KnowledgeDocumentUploadService(
                exactProperties, new TikaDocumentParser(100));

        UploadedKnowledgeDocument accepted = exactService.upload(
                new MockMultipartFile("file", "guide.txt", "text/plain", exact), validRequest(), 9);
        assertThat(accepted.document().fileSize()).isEqualTo(exact.length);

        MockMultipartFile lyingSize = new MockMultipartFile(
                "file", "large.txt", "text/plain", "有效正文".repeat(30).getBytes(StandardCharsets.UTF_8)) {
            @Override
            public long getSize() {
                return 1;
            }
        };
        assertThatThrownBy(() -> exactService.upload(lyingSize, validRequest(), 9))
                .isInstanceOf(KnowledgeUploadException.class)
                .extracting("code")
                .isEqualTo("UPLOAD_TOO_LARGE");
    }

    @Test
    void rejectsPathTraversalInOriginalFilename() {
        assertCode(new MockMultipartFile("file", "../guide.txt", "text/plain",
                "有效正文".getBytes(StandardCharsets.UTF_8)), "INVALID_ORIGINAL_FILENAME");
        assertCode(new MockMultipartFile("file", "C:\\temp\\guide.txt", "text/plain",
                "有效正文".getBytes(StandardCharsets.UTF_8)), "INVALID_ORIGINAL_FILENAME");
    }

    @Test
    void usesGeneratedFilenameInsideNormalizedUploadRoot() throws Exception {
        MockMultipartFile file = fixture(
                TestDocumentFixtures.text(temporaryDirectory, "human-name.txt"), "text/plain").file();

        KnowledgeDocument document = service.upload(file, validRequest(), 9).document();
        Path stored = uploadRoot.resolve(document.storedFilename()).normalize();

        assertThat(document.originalFilename()).isEqualTo("human-name.txt");
        assertThat(document.storedFilename()).isNotEqualTo(document.originalFilename())
                .matches("[0-9a-f\\-]{36}\\.txt");
        assertThat(stored).startsWith(uploadRoot.toAbsolutePath().normalize());
        assertThat(stored).isRegularFile();
    }

    @Test
    void calculatesStableSha256FromActualBytesWithoutRejectingDuplicatesYet() {
        byte[] content = "相同内容会产生相同摘要".getBytes(StandardCharsets.UTF_8);
        UploadedKnowledgeDocument first = service.upload(
                new MockMultipartFile("file", "first.txt", "text/plain", content), validRequest(), 9);
        UploadedKnowledgeDocument second = service.upload(
                new MockMultipartFile("file", "second.txt", "text/plain", content), validRequest(), 9);

        assertThat(first.document().sha256()).hasSize(64).isEqualTo(second.document().sha256());
        assertThat(first.document().storedFilename()).isNotEqualTo(second.document().storedFilename());
    }

    @Test
    void sourceUrlAllowsOnlyHttpOrHttpsAndIsNeverFetched() throws Exception {
        MockMultipartFile file = fixture(
                TestDocumentFixtures.text(temporaryDirectory, "guide.txt"), "text/plain").file();
        KnowledgeDocumentUploadRequest unreachableButValid = request(
                "标题", "杭州", "文旅局", "http://127.0.0.1:1/must-not-be-fetched",
                LocalDate.of(2026, 8, 1), KnowledgeDocumentType.OFFICIAL_NOTICE);

        assertThat(service.upload(file, unreachableButValid, 9).document().sourceUrl())
                .isEqualTo(URI.create("http://127.0.0.1:1/must-not-be-fetched"));
        for (String url : List.of(
                "file:///etc/passwd", "ftp://example.test/source", "//example.test/source", "https:relative")) {
            KnowledgeDocumentUploadRequest invalid = request(
                    "标题", "杭州", "文旅局", url,
                    LocalDate.of(2026, 8, 1), KnowledgeDocumentType.OFFICIAL_NOTICE);
            assertThatThrownBy(() -> service.upload(file, invalid, 9))
                    .isInstanceOf(KnowledgeUploadException.class)
                    .extracting("code")
                    .isEqualTo("INVALID_SOURCE_URL");
        }
    }

    @Test
    void parsingFailureLeavesNoTemporaryOrFinalFile() {
        MockMultipartFile corrupt = new MockMultipartFile("file", "corrupt.pdf", "application/pdf",
                "%PDF-1.7\nnot-a-valid-pdf".getBytes(StandardCharsets.US_ASCII));

        assertCode(corrupt, "DOCUMENT_PARSE_FAILED");
        assertThat(uploadRoot.resolve(".staging")).isEmptyDirectory();
        assertThat(uploadRoot).isDirectoryContaining(path ->
                path.getFileName().toString().equals(".staging"));
    }

    @Test
    void cleanupFailureIsObservableOnThePrimaryUploadError() {
        KnowledgeUploadProperties properties = new KnowledgeUploadProperties(
                uploadRoot, DataSize.ofKilobytes(512), 20_000);
        KnowledgeDocumentUploadService cleanupFailingService = new KnowledgeDocumentUploadService(
                properties,
                new TikaDocumentParser(20_000),
                path -> {
                    throw new IOException("simulated cleanup failure");
                });
        MockMultipartFile corrupt = new MockMultipartFile("file", "corrupt.pdf", "application/pdf",
                "%PDF-1.7\nnot-a-valid-pdf".getBytes(StandardCharsets.US_ASCII));

        KnowledgeUploadException thrown = catchThrowableOfType(
                KnowledgeUploadException.class,
                () -> cleanupFailingService.upload(corrupt, validRequest(), 9));

        assertThat(thrown.code()).isEqualTo("DOCUMENT_PARSE_FAILED");
        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(thrown.getSuppressed()[0])
                .isInstanceOf(IOException.class)
                .hasMessage("simulated cleanup failure");
    }

    private Fixture fixture(Path path, String contentType) throws IOException {
        return new Fixture(new MockMultipartFile(
                "file", path.getFileName().toString(), contentType, Files.readAllBytes(path)));
    }

    private KnowledgeDocumentUploadRequest validRequest() {
        return request("西湖游览说明", "杭州", "杭州市文化广电旅游局", "https://example.test/source",
                LocalDate.of(2026, 8, 1), KnowledgeDocumentType.OFFICIAL_NOTICE);
    }

    private KnowledgeDocumentUploadRequest request(
            String title,
            String city,
            String publisher,
            String sourceUrl,
            LocalDate publishedAt,
            KnowledgeDocumentType documentType) {
        return new KnowledgeDocumentUploadRequest(
                title, city, publisher, sourceUrl, publishedAt, documentType);
    }

    private void assertCode(MockMultipartFile file, String code) {
        assertThatThrownBy(() -> service.upload(file, validRequest(), 9))
                .isInstanceOf(KnowledgeUploadException.class)
                .extracting("code")
                .isEqualTo(code);
    }

    private record Fixture(MockMultipartFile file) {
    }
}
