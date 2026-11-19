package com.swen3.dms.worker;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts text from PDFs and images. PDFs that already contain a text layer (most digitally created ones) are
 * read directly, which is fast and exact; only scanned PDFs and images go through OCR.
 */
@Component
public class TextExtractor {

    /** A PDF with fewer letters or digits than this in its text layer is treated as a scan. */
    static final int MIN_TEXT_LAYER_CHARS = 20;

    private static final Logger log = LoggerFactory.getLogger(TextExtractor.class);

    private final OcrEngine ocr;
    private final WorkerProperties properties;

    public TextExtractor(OcrEngine ocr, WorkerProperties properties) {
        this.ocr = ocr;
        this.properties = properties;
    }

    public String extract(byte[] content, String contentType) throws IOException, OcrException {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.startsWith("application/pdf")) {
            return extractFromPdf(content);
        }
        if (type.startsWith("image/")) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new OcrException("Unreadable image of type " + contentType);
            }
            return normalize(ocr.recognize(image));
        }
        throw new OcrException("Unsupported content type " + contentType);
    }

    private String extractFromPdf(byte[] content) throws IOException, OcrException {
        try (PDDocument pdf = Loader.loadPDF(content)) {
            int pages = Math.min(pdf.getNumberOfPages(), properties.maxPages());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(pages);
            String textLayer = stripper.getText(pdf);
            if (textLayer.codePoints().filter(Character::isLetterOrDigit).count() >= MIN_TEXT_LAYER_CHARS) {
                return normalize(textLayer);
            }

            log.debug("No usable text layer, running OCR on {} page(s)", pages);
            PDFRenderer renderer = new PDFRenderer(pdf);
            List<String> pageTexts = new ArrayList<>();
            for (int page = 0; page < pages; page++) {
                pageTexts.add(ocr.recognize(renderer.renderImageWithDPI(page, properties.dpi(), ImageType.GRAY)));
            }
            return normalize(String.join("\n\n", pageTexts));
        }
    }

    /** Trims trailing spaces and collapses runs of blank lines. */
    static String normalize(String text) {
        return text.replace("\r", "")
                .replace("\f", "\n")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
