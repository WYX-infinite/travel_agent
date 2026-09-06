package com.wyx.travelagent.knowledge.ingest;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestDocumentFixtures {
    public static final String TITLE = "Travel Knowledge Title";
    public static final String SOURCE = "Source: Example City Culture Bureau";

    private TestDocumentFixtures() {
    }

    public static Path pdf(Path directory, String filename) throws IOException {
        Path path = directory.resolve(filename);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                content.newLineAtOffset(72, 720);
                content.showText(TITLE);
                content.endText();

                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.newLineAtOffset(72, 680);
                content.showText("First paragraph has useful travel guidance.");
                content.newLineAtOffset(0, -24);
                content.showText(SOURCE);
                content.endText();
            }
            document.save(path.toFile());
        }
        return path;
    }

    public static Path docx(Path directory, String filename) throws IOException {
        Path path = directory.resolve(filename);
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(path)) {
            XWPFParagraph title = document.createParagraph();
            title.setStyle("Title");
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setText(TITLE);
            document.createParagraph().createRun().setText("First   paragraph has repeated    spaces.");
            document.createParagraph().createRun().setText(SOURCE);
            document.write(output);
        }
        return path;
    }

    public static Path highlyCompressedDocx(Path directory, String filename) throws IOException {
        Path path = directory.resolve(filename);
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(path)) {
            document.createParagraph().createRun().setText("压".repeat(200_000));
            document.write(output);
        }
        return path;
    }

    public static Path markdown(Path directory, String filename) throws IOException {
        Path path = directory.resolve(filename);
        Files.writeString(path, "# " + TITLE + "\r\n\r\nFirst   paragraph.\r\n\r\n\r\n" + SOURCE + "\r\n",
                StandardCharsets.UTF_8);
        return path;
    }

    public static Path text(Path directory, String filename) throws IOException {
        Path path = directory.resolve(filename);
        Files.writeString(path, TITLE + "\r\n\r\nFirst\t paragraph.\r\n\r\n\r\n" + SOURCE + "\r\n",
                StandardCharsets.UTF_8);
        return path;
    }
}
